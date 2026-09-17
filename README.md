# Lab 2 - Order / Inventory / Notification (modular monolith + events)

A single Spring Boot deployable with three in-process modules backed by a
shared Supabase Postgres database, plus a React (Vite) dashboard.

| Module | Package | Role |
|---|---|---|
| Order | `edu.cit.balacy.shop` | Multi-item orders, cancellation, order history |
| Inventory | `edu.cit.balacy.inventory` | Stock, reserve/restock, low-stock rule |
| Notification | `edu.cit.balacy.notification` | Listens to events, writes the activity log |

## Project layout

```
src/main/java/edu/cit/balacy/
  Activity02Application.java   <- @SpringBootApplication, scans all three modules
  config/WebConfig.java        <- CORS for the Vite dev server
  config/ApiExceptionHandler.java <- 404/409 for the cancel path, 400 for bad bodies
  events/                      <- shared event records (no module logic)
  shop/                        <- Order module
  inventory/                   <- Inventory module (InventoryServiceImpl is package-private)
  notification/                <- Notification module (listener is package-private)
sql/schema.sql                 <- recreates + seeds all four tables from scratch
frontend/                      <- React (Vite) dashboard
```

### Why there's an `events` package

The events could have lived in `shop`, but `LowStockEvent` is published by
**Inventory**, and having `inventory` import `shop` to do that would be a
worse coupling than the one it solves. A neutral, logic-free `events` package
keeps every arrow pointing the same direction: `shop -> events`,
`inventory -> events`, `notification -> events`, and nothing back.

The boundary rule the lab asks for holds, and you can verify it with grep:

```bash
grep -r "balacy.notification" src/main/java/edu/cit/balacy/shop src/main/java/edu/cit/balacy/inventory   # no hits
grep -r "balacy.shop\|balacy.inventory" src/main/java/edu/cit/balacy/notification                        # no hits
```

`shop` and `inventory` would still compile if the `notification` package were
deleted outright.

## 1. Supabase setup

1. Create a free project at https://supabase.com.
2. **Project Settings -> Database**, copy the connection info (host, port,
   database, user, password). Session pooler or direct connection both work.
3. Open the **SQL Editor**, paste `sql/schema.sql`, run it.

   > ⚠️ This script **drops and recreates** `inventory`, `orders`,
   > `order_items`, and `notifications`. The `orders` table changed shape
   > since Lab 1 (`product_id`/`quantity` moved out to `order_items`), so
   > re-running resets all order history and stock back to seed values.

4. Confirm the seeded rows under **Table Editor**:

   | product_id | name | stock |
   |---|---|---|
   | P100 | Wireless Mouse | 25 |
   | P200 | Mechanical Keyboard | 10 |
   | P300 | USB-C Hub | 0 |
   | P400 | Laptop Stand | 6 |

   `P300` at 0 makes the rejection path easy to demo; `P400` at 6 sits one
   order away from the low-stock threshold of 5.

## 2. Backend environment variables

Credentials are never committed. Copy `.env.example` to `.env` and fill in:

```
SUPABASE_DB_URL=jdbc:postgresql://<PROJECT_REF>.supabase.co:5432/postgres
SUPABASE_DB_USERNAME=postgres
SUPABASE_DB_PASSWORD=<your-db-password>
```

Optional overrides: `LOW_STOCK_THRESHOLD` (default `5`),
`CORS_ALLOWED_ORIGIN` (default `http://localhost:5173`), `SERVER_PORT`
(default `8080`).

```bash
export $(grep -v '^#' .env | xargs)
./mvnw spring-boot:run
```

## 3. Frontend setup

```bash
cd frontend
npm install
npm run dev
```

Opens on `http://localhost:5173`. Set `VITE_API_BASE_URL` in `frontend/.env`
if the backend isn't on `localhost:8080`.

