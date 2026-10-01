# INTEGRATION.md: LegacySupply

Facts marked **(manual)** come from the interface manual Rev. 2.3.1.
Facts marked **(measured)** must be filled in by YOU from your own probing. Do not guess them.

## 1. Product mapping

Get these from `GET /catalog` (each partner has its own catalog).

| Our product ID | Name | SupplierSku | PackSize |
|---|---|---|---|
| P100 | Wireless Mouse | _fill in_ | _fill in_ |
| P200 | Mechanical Keyboard | _fill in_ | _fill in_ |
| P400 | Laptop Stand | _fill in_ | _fill in_ |

The same values go in `supplier.catalog` in `application.properties`.

## 2. Sessions

- Sign in: `POST /auth/token` with `<AuthRequest>` (ClientId + ApiKey) returns `<SessionToken>` **(manual)**.
- The token is sent in the `X-LS-Session` header on every other request **(manual)**.
- Lifetime: **(measured)** _X seconds. Describe how you measured it (interval, number of runs) and whether use extends it._
- Our handling: the adapter signs in on first use. When it gets a 401 other than E-AUTH-01 it drops the token, signs in again once, and repeats the request (without using up a retry).

## 3. Error codes I actually received

| Code | HTTP | What actually caused it (measured) |
|---|---|---|
| E-AUTH-01 | 401 | _e.g. wrong API key_ |
| E-AUTH-02 | 401 | _..._ |
| E-AUTH-03 | 401 | _..._ |
| E-AUTH-07 | 401 | _..._ |
| E-FMT-01 | 415 | _..._ |
| E-FMT-02 | 400 | _..._ |
| E-REF-05 | 400 | _..._ |
| E-SKU-02 | 422 | _..._ |
| E-QTY-11 | 422 | _..._ |
| E-IDEM-04 | 409 | _..._ |
| E-PO-04 | 404 | _..._ |
| E-QRY-06 | 400 | _..._ |
| E-RATE-03 | 429 | _..._ |
| E-SYS-50 / E-SYS-99 | 503 | _..._ |

Only list the codes you really got.

## 4. Qty and Uom

_Explain in your own words, with one worked example from a real order (need N units, PackSize P, so Qty = ceil(N / P), and the Uom in the reply)._

Manual: Qty is a whole number 1 to 99 in the supplier's unit of measure; the acknowledgement returns `Uom` (e.g. `CS`).

## 5. Status mapping

| LegacySupply StatusCode | Meaning (manual) | Our enum |
|---|---|---|
| 10 | Accepted | SUBMITTED |
| 20 | Picking | SUBMITTED |
| 30 | Shipped | IN_TRANSIT |
| 40 | Delivered | DELIVERED (publishes `SupplierOrderDeliveredEvent`, Inventory restocks) |
| _other (e.g. cancelled, add the code you saw)_ | not in the manual | CANCELLED / UNKNOWN |

**Unexpected status policy:** an unmapped code maps to UNKNOWN. The order keeps its current status, a warning with the raw code is logged, and no restock happens. After you see the real code in the logs, add it to `XmlTranslator.mapStatus`. Cancelled orders stop being polled and never restock.

## 6. Resilience

- Timeouts: connect 2 s, read 3 s.
- Retries: at most 3 attempts per call, exponential backoff with jitter (longer for 429).
- Idempotency: `X-Request-Id` is generated once when the `supplier_orders` row is created and stored in `request_id`. It is reused on every retry and after restarts. Before re-sending a PENDING order, the adapter also checks `GET /purchase-orders?buyerRef=RO-<id>`.
- No lost reorders: unreachable supplier leaves the row `PENDING`. `SupplierJobs.resubmitPending` sends it later.
- Quota: status polling checks at most `supplier.max-status-checks-per-run` orders per run and stops on the first 429 or outage.
