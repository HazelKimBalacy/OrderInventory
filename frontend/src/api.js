const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

async function request(path, options = {}) {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })

  const data = await res.json().catch(() => null)

  // A REJECTED order is a normal 200 response, not an error - only genuine
  // HTTP failures (404/409/400/500) land here.
  if (!res.ok) {
    const message = data?.message || `Request failed with status ${res.status}`
    const error = new Error(message)
    error.status = res.status
    throw error
  }

  return data
}

/** items: [{ productId, quantity }, ...] */
export function placeOrder(items) {
  return request('/api/orders', {
    method: 'POST',
    body: JSON.stringify({ items }),
  })
}

export function cancelOrder(orderId) {
  return request(`/api/orders/${orderId}/cancel`, { method: 'POST' })
}

export function getInventory() {
  return request('/api/inventory')
}

export function getOrders() {
  return request('/api/orders')
}

export function getNotifications() {
  return request('/api/notifications')
}
