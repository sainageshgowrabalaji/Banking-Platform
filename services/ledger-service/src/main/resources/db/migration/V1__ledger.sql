-- The ledger. Accounts, the journal, holds, and the two tables every service with events and
-- idempotent writes has (outbox_event and idempotency_record).
--
-- Money is stored in the smallest unit of its currency (cents for USD) as a whole number, so no
-- rounding can ever happen in the database.

create table account (
    id                   uuid primary key,
    customer_id          uuid,
    type                 text        not null check (type in ('CHECKING', 'SAVINGS', 'INTERNAL')),
    name                 text,
    currency             varchar(3)  not null,
    status               text        not null check (status in ('PENDING', 'ACTIVE', 'FROZEN', 'CLOSED')),
    -- Running totals, changed in the same transaction as the journal entry that causes the change.
    ledger_balance_minor bigint      not null default 0,
    held_minor           bigint      not null default 0 check (held_minor >= 0),
    opened_at            timestamptz not null,
    closed_at            timestamptz,
    -- Optimistic locking. An update only succeeds when the version is still the one that was read.
    version              bigint      not null default 0,
    constraint customer_account_has_customer check (type = 'INTERNAL' or customer_id is not null),
    -- The last line of defence. Even if the code had a bug, a customer account could not be overdrawn.
    constraint customer_account_not_overdrawn
        check (type = 'INTERNAL' or ledger_balance_minor - held_minor >= 0)
);
create index account_customer_idx on account (customer_id);

create table journal_entry (
    id        uuid primary key,
    seq       bigint generated always as identity unique,
    reference text        not null unique,
    posted_at timestamptz not null
);

create table posting (
    id           bigint generated always as identity primary key,
    entry_id     uuid       not null references journal_entry (id),
    line_no      int        not null,
    account_id   uuid       not null references account (id),
    side         text       not null check (side in ('DEBIT', 'CREDIT')),
    amount_minor bigint     not null check (amount_minor > 0),
    currency     varchar(3) not null,
    unique (entry_id, line_no)
);
create index posting_account_idx on posting (account_id, entry_id);

-- The journal is append only. The database itself refuses to change or delete what was posted.
create function forbid_change() returns trigger
    language plpgsql as
$$
begin
    raise exception '% rows are never changed or deleted. Post a reversing entry instead.', tg_table_name
        using errcode = 'restrict_violation';
end
$$;

create trigger journal_entry_append_only
    before update or delete on journal_entry
    for each row execute function forbid_change();

create trigger posting_append_only
    before update or delete on posting
    for each row execute function forbid_change();

-- TRUNCATE empties a table without touching its rows one by one, so the two triggers above would not
-- see it. These do.
create trigger journal_entry_no_truncate
    before truncate on journal_entry
    for each statement execute function forbid_change();

create trigger posting_no_truncate
    before truncate on posting
    for each statement execute function forbid_change();

-- Debits must equal credits. The Java code checks this before it saves, and the database checks it
-- again when the transaction commits, so an unbalanced entry cannot exist whatever wrote it.
create function check_entry_balanced() returns trigger
    language plpgsql as
$$
declare
    entry      uuid;
    debits     bigint;
    credits    bigint;
    lines      int;
    currencies int;
begin
    -- This runs for a new posting and for a new journal entry. Each names the entry in its own column.
    if tg_table_name = 'posting' then
        entry := new.entry_id;
    else
        entry := new.id;
    end if;

    select coalesce(sum(amount_minor) filter (where side = 'DEBIT'), 0),
           coalesce(sum(amount_minor) filter (where side = 'CREDIT'), 0),
           count(*),
           count(distinct currency)
    into debits, credits, lines, currencies
    from posting
    where entry_id = entry;

    if lines < 2 or debits <> credits or currencies <> 1 then
        raise exception 'journal entry % is not balanced (% lines, debits %, credits %)', entry, lines, debits, credits
            using errcode = 'check_violation';
    end if;
    return null;
end
$$;

create constraint trigger posting_balanced
    after insert on posting
    deferrable initially deferred
    for each row execute function check_entry_balanced();

-- The same check from the side of the entry, so an entry with no lines at all is refused too.
create constraint trigger journal_entry_has_lines
    after insert on journal_entry
    deferrable initially deferred
    for each row execute function check_entry_balanced();

create table hold (
    id           uuid primary key,
    account_id   uuid        not null references account (id),
    amount_minor bigint      not null check (amount_minor > 0),
    currency     varchar(3)  not null,
    reference    text        not null unique,
    status       text        not null check (status in ('ACTIVE', 'RELEASED', 'CAPTURED')),
    created_at   timestamptz not null,
    expires_at   timestamptz not null,
    closed_at    timestamptz,
    version      bigint      not null default 0
);
create index hold_account_active_idx on hold (account_id) where status = 'ACTIVE';
create index hold_expiry_idx on hold (expires_at) where status = 'ACTIVE';

-- Events waiting to go to Kafka. Written in the same transaction as the change they describe.
create table outbox_event (
    seq          bigint generated always as identity primary key,
    id           uuid        not null unique,
    topic        text        not null,
    msg_key      text        not null,
    type         text        not null,
    payload      text        not null,
    traceparent  text,
    created_at   timestamptz not null default now(),
    published_at timestamptz
);
create index outbox_event_waiting_idx on outbox_event (seq) where published_at is null;

-- One row for each Idempotency-Key a client has used.
create table idempotency_record (
    scope           text        not null,
    idem_key        text        not null,
    request_hash    text        not null,
    resource_id     text,
    response_status int,
    created_at      timestamptz not null,
    primary key (scope, idem_key)
);
create index idempotency_record_age_idx on idempotency_record (created_at);

-- Proves the running totals. This view must always be empty. A test checks it, and an operator can too.
create view account_balance_mismatch as
select a.id                   as account_id,
       a.ledger_balance_minor as running_total,
       coalesce(s.from_entries, 0) as from_entries
from account a
         left join (select p.account_id,
                           sum(case
                                   when (p.side = 'CREDIT') = (acc.type <> 'INTERNAL') then p.amount_minor
                                   else -p.amount_minor
                               end) as from_entries
                    from posting p
                             join account acc on acc.id = p.account_id
                    group by p.account_id) s on s.account_id = a.id
where a.ledger_balance_minor <> coalesce(s.from_entries, 0);
