-- Messages sent to customers, and the ids of the events that were already handled.

create table notification (
    id         uuid primary key,
    seq        bigint generated always as identity unique,
    event_id   uuid        not null unique,
    event_type text        not null,
    account_id uuid        not null,
    channel    text        not null check (channel in ('IN_APP', 'EMAIL', 'SMS')),
    title      text        not null,
    body       text        not null,
    created_at timestamptz not null
);
create index notification_account_idx on notification (account_id, seq desc);

-- One row for each event a consumer has handled. Kafka can deliver an event twice. The primary key
-- makes the second insert do nothing, and the consumer then knows to skip the event.
create table processed_event (
    consumer     text        not null,
    event_id     uuid        not null,
    processed_at timestamptz not null,
    primary key (consumer, event_id)
);
