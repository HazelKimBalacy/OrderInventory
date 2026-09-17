import { useCallback, useEffect, useState } from 'react'
import {
  cancelOrder,
  getInventory,
  getNotifications,
  getOrders,
  placeOrder,
} from './api'

const NOTIFICATION_LABELS = {
  ORDER_CONFIRMED: 'Confirmed',
  ORDER_REJECTED: 'Rejected',
  ORDER_CANCELLED: 'Cancelled',
  LOW_STOCK: 'Reorder',
}

export default function App() {
  const [inventory, setInventory] = useState([])
  const [orders, setOrders] = useState([])
  const [notifications, setNotifications] = useState([])

  const [cart, setCart] = useState([])
  const [selectedProduct, setSelectedProduct] = useState('')
  const [quantity, setQuantity] = useState(1)

  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  // Every order/cancel re-pulls all three views, so the dashboard always
  // reflects what the backend actually did rather than a guess.
  const refresh = useCallback(async () => {
    try {
      const [inv, ord, notes] = await Promise.all([
        getInventory(),
        getOrders(),
        getNotifications(),
      ])
      setInventory(inv)
      setOrders(ord)
      setNotifications(notes)
      if (inv.length && !selectedProduct) setSelectedProduct(inv[0].productId)
    } catch (err) {
      setError(err.message)
    }
  }, [selectedProduct])

  useEffect(() => {
    refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function addToCart(e) {
    e.preventDefault()
    const qty = Number(quantity)
    if (!selectedProduct || qty < 1) return

    setCart((prev) => {
      const existing = prev.find((l) => l.productId === selectedProduct)
      if (existing) {
        return prev.map((l) =>
          l.productId === selectedProduct ? { ...l, quantity: l.quantity + qty } : l,
        )
      }
      return [...prev, { productId: selectedProduct, quantity: qty }]
    })
    setQuantity(1)
  }

  function updateCartQty(productId, qty) {
    setCart((prev) =>
      prev.map((l) => (l.productId === productId ? { ...l, quantity: Math.max(1, qty) } : l)),
    )
  }

  function removeFromCart(productId) {
    setCart((prev) => prev.filter((l) => l.productId !== productId))
  }

  async function submitOrder() {
    if (!cart.length) return
    setBusy(true)
    setError(null)
    setResult(null)
    try {
      const data = await placeOrder(cart)
      setResult(data)
      if (data.status === 'CONFIRMED') setCart([])
      await refresh()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function handleCancel(orderId) {
    setBusy(true)
    setError(null)
    setResult(null)
    try {
      const data = await cancelOrder(orderId)
      setResult(data)
      await refresh()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const productName = (id) =>
    inventory.find((i) => i.productId === id)?.name ?? id

  return (
    <div className="page">
      <h1>Order &amp; Inventory</h1>

      {error && <div className="banner error">Request error: {error}</div>}

      {result && (
        <div className={`banner ${result.status.toLowerCase()}`}>
          <strong>
            Order O{result.orderId} - {result.status}
          </strong>
          {result.reason && <div className="reason">{result.reason}</div>}
          {result.items?.length > 0 && (
            <ul className="outcomes">
              {result.items.map((i) => (
                <li key={i.productId}>
                  {i.quantity}x {i.productId} - {i.outcome}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      <section className="card">
        <h2>Build an order</h2>
        <form className="cart-form" onSubmit={addToCart}>
          <label>
            Product
            <select
              value={selectedProduct}
              onChange={(e) => setSelectedProduct(e.target.value)}
            >
              {inventory.map((item) => (
                <option key={item.productId} value={item.productId}>
                  {item.productId} - {item.name} (stock {item.stock})
                </option>
              ))}
            </select>
          </label>
          <label>
            Quantity
            <input
              type="number"
              min="1"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
          </label>
          <button type="submit">Add to cart</button>
        </form>

        {cart.length === 0 ? (
          <p className="muted">Cart is empty. Add at least one product.</p>
        ) : (
          <>
            <table className="table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Qty</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {cart.map((line) => (
                  <tr key={line.productId}>
                    <td>
                      {line.productId} - {productName(line.productId)}
                    </td>
                    <td>
                      <input
                        type="number"
                        min="1"
                        value={line.quantity}
                        onChange={(e) =>
                          updateCartQty(line.productId, Number(e.target.value))
                        }
                      />
                    </td>
                    <td>
                      <button
                        type="button"
                        className="link-danger"
                        onClick={() => removeFromCart(line.productId)}
                      >
                        Remove
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <button
              type="button"
              className="primary"
              onClick={submitOrder}
              disabled={busy}
            >
              {busy ? 'Submitting...' : `Submit order (${cart.length} item${cart.length > 1 ? 's' : ''})`}
            </button>
          </>
        )}
      </section>

      <section className="card">
        <h2>Inventory</h2>
        <table className="table">
          <thead>
            <tr>
              <th>Product</th>
              <th>Name</th>
              <th>Stock</th>
            </tr>
          </thead>
          <tbody>
            {inventory.map((item) => (
              <tr key={item.productId} className={item.lowStock ? 'low-stock' : ''}>
                <td>{item.productId}</td>
                <td>{item.name}</td>
                <td>
                  {item.stock}
                  {item.lowStock && <span className="tag">low</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card">
        <h2>Order history</h2>
        {orders.length === 0 ? (
          <p className="muted">No orders yet.</p>
        ) : (
          <ul className="order-list">
            {orders.map((order) => (
              <li key={order.orderId} className={`order ${order.status.toLowerCase()}`}>
                <div className="order-head">
                  <span className="order-id">O{order.orderId}</span>
                  <span className={`status ${order.status.toLowerCase()}`}>
                    {order.status}
                  </span>
                  {order.status === 'CONFIRMED' && (
                    <button
                      type="button"
                      className="link-danger"
                      onClick={() => handleCancel(order.orderId)}
                      disabled={busy}
                    >
                      Cancel
                    </button>
                  )}
                </div>
                <div className="order-items">
                  {order.items.map((i) => `${i.quantity}x ${i.productId}`).join(', ')}
                </div>
                {order.reason && <div className="reason">{order.reason}</div>}
                <div className="muted small">
                  {new Date(order.createdAt).toLocaleString()}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card">
        <h2>Activity feed</h2>
        {notifications.length === 0 ? (
          <p className="muted">No notifications yet.</p>
        ) : (
          <ul className="feed">
            {notifications.map((n) => (
              <li key={n.notificationId} className={n.type.toLowerCase()}>
                <span className={`tag ${n.type.toLowerCase()}`}>
                  {NOTIFICATION_LABELS[n.type] ?? n.type}
                </span>
                <span className="feed-message">{n.message}</span>
                <span className="muted small">
                  {new Date(n.createdAt).toLocaleTimeString()}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
