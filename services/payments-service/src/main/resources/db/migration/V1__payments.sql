-- Payments and the state of their saga, the messages exchanged with the rails, the ACH batch
-- simulator's tables, and the outbox and idempotency tables every writing service has.

create table payment (
    id                   uuid primary key,
    -- Given by the database in arrival order. It is the stable order for paging.
    seq                  bigint generated always as identity unique,
    debtor_account_id    uuid        not null,
    creditor_name        text        not null,
    creditor_account     text        not null,
    creditor_routing     text,
    amount_minor         bigint      not null check (amount_minor > 0),
    currency             varchar(3)  not null,
    rail                 text        not null check (rail in ('BOOK', 'ACH', 'INSTANT')),
    status               text        not null check (status in ('RCVD', 'ACCP', 'ACSP', 'ACSC', 'RJCT', 'CANC')),
    reason_code          text,
    reason_detail        text,
    end_to_end_id        text,
    remittance_info      text,
    hold_id              uuid,
    hold_release_pending boolean     not null default false,
    rail_reference       text,
    settlement_date      date,
    created_at           timestamptz not null,
    settled_at           timestamptz,
    updated_at           timestamptz not null default now(),
    version              bigint      not null default 0,

    -- Saga bookkeeping.
    -- next_attempt_at is set while the saga still has work to do for this payment. Null means done.
    next_attempt_at      timestamptz,
    -- locked_until is the claim. While it is in the future, one worker owns the payment.
    locked_until         timestamptz,
    attempts             int         not null default 0,
    last_error           text,
    -- failures counts the errors that are not expected to pass by themselves. After a few in a row
    -- the payment is parked. It leaves the worker's list and waits for a person. To let the saga try
    -- a parked payment again, once the cause is fixed
    --   update payment set parked_at = null, failures = 0, attempts = 0, next_attempt_at = now() where id = '...';
    failures             int         not null default 0,
    parked_at            timestamptz
);
create index payment_due_idx on payment (next_attempt_at) where next_attempt_at is not null;
create index payment_parked_idx on payment (parked_at) where parked_at is not null;
create index payment_account_idx on payment (debtor_account_id, seq desc);

-- A copy of every message exchanged with a rail.
create table payment_message (
    id         bigint generated always as identity primary key,
    payment_id uuid        not null references payment (id),
    direction  text        not null check (direction in ('OUT', 'IN')),
    type       text        not null,
    body       text        not null,
    created_at timestamptz not null default now()
);
create index payment_message_payment_idx on payment_message (payment_id, id);

-- The ACH simulator. Payments wait as entries, are grouped into a batch at each cut-off, and the
-- batch is settled later, the way the real network works in days.
create table ach_batch (
    id           uuid primary key,
    status       text        not null check (status in ('SUBMITTED', 'SETTLED')),
    entry_count  int         not null,
    total_minor  bigint      not null,
    file         text        not null,
    submitted_at timestamptz not null,
    settled_at   timestamptz
);

-- Trace numbers. Each ACH entry gets the bank's routing prefix and the next seven digit number.
-- After 9,999,999 the numbers start again, as they do on the real network, where a trace number
-- only has to be unique within one file.
create sequence ach_trace_seq maxvalue 9999999 cycle;

create table ach_entry (
    payment_id   uuid primary key references payment (id),
    trace_number text        not null,
    batch_id     uuid references ach_batch (id),
    status       text        not null check (status in ('QUEUED', 'SUBMITTED', 'SETTLED', 'RETURNED')),
    return_code  text,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);
create index ach_entry_queued_idx on ach_entry (created_at) where status = 'QUEUED';

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
