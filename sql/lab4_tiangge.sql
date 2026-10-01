-- Lab 4: run once in the existing Supabase SQL editor. This is additive and
-- does not reset inventory, orders, notifications, or supplier purchase orders.

create table if not exists channel_state (
    id          integer primary key check (id = 1),
    feed_cursor bigint not null
);

create table if not exists channel_processed_events (
    event_id        text primary key,
    sequence_number bigint not null
);

create index if not exists idx_channel_events_sequence
    on channel_processed_events (sequence_number);

create table if not exists channel_orders (
    tiangge_order_id    text primary key,
    local_order_id      bigint not null,
    lines_json          text not null,
    decision            varchar(20) check (decision in ('ACCEPTED', 'REJECTED', 'BACKORDERED')),
    reason              varchar(200),
    decision_sent       boolean not null default false,
    cancellation_pending boolean not null default false,
    cancellation_sent   boolean not null default false,
    resolution          varchar(20) check (resolution in ('ACCEPTED', 'CANCELLED')),
    resolution_sent     boolean not null default false
);

create table if not exists channel_stock_outbox (
    product_id text primary key,
    available  integer not null check (available >= 0)
);
