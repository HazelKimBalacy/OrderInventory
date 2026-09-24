-- Lab 3: run once in the Supabase SQL editor. Does NOT touch existing tables.
-- Also paste this at the bottom of schema.sql (and add
-- `drop table if exists supplier_orders;` at the top) so the repo stays complete.

create table if not exists supplier_orders (
    id          bigint generated always as identity primary key,
    product_id  text    not null,
    buyer_ref   text    unique,               -- "RO-" || id, set right after insert
    request_id  text    not null unique,      -- X-Request-Id, fixed for the reorder's lifetime
    po_number   text,
    cases       integer not null check (cases > 0),
    units       integer not null check (units > 0),
    status      text    not null check (status in (
                    'PENDING','SUBMITTED','IN_TRANSIT','DELIVERED','CANCELLED','FAILED','UNKNOWN')),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create index if not exists idx_supplier_orders_status on supplier_orders (status);
