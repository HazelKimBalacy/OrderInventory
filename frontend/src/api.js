const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export async function placeOrder(productId, quantity) {
  const res = await fetch(`${API_BASE_URL}/api/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ productId, quantity }),
  })

  const data = await res.json().catch(() => null)

  if (!res.ok && !data) {
    throw new Error(`Request failed with status ${res.status}`)
  }

  return data
}
