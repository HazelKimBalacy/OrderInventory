-- Lab 2 schema. Run this once in the Supabase SQL Editor (or via psql)
-- against your project's Postgres database before starting the Spring Boot
-- app. The app connects with ddl-auto=validate, so it expects these tables
-- (and these column names) to already exist.
--
-- This script recreates the current schema from scratch. It drops the Lab 1
-- tables first because the `orders` table changed shape: product_id and
-- quantity moved out to the new `order_items` table, since an order can now
-- carry multiple line items. Re-running this is destructive - it resets all
-- order history, notifications, and stock levels back to the seed values.

drop table if exists order_items;
drop table if exists notifications;
drop table if exists orders;
drop table if exists inventory;

-- Inventory ---------------------------------------------------------------
create table inventory (
    product_id text primary key,
    name       text    not null,
    stock      integer not null check (stock >= 0)
);

-- Orders ------------------------------------------------------------------
-- status now allows CANCELLED alongside CONFIRMED/REJECTED.
create table orders (
    order_id   bigint generated always as identity primary key,
    status     text not null check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED')),
    reason     text,
    created_at timestamptz not null default now()
);

-- Order line items --------------------------------------------------------
-- One row per product in an order. Cascades on delete so removing an order
-- never leaves orphaned lines behind.
create table order_items (
    order_item_id bigint generated always as identity primary key,
    order_id      bigint  not null references orders (order_id) on delete cascade,
    product_id    text    not null,
    quantity      integer not null check (quantity > 0)
);

create index idx_order_items_order_id on order_items (order_id);

-- Notifications -----------------------------------------------------------
-- Written by the Notification module's @EventListener. The `type` column is
-- an addition beyond the minimum columns: the lab requires low-stock entries
-- to be distinguishable from order confirmation/rejection entries, and a
-- typed column does that without the UI having to pattern-match message text.
create table notifications (
    notification_id bigint generated always as identity primary key,
    type            text not null check (type in (
                        'ORDER_CONFIRMED', 'ORDER_REJECTED', 'ORDER_CANCELLED', 'LOW_STOCK')),
    message         text not null,
    created_at      timestamptz not null default now()
);

create index idx_notifications_created_at on notifications (created_at desc);

-- Seed data ---------------------------------------------------------------
-- P400 starts at 6 so a single order of 2 pushes it under the low-stock
-- threshold of 5, which makes the LowStock event easy to demo on purpose.
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse',      25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub',            0),
    ('P400', 'Laptop Stand',         6);
