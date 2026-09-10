import { useState } from 'react'
import { placeOrder } from './api'

// Mirrors the seed data in sql/schema.sql. There's no GET /api/inventory
// endpoint in this activity's spec, so the product list is static here;
// the live stock count comes back from the server on every order attempt.
const PRODUCTS = [
  { id: 'P100', label: 'P100 - Wireless Mouse' },
  { id: 'P200', label: 'P200 - Mechanical Keyboard' },
  { id: 'P300', label: 'P300 - USB-C Hub' },
]

export default function App() {
  const [productId, setProductId] = useState(PRODUCTS[0].id)
  const [quantity, setQuantity] = useState(1)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setLoading(true)
    setError(null)
    setResult(null)

    try {
      const data = await placeOrder(productId, Number(quantity))
      setResult(data)
    } catch (err) {
      setError(err.message || 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <h1>Place an Order</h1>

      <form onSubmit={handleSubmit} className="order-form">
        <label>
          Product
          <select value={productId} onChange={(e) => setProductId(e.target.value)}>
            {PRODUCTS.map((p) => (
              <option key={p.id} value={p.id}>
                {p.label}
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

        <button type="submit" disabled={loading}>
          {loading ? 'Submitting...' : 'Submit Order'}
        </button>
      </form>

      {error && <div className="result error">Request error: {error}</div>}

      {result && (
        <div className={`result ${result.status === 'CONFIRMED' ? 'confirmed' : 'rejected'}`}>
          <div className="status">{result.status}</div>
          {result.reason && <div className="reason">{result.reason}</div>}
          {result.inventory && (
            <div className="inventory">
              Current stock of {result.inventory.name}: {result.inventory.stock}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
