const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

function basicAuthorization(credentials) {
  const bytes = new TextEncoder().encode(`${credentials.username}:${credentials.password}`)
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return `Basic ${btoa(binary)}`
}

async function request(path, { credentials, ...options } = {}) {
  const headers = new Headers(options.headers)
  if (credentials) {
    headers.set('Authorization', basicAuthorization(credentials))
  }
  if (options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
  } catch (error) {
    if (error instanceof TypeError) {
      throw new Error(`Cannot reach the SmartStock API at ${API_BASE_URL}. Start the Spring Boot backend and check its CORS settings.`)
    }
    throw error
  }
  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('application/json')
    ? await response.json()
    : await response.text()

  if (!response.ok) {
    const message = typeof payload === 'string'
      ? payload
      : payload.message || payload.error || 'The request could not be completed.'
    if (response.status === 401) {
      throw new Error('Invalid credentials or your session has expired. Please sign in again.')
    }
    if (response.status === 403) {
      throw new Error('Administrator access is required to use this feature.')
    }
    throw new Error(message)
  }

  return payload
}

export const api = {
  login: ({ username, password }) => request('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  }),

  register: (details) => request('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(details),
  }),

  getStocks: (credentials) => request('/api/v1/stocks', { credentials }),

  getStockHistory: (credentials, symbol, range) => request(
    `/api/v1/stocks/${encodeURIComponent(symbol)}/history?range=${encodeURIComponent(range)}`,
    { credentials },
  ),

  getCompanyFundamentals: (credentials, symbol) => request(
    `/api/v1/stocks/${encodeURIComponent(symbol)}/fundamentals`,
    { credentials },
  ),

  getPortfolios: (credentials) => request('/api/v1/portfolios', { credentials }),

  getAdminDashboard: (credentials) => request('/api/v1/admin/dashboard', { credentials }),

  getAdminUsers: (credentials) => request('/api/v1/admin/users', { credentials }),

  getAdminUser: (credentials, userId) => request(
    `/api/v1/admin/users/${encodeURIComponent(userId)}`,
    { credentials },
  ),

  updateAdminUserRole: (credentials, userId, role) => request(
    `/api/v1/admin/users/${encodeURIComponent(userId)}/role`,
    { credentials, method: 'PUT', body: JSON.stringify({ role }) },
  ),

  getAdminStocks: (credentials) => request('/api/v1/admin/stocks', { credentials }),

  getAdminStock: (credentials, symbol) => request(
    `/api/v1/admin/stocks/${encodeURIComponent(symbol)}`,
    { credentials },
  ),

  createAdminStock: (credentials, stock) => request('/api/v1/admin/stocks', {
    credentials,
    method: 'POST',
    body: JSON.stringify(stock),
  }),

  updateAdminStock: (credentials, symbol, stock) => request(
    `/api/v1/admin/stocks/${encodeURIComponent(symbol)}`,
    { credentials, method: 'PUT', body: JSON.stringify(stock) },
  ),

  deleteAdminStock: (credentials, symbol) => request(
    `/api/v1/admin/stocks/${encodeURIComponent(symbol)}`,
    { credentials, method: 'DELETE' },
  ),

  getWatchlist: (credentials) => request('/api/v1/watchlist', { credentials }),

  addWatchlistStock: (credentials, symbol) => request('/api/v1/watchlist', {
    credentials,
    method: 'POST',
    body: JSON.stringify({ symbol }),
  }),

  removeWatchlistStock: (credentials, symbol) => request(
    `/api/v1/watchlist/${encodeURIComponent(symbol)}`,
    { credentials, method: 'DELETE' },
  ),

  createPortfolio: (credentials, name) => request(
    `/api/v1/portfolios?name=${encodeURIComponent(name)}`,
    { credentials, method: 'POST' },
  ),

  trade: (credentials, portfolioId, tradeType, trade) => request(
    `/api/v1/portfolios/${portfolioId}/${tradeType}`,
    {
      credentials,
      method: 'POST',
      body: JSON.stringify(trade),
    },
  ),
}
