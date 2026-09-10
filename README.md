# Activity 02 - Order & Inventory (in-process modules + Supabase + React)

A single Spring Boot app with two in-process modules, `edu.cit.balacy.shop`
(Order) and `edu.cit.balacy.inventory` (Inventory), backed by a shared
Supabase Postgres database, plus a React (Vite) frontend that talks to it
over REST.

## Project layout

```
Activity02/
  src/main/java/edu/cit/balacy/
    Activity02Application.java     <- @SpringBootApplication, scans both modules
    config/WebConfig.java          <- CORS for the Vite dev server
    shop/                          <- Order module
    inventory/                     <- Inventory module (InventoryServiceImpl is package-private)
  sql/schema.sql                   <- creates + seeds inventory/orders tables
  frontend/                        <- React (Vite) app
  .env.example                     <- template for backend env vars (never commit .env)
```

## 1. Supabase setup

1. Create a free project at https://supabase.com.
2. In the Supabase dashboard, go to **Project Settings -> Database** and copy
   the connection info (host, port, database, user, password). Use the
   **Session pooler** or **direct connection** string depending on your
   network; either works for this activity.
3. Open the **SQL Editor** in Supabase, paste the contents of
   `sql/schema.sql`, and run it. This creates the `inventory` and `orders`
   tables and seeds:
   - `P100` - Wireless Mouse - stock 25
   - `P200` - Mechanical Keyboard - stock 10
   - `P300` - USB-C Hub - stock 0
4. Confirm the rows exist under **Table Editor**.

## 2. Backend environment variables

Credentials are never committed. Copy `.env.example` to `.env` and fill it
in, then export the values before running the app (or set them in your
IDE's run configuration):

```
SUPABASE_DB_URL=jdbc:postgresql://<PROJECT_REF>.supabase.co:5432/postgres
SUPABASE_DB_USERNAME=postgres
SUPABASE_DB_PASSWORD=<your-db-password>
```

On macOS/Linux you can load them into your shell before running Maven:

```bash
export $(grep -v '^#' .env | xargs)
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080` by default (`SERVER_PORT` to
override).

## 3. Frontend setup

```bash
cd frontend
npm install
npm run dev
```

Opens on `http://localhost:5173` by default. If your backend runs on a
different host/port, copy `frontend/.env.example` to `frontend/.env` and set
`VITE_API_BASE_URL`.

## 4. Testing the confirmed and rejected paths

With both servers running, open the app, open DevTools -> Network, and:

- **Confirmed path:** pick `P100` (stock 25), quantity `1`, submit. Expect
  `200 OK` with `{"status":"CONFIRMED", "reason":null, "inventory":{...,"stock":24}}`.
- **Rejected path:** pick `P300` (stock 0), any quantity, submit. Expect
  `200 OK` with `{"status":"REJECTED","reason":"Insufficient stock...", ...}`.

### Network tab evidence

_Add your screenshots here before submitting:_

**Confirmed order:**

`(screenshot placeholder - Network tab showing the POST /api/orders request/response for a CONFIRMED order)`

**Rejected order:**

`(screenshot placeholder - Network tab showing the POST /api/orders request/response for a REJECTED order)`

## 5. Reflection (300-500 words)

**1. In-process vs. separate microservices over a network.**

`OrderService` calls `InventoryService.reserve(...)` as a plain Java method
call inside the same JVM and request thread. That gets a few things for
free that a network call would not. Correctness: the inventory update and
the order insert share one `@Transactional` boundary and one Postgres
connection, so a crash mid-operation can't decrement stock without
recording an order, or vice versa - the database's ACID guarantees cover
it. Reliability: an in-process call can't time out or fail because the
"other service" is down, so no retries, circuit breakers, or idempotency
keys are needed. Simplicity: no serialization, no API versioning between
the modules, and a method-signature change is a compiler error today, not
a breaking change discovered in production later.

Splitting Inventory into its own microservice over HTTP/gRPC means adding
all of that back deliberately: a way to keep data consistent despite
partial failures (a saga or outbox pattern, since one ACID transaction
across two services isn't available), network resilience (timeouts,
retries with backoff, circuit breaking), idempotency on `reserve` so a
retried request doesn't double-decrement stock, distributed tracing across
the hop, and a versioned contract between the two services.

**2. Why `InventoryServiceImpl` is package-private.**

Package-private means the compiler - not a review comment or convention -
enforces the module boundary. Nothing in `edu.cit.balacy.shop` can import
`InventoryServiceImpl`, cast an `InventoryService` back to it, or
instantiate it, so Order is structurally forced to depend on the published
interface only. If it were `public`, nothing would stop a future teammate
from injecting the concrete class directly and quietly coupling Order's
behavior to internals that were only ever meant to be private to
Inventory. That coupling stays invisible until Inventory changes for an
unrelated reason and Order breaks with it - exactly the accidental coupling
module boundaries exist to prevent, and exactly what would make extracting
Inventory later (see below) far more painful.

**3. When to extract Inventory into its own microservice.**

Worth doing once Inventory has independent reasons to scale, deploy, or
fail separately from Order - for example, other systems need to read or
write it too (a warehouse app, a supplier feed), it has very different
load characteristics, a different team owns it and needs its own deploy
schedule, or it needs its own datastore or schema pace. To do it: swap the
in-process `InventoryService` implementation for an HTTP or messaging
client behind the same interface, so `OrderService` barely changes; give
Inventory its own datastore; replace the implicit shared transaction with
an explicit saga or outbox so a rejected reservation can't leave `orders`
inconsistent; and add the network concerns from question 1 - retries,
timeouts, idempotency keys on `reserve`, and monitoring for the new hop.