## 4. API surface

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/orders` | Place a multi-item order (all-or-nothing) |
| `POST` | `/api/orders/{orderId}/cancel` | Cancel a confirmed order, restock its items |
| `GET` | `/api/orders` | Order history with status and line items |
| `GET` | `/api/inventory` | All products with current stock + `lowStock` flag |
| `GET` | `/api/notifications` | Activity log written by the Notification module |

<<<<<<< HEAD
- **Confirmed path:** pick `P100` (stock 25), quantity `1`, submit. Expect
  `200 OK` with `{"status":"CONFIRMED", "reason":null, "inventory":{...,"stock":24}}`.
  <img width="975" height="548" alt="image" src="https://github.com/user-attachments/assets/ede82a3f-7aa9-46b7-867e-afca4f989f7f" />

- **Rejected path:** pick `P300` (stock 0), any quantity, submit. Expect
  `200 OK` with `{"status":"REJECTED","reason":"Insufficient stock...", ...}`.
  <img width="975" height="548" alt="image" src="https://github.com/user-attachments/assets/82f41f7f-d187-4782-a407-fae5b3a66d70" />




=======
Request body for `POST /api/orders`:

```json
{ "items": [ { "productId": "P100", "quantity": 2 }, { "productId": "P200", "quantity": 1 } ] }
```

Response:
>>>>>>> 72867b4 (Update Activity02)

```json
{
  "orderId": 12,
  "status": "CONFIRMED",
  "reason": null,
  "items": [
    { "productId": "P100", "quantity": 2, "outcome": "RESERVED" },
    { "productId": "P200", "quantity": 1, "outcome": "RESERVED" }
  ],
  "inventory": [
    { "productId": "P100", "name": "Wireless Mouse", "stock": 23, "lowStock": false },
    { "productId": "P200", "name": "Mechanical Keyboard", "stock": 9, "lowStock": false }
  ]
}
```

Status codes: a **REJECTED order is still `200 OK`** — it's a normal business
outcome, not a transport failure. The cancel path uses `404` (no such order)
and `409` (not cancellable).

### Two implementation details worth knowing

**Duplicate products in one cart are summed before validation.** Adding 3 and
then 4 of a product with 5 in stock is caught as a request for 7, not waved
through as two individually-valid lines.

**Validation takes the same row lock as reservation.** `checkAvailability()`
uses the `PESSIMISTIC_WRITE` finder that `reserve()` uses, and `placeOrder()`
is one transaction, so the rows stay locked from the first check through the
last write. Without that, a concurrent order could invalidate a check that
had already passed — the "validate everything first" rule would be true only
in the single-user case.

**Cancelling a REJECTED order returns 409, not 200.** The spec only names the
already-CANCELLED case, but a rejected order never reserved anything, so
there's nothing to restock; treating it as cancellable would be misleading.

### On `@Async`

**Neither listener is `@Async`, deliberately.** Spring's default synchronous
publication means `NotificationService.record()` runs inside the same
transaction as the order that triggered it. That's what I want here: if the
order rolls back, so does its notification, and the activity feed can never
claim an order was confirmed when it wasn't. Making the listeners `@Async`
would put them on a separate thread with a separate transaction, buying
throughput I don't need and costing that consistency — a notification could
survive a rolled-back order, or vanish silently if the async thread threw.
If notification work later became slow (email, SMS), the right move is
`@TransactionalEventListener(phase = AFTER_COMMIT)` **plus** `@Async`, so it
fires only once the order is durably committed.

## 5. Network tab evidence

> Replace each placeholder with your own screenshot. Open DevTools → Network,
> click the request, and capture the **Headers** and **Response** panes.

**(a) Multi-item order, all items succeed → CONFIRMED**
<img width="1913" height="1079" alt="image" src="https://github.com/user-attachments/assets/06f25ea7-49e2-4d53-80a9-88e060a11bf4" />


Cart: `P100 x2` + `P200 x1`. Expect `200 OK`, `status: CONFIRMED`, every item
`RESERVED`, and both stock counts dropping in the inventory table.

**(b) Multi-item order, one item fails → whole order REJECTED, nothing reserved**

Cart: `P100 x1` (fine) + `P300 x1` (stock 0). Expect `200 OK`,
`status: REJECTED`, **both** items `NOT_RESERVED`, and `P100` stock unchanged
from before the request — that unchanged number is the actual proof of
all-or-nothing.

<img width="1919" height="1079" alt="image" src="https://github.com/user-attachments/assets/c140ebf5-85d8-483f-b5cf-1014eaff76fc" />


**(c) Cancel with restock reflected in GET /api/inventory**

Place `P100 x3` (25 → 22), then hit Cancel on that order. Expect `200 OK`
with `status: CANCELLED` and `outcome: RESTOCKED`, then a follow-up
`GET /api/inventory` showing `P100` back at 25.
<img width="1910" height="1079" alt="image" src="https://github.com/user-attachments/assets/9178470d-8aaf-4ec9-9ab1-61d639925a29" />



**(d) Notification feed: confirmed + rejected + low stock**

Order `P400 x2` (6 → 4, crosses the threshold of 5). Then
`GET /api/notifications` shows an `ORDER_CONFIRMED` entry, a `LOW_STOCK`
"reorder needed" entry, and — after running scenario (b) — an
`ORDER_REJECTED` entry.
<img width="1919" height="1079" alt="image" src="https://github.com/user-attachments/assets/62a90520-aaec-4dbc-a66b-8b38598f709d" />



## 6. Reflection

**Atomicity across repeated `InventoryService` calls.** A multi-item order
calls into Inventory many times — once to validate each line, once to reserve
each line — but every one of those calls runs on the same thread, in the same
JVM, inside the single `@Transactional` boundary opened by
`OrderService.placeOrder()`. They share one database connection and one
Postgres transaction, so the guarantee isn't something my code implements; it
is the database's ACID commit. If line four fails after lines one through
three were decremented, the rollback undoes all of them with no compensating
code. I layered two things on top: pessimistic row locks held from first
check to final write, so no concurrent order can invalidate a passed check,
and per-product aggregation, so duplicate cart lines are validated as one
total. Split Order and Inventory across a network and all of that evaporates.
There is no distributed transaction to enlist in, so I would need a saga:
Order issues reserve calls as separate steps and, on any failure, issues
explicit compensating `restock` calls for whatever already succeeded. That
introduces problems the monolith never has — compensation itself can fail, so
it needs retries and a dead-letter path; `reserve` needs idempotency keys so a
retried request doesn't double-decrement; and there is a real window where
stock is reserved for an order that will never be confirmed.

**Events versus a direct call.** Publishing `OrderPlacedEvent` instead of
calling a notification service inverts the dependency. `OrderService` names
no notification type, imports nothing from that package, and would compile
unchanged if the module were deleted; Notification depends on the event
records only. The cost is that the coupling still exists, it just moved into
the event's shape and became invisible to the compiler — renaming a field
breaks a listener at runtime, not at build time. As a separate microservice,
the in-memory `ApplicationEventPublisher` would need replacing with a broker
(Kafka, RabbitMQ), and everything currently free would become explicit
design: at-least-once delivery, so listeners must be idempotent; an outbox
table, since publishing to a broker and committing the order are no longer
one atomic act; ordering guarantees; and retry/DLQ handling.

**Which module I would extract first.** Notification. It is already the only
module nothing depends on — the dependency arrows point exclusively inward
— so extracting it breaks no caller. Its data is self-contained (the
`notifications` table is touched by nothing else), and it needs no
consistency guarantee with the order transaction beyond "eventually." In
practice: swap `ApplicationEventPublisher` for a broker publisher in Order
and Inventory, move the event records to a shared contract library, and
replace `@EventListener` with a message consumer. Inventory would be the
worst first choice, since it sits on the hot transactional path that every
order depends on.
