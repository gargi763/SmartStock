import { useEffect, useMemo, useState } from 'react'
import { api } from './services/api'
import InvestmentJournal from './InvestmentJournal'
import InvestmentSimulator from './InvestmentSimulator'
import MarketHeatmap from './MarketHeatmap'
import './App.css'

const moneyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
})

function formatMoney(value) {
  const amount = Number(value ?? 0)
  return Number.isFinite(amount) ? moneyFormatter.format(amount) : '—'
}

function formatFundamentalCurrency(value, currency) {
  if (value == null || !Number.isFinite(Number(value))) return 'Not available'
  const amount = Number(value)
  if (!currency || !/^[A-Z]{3}$/.test(currency)) {
    return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(amount)} (currency not provided)`
  }
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(amount)
}

function formatFundamentalRatio(value) {
  if (value == null || !Number.isFinite(Number(value))) return 'Not available'
  return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(Number(value))}×`
}

function formatFundamentalPercent(value) {
  if (value == null || !Number.isFinite(Number(value))) return 'Not available'
  return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(Number(value) * 100)}%`
}

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function getAllocationSlices(holdings, totalValue) {
  let allocationStart = 0
  return holdings.map((holding) => {
    const start = allocationStart
    allocationStart += (holding.allocationValue / totalValue) * 100
    return { ...holding, start, end: allocationStart }
  })
}

function getPriceChart(prices) {
  if (prices.length === 0) return null

  const values = prices.map((point) => Number(point.close))
  const minimum = Math.min(...values)
  const maximum = Math.max(...values)
  const padding = (maximum - minimum) * 0.08 || Math.max(maximum * 0.01, 0.01)
  const lowerBound = minimum - padding
  const upperBound = maximum + padding
  const width = 800
  const height = 240
  const horizontalPadding = 18
  const points = prices.map((point, index) => {
    const x = prices.length === 1
      ? width / 2
      : horizontalPadding + (index / (prices.length - 1)) * (width - horizontalPadding * 2)
    const y = height - ((Number(point.close) - lowerBound) / (upperBound - lowerBound)) * height
    return { x, y, ...point }
  })

  return {
    points,
    minimum,
    maximum,
    polyline: points.map((point) => `${point.x},${point.y}`).join(' '),
  }
}

function AuthPanel({ onAuthenticate, error, busy }) {
  const [mode, setMode] = useState('login')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  async function handleSubmit(event) {
    event.preventDefault()
    await onAuthenticate({ mode, username, email, password })
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="brand auth-brand">
          <div className="brand-mark">S</div>
          <h1>SmartStock</h1>
        </div>
        <p className="eyebrow">Virtual investing workspace</p>
        <h2>{mode === 'login' ? 'Welcome back' : 'Create your account'}</h2>
        <p className="auth-intro">
          Sign in to view your portfolios and simulated trades.
        </p>

        {error && <p className="alert error-alert" role="alert">{error}</p>}

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            Username
            <input
              autoComplete="username"
              required
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </label>
          {mode === 'register' && (
            <label>
              Email
              <input
                autoComplete="email"
                type="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>
          )}
          <label>
            Password
            <input
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              type="password"
              minLength={6}
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          <button className="primary-btn auth-submit" type="submit" disabled={busy}>
            {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        <button
          className="text-btn"
          type="button"
          onClick={() => setMode(mode === 'login' ? 'register' : 'login')}
        >
          {mode === 'login' ? 'New to SmartStock? Create an account' : 'Already registered? Sign in'}
        </button>
        <div className="demo-hint">
          <strong>Demo account</strong>
          <span>Username: demo · Password: demo123</span>
        </div>
      </section>
    </main>
  )
}

function StockIntelligence({ stocks, credentials, hasPortfolio, initialSymbol, onBackToDashboard, onTrade }) {
  const [stockSearch, setStockSearch] = useState('')
  const [selectedSymbol, setSelectedSymbol] = useState(initialSymbol || stocks[0]?.symbol || '')
  const [historyRange, setHistoryRange] = useState('1M')
  const [historyData, setHistoryData] = useState(null)
  const [historyLoading, setHistoryLoading] = useState(Boolean(stocks[0]?.symbol))
  const [historyError, setHistoryError] = useState('')
  const [historyRetry, setHistoryRetry] = useState(0)
  const [fundamentals, setFundamentals] = useState(null)
  const [fundamentalsLoading, setFundamentalsLoading] = useState(Boolean(stocks[0]?.symbol))
  const [fundamentalsError, setFundamentalsError] = useState('')
  const [fundamentalsRetry, setFundamentalsRetry] = useState(0)
  const [investmentAmount, setInvestmentAmount] = useState('')
  const [quantity, setQuantity] = useState('')
  const [buyPrice, setBuyPrice] = useState(String(
    stocks.find((stock) => stock.symbol === initialSymbol)?.currentPrice ?? stocks[0]?.currentPrice ?? '',
  ))
  const [targetPrice, setTargetPrice] = useState('')

  const matchingStocks = stocks.filter((stock) => {
    const query = stockSearch.trim().toLowerCase()
    return !query
      || stock.symbol.toLowerCase().includes(query)
      || stock.companyName.toLowerCase().includes(query)
      || (stock.sector || '').toLowerCase().includes(query)
  })
  const selectedStock = stocks.find((stock) => stock.symbol === selectedSymbol)
  const numericQuantity = Number(quantity)
  const numericBuyPrice = Number(buyPrice)
  const numericTargetPrice = Number(targetPrice)
  const calculatorReady = numericQuantity > 0
    && numericBuyPrice > 0
    && targetPrice.trim() !== ''
    && numericTargetPrice >= 0
  const estimatedInvestment = calculatorReady ? numericQuantity * numericBuyPrice : 0
  const potentialProfitLoss = calculatorReady
    ? numericQuantity * (numericTargetPrice - numericBuyPrice)
    : 0
  const potentialReturn = calculatorReady && estimatedInvestment > 0
    ? (potentialProfitLoss / estimatedInvestment) * 100
    : 0
  const dayChangePercent = Number(selectedStock?.dayChangePercent)
  const hasDayChangePercent = selectedStock?.dayChangePercent != null && Number.isFinite(dayChangePercent)
  const isDemoPrice = selectedStock?.marketSource?.toLowerCase() === 'demo'

  useEffect(() => {
    if (!selectedSymbol) {
      return undefined
    }

    let cancelled = false

    api.getStockHistory(credentials, selectedSymbol, historyRange)
      .then((data) => {
        if (!cancelled) setHistoryData(data)
      })
      .catch((requestError) => {
        if (!cancelled) setHistoryError(requestError.message)
      })
      .finally(() => {
        if (!cancelled) setHistoryLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [credentials, historyRange, historyRetry, selectedSymbol])

  useEffect(() => {
    if (!selectedSymbol) return undefined

    let cancelled = false
    api.getCompanyFundamentals(credentials, selectedSymbol)
      .then((data) => {
        if (!cancelled) setFundamentals(data)
      })
      .catch((requestError) => {
        if (!cancelled) setFundamentalsError(requestError.message)
      })
      .finally(() => {
        if (!cancelled) setFundamentalsLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [credentials, fundamentalsRetry, selectedSymbol])

  const priceChart = getPriceChart(historyData?.prices || [])
  const hasFundamentalMetrics = fundamentals && [
    fundamentals.companyName,
    fundamentals.exchange,
    fundamentals.sector,
    fundamentals.marketCapitalization,
    fundamentals.peRatio,
    fundamentals.earningsPerShare,
    fundamentals.priceToBookRatio,
    fundamentals.dividendYield,
    fundamentals.revenue,
    fundamentals.netIncome,
    fundamentals.profitMargin,
  ].some((value) => value != null && value !== '')

  function updateInvestmentAmount(value) {
    setInvestmentAmount(value)
    if (Number(value) > 0 && numericBuyPrice > 0) {
      setQuantity((Number(value) / numericBuyPrice).toFixed(6).replace(/\.?0+$/, ''))
    } else if (value === '') {
      setQuantity('')
    } else if (Number(value) === 0) {
      setQuantity('0')
    }
  }

  function updateQuantity(value) {
    setQuantity(value)
    if (Number(value) > 0 && numericBuyPrice > 0) {
      setInvestmentAmount((Number(value) * numericBuyPrice).toFixed(2))
    } else if (!value) {
      setInvestmentAmount('')
    }
  }

  function updateBuyPrice(value) {
    setBuyPrice(value)
    const parsedPrice = Number(value)
    if (Number(investmentAmount) > 0 && parsedPrice > 0) {
      setQuantity((Number(investmentAmount) / parsedPrice).toFixed(6).replace(/\.?0+$/, ''))
    }
  }

  function selectStock(symbol) {
    setSelectedSymbol(symbol)
    setHistoryData(null)
    setHistoryError('')
    setHistoryLoading(Boolean(symbol))
    setFundamentals(null)
    setFundamentalsError('')
    setFundamentalsLoading(Boolean(symbol))
    const stock = stocks.find((item) => item.symbol === symbol)
    setBuyPrice(String(stock?.currentPrice ?? ''))
    setTargetPrice('')
  }

  function searchStocks(value) {
    setStockSearch(value)
    const query = value.trim().toLowerCase()
    const matches = stocks.filter((stock) => (
      !query
      || stock.symbol.toLowerCase().includes(query)
      || stock.companyName.toLowerCase().includes(query)
      || (stock.sector || '').toLowerCase().includes(query)
    ))
    if (!matches.some((stock) => stock.symbol === selectedSymbol)) {
      const nextStock = matches[0]
      selectStock(nextStock?.symbol || '')
    }
  }

  return (
    <main className="intelligence-page">
      <header className="intelligence-topbar">
        <button className="intelligence-back" type="button" onClick={onBackToDashboard}>
          <span aria-hidden="true">←</span> Dashboard
        </button>
        <span className="intelligence-brand">SMARTSTOCK <i /> RESEARCH</span>
      </header>

      <section className="intelligence-heading">
        <div>
          <p className="intelligence-kicker">MARKET RESEARCH TERMINAL</p>
          <h1>Stock Intelligence</h1>
          <p>Explore supported securities, review available market data, and model a hypothetical trade.</p>
        </div>
        <label className="intelligence-selector">
          <span>Find a supported stock</span>
          <input
            type="search"
            list="intelligence-stock-options"
            value={stockSearch}
            onChange={(event) => searchStocks(event.target.value)}
            placeholder="Search symbol or company"
          />
          <datalist id="intelligence-stock-options">
            {matchingStocks.map((stock) => (
              <option key={stock.symbol} value={stock.symbol}>{stock.companyName}</option>
            ))}
          </datalist>
          <select
            value={selectedSymbol}
            onChange={(event) => selectStock(event.target.value)}
            aria-label="Select stock"
            disabled={matchingStocks.length === 0}
          >
            {matchingStocks.map((stock) => (
              <option key={stock.symbol} value={stock.symbol}>{stock.symbol} · {stock.companyName}</option>
            ))}
          </select>
        </label>
      </section>

      {!selectedStock ? (
        <section className="intelligence-empty">
          <h2>No supported stock selected</h2>
          <p>{stocks.length === 0 ? 'The stock API returned no securities.' : matchingStocks.length === 0 ? 'No stocks match your search. Clear the search to browse supported securities.' : 'Choose a supported stock to view its research details.'}</p>
        </section>
      ) : (
        <>
          <section className="intelligence-stock-header">
            <div className="intelligence-stock-title">
              <span className="intelligence-symbol">{selectedStock.symbol}</span>
              <div>
                <h2>{selectedStock.companyName}</h2>
                <p>{selectedStock.sector || 'Sector not provided'} <span>·</span> {isDemoPrice ? 'Simulated demo quote' : `Source: ${selectedStock.marketSource || 'unspecified'}`}</p>
              </div>
            </div>
            <div className="intelligence-quote">
              <span>Latest available price</span>
              <strong>{formatMoney(selectedStock.currentPrice)}</strong>
              <small className={Number(selectedStock.dayChange) >= 0 ? 'positive' : 'negative'}>
                {selectedStock.dayChange == null
                  ? 'Change not provided'
                  : `${formatMoney(selectedStock.dayChange)}${hasDayChangePercent ? ` (${dayChangePercent.toFixed(2)}%)` : ''} API day change`}
              </small>
            </div>
            <div className="intelligence-trade-actions">
              <button type="button" disabled={!hasPortfolio} onClick={() => onTrade(selectedStock, 'buy')}>Buy</button>
              <button type="button" disabled={!hasPortfolio} onClick={() => onTrade(selectedStock, 'sell')}>Sell</button>
            </div>
          </section>
          {!hasPortfolio && (
            <p className="intelligence-portfolio-note">
              Create a portfolio on the Dashboard to enable virtual trading.
            </p>
          )}

          <section className="intelligence-grid">
            <article className="intelligence-card intelligence-price-card">
              <div className="intelligence-card-heading">
                <div>
                  <span className="intelligence-kicker">PRICE OVERVIEW</span>
                  <h3>Historical prices</h3>
                </div>
                <span className="intelligence-live-tag">HISTORICAL DAILY</span>
              </div>
              <div className="intelligence-price-summary">
                <strong>{formatMoney(selectedStock.currentPrice)}</strong>
                <span>{selectedStock.symbol} current quote · {isDemoPrice ? 'simulated' : 'not plotted'}</span>
              </div>
              <div className="intelligence-chart-frame">
                {historyLoading ? (
                  <div className="intelligence-chart-state" role="status">Loading historical prices…</div>
                ) : historyError ? (
                  <div className="intelligence-chart-state intelligence-chart-error" role="alert">
                    <strong>Historical prices unavailable</strong>
                    <p>{historyError}</p>
                    <button
                      type="button"
                      onClick={() => {
                        setHistoryData(null)
                        setHistoryError('')
                        setHistoryLoading(true)
                        setHistoryRetry((retry) => retry + 1)
                      }}
                    >
                      Retry
                    </button>
                  </div>
                ) : !historyData || historyData.prices.length === 0 ? (
                  <div className="intelligence-chart-state">
                    <strong>No historical prices returned for this range</strong>
                    <p>The provider returned no daily observations in the selected period. No values have been fabricated.</p>
                  </div>
                ) : (
                  <div className="intelligence-chart-container">
                    <div className="intelligence-chart-axis">
                      <span>{formatMoney(priceChart.maximum)}</span>
                      <span>{formatMoney(priceChart.minimum)}</span>
                    </div>
                    <svg
                      className="intelligence-price-chart"
                      viewBox="0 0 800 240"
                      preserveAspectRatio="none"
                      role="img"
                      aria-label={`${selectedStock.symbol} daily closing prices for ${historyData.range}`}
                    >
                      {[0, 1, 2, 3].map((line) => (
                        <line key={line} x1="0" x2="800" y1={line * 80} y2={line * 80} />
                      ))}
                      {priceChart.points.length === 1 ? (
                        <circle cx={priceChart.points[0].x} cy={priceChart.points[0].y} r="4" />
                      ) : (
                        <polyline points={priceChart.polyline} />
                      )}
                    </svg>
                  </div>
                )}
              </div>
              {historyData && historyData.prices.length > 0 && (
                <>
                  <div className="intelligence-chart-dates">
                    <span>{historyData.prices[0].timestamp}</span>
                    <span>{historyData.prices[historyData.prices.length - 1].timestamp}</span>
                  </div>
                  <p className="intelligence-history-meta">
                    {historyData.source} · Data as of {historyData.dataAsOf || 'unknown'}
                    {' · '}Retrieved {formatDate(historyData.retrievedAt)}
                    {historyData.cached ? ' (backend cache)' : ''}
                  </p>
                </>
              )}
              <div className="intelligence-time-ranges" aria-label="Historical price range">
                {['1W', '1M', '3M'].map((range) => (
                  <button
                    type="button"
                    key={range}
                    className={historyRange === range ? 'selected' : ''}
                    aria-pressed={historyRange === range}
                    onClick={() => {
                      if (historyRange === range) return
                      setHistoryData(null)
                      setHistoryError('')
                      setHistoryLoading(true)
                      setHistoryRange(range)
                    }}
                  >
                    {range}
                  </button>
                ))}
                <span>Daily close · unadjusted</span>
              </div>
            </article>

            <article className="intelligence-card">
              <div className="intelligence-card-heading">
                <div>
                  <span className="intelligence-kicker">PROVIDER FUNDAMENTALS</span>
                  <h3>Company &amp; market statistics</h3>
                </div>
                {fundamentals?.source && <span className="intelligence-live-tag">{fundamentals.source}</span>}
              </div>
              {fundamentalsLoading ? (
                <p className="intelligence-fundamentals-state" role="status">Loading company fundamentals…</p>
              ) : fundamentalsError ? (
                <div className="intelligence-fundamentals-state intelligence-fundamentals-error" role="alert">
                  <p>{fundamentalsError}</p>
                  <button
                    type="button"
                    onClick={() => {
                      setFundamentals(null)
                      setFundamentalsError('')
                      setFundamentalsLoading(true)
                      setFundamentalsRetry((retry) => retry + 1)
                    }}
                  >
                    Retry
                  </button>
                </div>
              ) : fundamentals ? (
                <>
                  {!hasFundamentalMetrics && (
                    <p className="intelligence-fundamentals-state" role="status">
                      The provider returned no company metrics for this stock. Unavailable fields are left blank below.
                    </p>
                  )}
                  <dl className="intelligence-stat-list">
                    <div><dt>Company name</dt><dd>{fundamentals.companyName || 'Not available'}</dd></div>
                    <div><dt>Ticker</dt><dd>{fundamentals.symbol}</dd></div>
                    <div><dt>Exchange</dt><dd>{fundamentals.exchange || 'Not available'}</dd></div>
                    <div><dt>Sector</dt><dd>{fundamentals.sector || 'Not available'}</dd></div>
                    <div><dt>Market capitalization</dt><dd>{formatFundamentalCurrency(fundamentals.marketCapitalization, fundamentals.currency)}</dd></div>
                    <div><dt>P/E ratio</dt><dd>{formatFundamentalRatio(fundamentals.peRatio)}</dd></div>
                    <div><dt>Earnings per share</dt><dd>{formatFundamentalCurrency(fundamentals.earningsPerShare, fundamentals.currency)}</dd></div>
                    <div><dt>Price-to-book ratio</dt><dd>{formatFundamentalRatio(fundamentals.priceToBookRatio)}</dd></div>
                    <div><dt>Dividend yield</dt><dd>{formatFundamentalPercent(fundamentals.dividendYield)}</dd></div>
                    <div><dt>Revenue</dt><dd>{formatFundamentalCurrency(fundamentals.revenue, fundamentals.currency)}</dd></div>
                    <div><dt>Net income</dt><dd>{formatFundamentalCurrency(fundamentals.netIncome, fundamentals.currency)}</dd></div>
                    <div><dt>Profit margin</dt><dd>{formatFundamentalPercent(fundamentals.profitMargin)}</dd></div>
                    <div><dt>Reporting period ending</dt><dd>{fundamentals.fiscalPeriodEnding || 'Not available'}</dd></div>
                    <div><dt>Retrieved</dt><dd>{formatDate(fundamentals.retrievedAt)}{fundamentals.cached ? ' · cached' : ''}</dd></div>
                  </dl>
                </>
              ) : (
                <p className="intelligence-fundamentals-state">No company fundamentals are available.</p>
              )}
              <p className="intelligence-data-note">Fundamental values and reporting periods come from Alpha Vantage and may be delayed, incomplete, or unavailable for some symbols.</p>
            </article>

            <article className="intelligence-card">
              <div className="intelligence-card-heading">
                <div>
                  <span className="intelligence-kicker">WHAT-IF MODEL</span>
                  <h3>Investment calculator</h3>
                </div>
              </div>
              <div className="intelligence-calculator">
                <label>
                  Investment amount
                  <span className="intelligence-input-wrap"><i>$</i><input type="number" min="0" step="0.01" value={investmentAmount} onChange={(event) => updateInvestmentAmount(event.target.value)} placeholder="0.00" /></span>
                </label>
                <label>
                  Quantity (shares)
                  <input type="number" min="0" step="any" value={quantity} onChange={(event) => updateQuantity(event.target.value)} placeholder="0" />
                </label>
                <label>
                  Buy price per share
                  <span className="intelligence-input-wrap"><i>$</i><input type="number" min="0" step="0.0001" value={buyPrice} onChange={(event) => updateBuyPrice(event.target.value)} /></span>
                </label>
                <label>
                  Target price per share
                  <span className="intelligence-input-wrap"><i>$</i><input type="number" min="0" step="0.0001" value={targetPrice} onChange={(event) => setTargetPrice(event.target.value)} placeholder="0.00" /></span>
                </label>
              </div>
              <div className="intelligence-calculator-result">
                <div><span>Modeled investment</span><strong>{calculatorReady ? formatMoney(estimatedInvestment) : '—'}</strong></div>
                <div><span>Potential P/L at target</span><strong className={calculatorReady ? potentialProfitLoss >= 0 ? 'positive' : 'negative' : ''}>{calculatorReady ? formatMoney(potentialProfitLoss) : '—'}</strong></div>
                <div><span>Potential return</span><strong className={calculatorReady ? potentialProfitLoss >= 0 ? 'positive' : 'negative' : ''}>{calculatorReady ? `${potentialReturn.toFixed(2)}%` : '—'}</strong></div>
              </div>
              <p className="intelligence-data-note">Hypothetical estimates exclude fees, taxes, slippage, and any guarantee of reaching the target.</p>
            </article>

            <article className="intelligence-card intelligence-risk-card">
              <div className="intelligence-card-heading">
                <div>
                  <span className="intelligence-kicker">RISK CONTEXT</span>
                  <h3>Risk analysis</h3>
                </div>
                <span className="intelligence-risk-badge">LIMITED DATA</span>
              </div>
              <div className="intelligence-risk-reading">
                <span>Reported day change</span>
                <strong className={hasDayChangePercent ? dayChangePercent >= 0 ? 'positive' : 'negative' : ''}>
                  {hasDayChangePercent ? `${dayChangePercent > 0 ? '+' : ''}${dayChangePercent.toFixed(2)}%` : 'Not available'}
                </strong>
              </div>
              <p>
                {hasDayChangePercent
                  ? `The API reports a ${dayChangePercent < 0 ? 'negative' : 'positive'} day change of ${Math.abs(dayChangePercent).toFixed(2)}%. This is a single reported movement, not a measure of volatility or future risk.`
                  : 'The API does not provide a percentage day change, so even a one-day movement cannot be summarized here.'}
              </p>
              <p className="intelligence-data-note">Daily prices are limited to the selected range and provider history. This view does not calculate volatility, drawdown, liquidity, or a risk score. Current quotes marked demo are simulated, not live market data.</p>
            </article>
          </section>
        </>
      )}
    </main>
  )
}

function AdminPortal({ credentials, user, onBackToDashboard }) {
  const [section, setSection] = useState('dashboard')
  const [dashboard, setDashboard] = useState(null)
  const [users, setUsers] = useState([])
  const [stocks, setStocks] = useState([])
  const [selectedUserId, setSelectedUserId] = useState('')
  const [selectedUser, setSelectedUser] = useState(null)
  const [selectedSymbol, setSelectedSymbol] = useState('')
  const [selectedStock, setSelectedStock] = useState(null)
  const [userRole, setUserRole] = useState('')
  const [stockForm, setStockForm] = useState({ symbol: '', companyName: '', sector: '' })
  const [editingSymbol, setEditingSymbol] = useState('')
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let cancelled = false
    Promise.all([
      api.getAdminDashboard(credentials),
      api.getAdminUsers(credentials),
      api.getAdminStocks(credentials),
    ])
      .then(([dashboardData, userData, stockData]) => {
        if (cancelled) return
        setDashboard(dashboardData)
        setUsers(userData)
        setStocks(stockData)
        if (userData.length > 0 || stockData.length > 0) setDetailLoading(true)
        setSelectedUserId((current) => (
          userData.some((item) => String(item.id) === String(current))
            ? current
            : userData[0] ? String(userData[0].id) : ''
        ))
        setSelectedSymbol((current) => (
          stockData.some((item) => item.symbol === current)
            ? current
            : stockData[0]?.symbol || ''
        ))
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [credentials, reload])

  useEffect(() => {
    if (!selectedUserId) return undefined
    let cancelled = false
    api.getAdminUser(credentials, selectedUserId)
      .then((item) => {
        if (cancelled) return
        setSelectedUser(item)
        setUserRole(item.roles?.includes('ADMIN') ? 'ADMIN' : 'USER')
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message)
      })
      .finally(() => {
        if (!cancelled) setDetailLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [credentials, selectedUserId])

  useEffect(() => {
    if (!selectedSymbol) return undefined
    let cancelled = false
    api.getAdminStock(credentials, selectedSymbol)
      .then((item) => {
        if (!cancelled) setSelectedStock(item)
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message)
      })
      .finally(() => {
        if (!cancelled) setDetailLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [credentials, selectedSymbol])

  async function changeRole(event) {
    event.preventDefault()
    if (!selectedUser || selectedUser.username === user.username) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const updated = await api.updateAdminUserRole(credentials, selectedUser.id, userRole)
      setSelectedUser(updated)
      setUsers((current) => current.map((item) => item.id === updated.id ? updated : item))
      setNotice(`Updated ${updated.username}'s role to ${userRole}.`)
      setLoading(true)
      setReload((current) => current + 1)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setBusy(false)
    }
  }

  async function saveStock(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const saved = editingSymbol
        ? await api.updateAdminStock(credentials, editingSymbol, {
          companyName: stockForm.companyName.trim(),
          sector: stockForm.sector.trim(),
        })
        : await api.createAdminStock(credentials, {
          symbol: stockForm.symbol.trim().toUpperCase(),
          companyName: stockForm.companyName.trim(),
          sector: stockForm.sector.trim(),
        })
      setStocks((current) => editingSymbol
        ? current.map((item) => item.symbol === saved.symbol ? saved : item)
        : [saved, ...current])
      setSelectedStock(saved)
      setSelectedSymbol(saved.symbol)
      setStockForm({ symbol: '', companyName: '', sector: '' })
      setEditingSymbol('')
      setNotice(`${saved.symbol} ${editingSymbol ? 'metadata updated' : 'added'} successfully.`)
      setLoading(true)
      setReload((current) => current + 1)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setBusy(false)
    }
  }

  function beginStockEdit() {
    if (!selectedStock) return
    setEditingSymbol(selectedStock.symbol)
    setStockForm({
      symbol: selectedStock.symbol,
      companyName: selectedStock.companyName,
      sector: selectedStock.sector,
    })
    setError('')
    setNotice('')
  }

  async function deleteStock() {
    if (!selectedStock || !window.confirm(
      `Delete ${selectedStock.symbol}? Stocks referenced by holdings, trades, or watchlists cannot be deleted.`,
    )) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await api.deleteAdminStock(credentials, selectedStock.symbol)
      const remaining = stocks.filter((item) => item.symbol !== selectedStock.symbol)
      setStocks(remaining)
      setSelectedSymbol(remaining[0]?.symbol || '')
      if (!remaining.length) setSelectedStock(null)
      setNotice(`${selectedStock.symbol} deleted.`)
      setLoading(true)
      setEditingSymbol('')
      setStockForm({ symbol: '', companyName: '', sector: '' })
      setReload((current) => current + 1)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="main-panel admin-main">
      <header className="topbar">
        <div>
          <p className="eyebrow">SmartStock administration</p>
          <h1>Admin portal</h1>
          <p className="panel-caption">Signed in as {user.username} · administrative actions are enforced by the backend.</p>
        </div>
        <button className="ghost-btn" type="button" onClick={onBackToDashboard}>Investor dashboard</button>
      </header>

      <nav className="admin-section-nav" aria-label="Admin portal sections">
        {[
          ['dashboard', 'Overview'],
          ['users', 'User management'],
          ['stocks', 'Stock management'],
        ].map(([value, label]) => (
          <button
            className={section === value ? 'admin-section-tab active' : 'admin-section-tab'}
            type="button"
            key={value}
            aria-current={section === value ? 'page' : undefined}
            onClick={() => setSection(value)}
          >
            {label}
          </button>
        ))}
      </nav>

      {notice && (
        <div className="alert success-alert" role="status">
          <span>{notice}</span>
          <button type="button" onClick={() => setNotice('')}>Dismiss</button>
        </div>
      )}
      {error && (
        <div className="alert error-alert" role="alert">
          <span>{error}</span>
          <button type="button" onClick={() => {
            setError('')
            setLoading(true)
            setReload((current) => current + 1)
          }}>Retry</button>
        </div>
      )}

      {loading ? (
        <section className="loading-state" role="status">Loading admin data…</section>
      ) : error && !dashboard ? (
        <section className="panel empty-state">
          <h2>Admin data unavailable</h2>
          <p>Check your administrator access and the backend connection, then retry.</p>
          <button className="primary-btn" type="button" onClick={() => setReload((current) => current + 1)}>Retry</button>
        </section>
      ) : section === 'dashboard' ? (
        dashboard ? (
          <>
            <div className="admin-stats-grid">
              <article className="admin-stat-card admin-stat-primary">
                <span>Registered users</span>
                <strong>{dashboard.totalRegisteredUsers}</strong>
                <small>{dashboard.usersWithUserRole} USER · {dashboard.usersWithAdminRole} ADMIN</small>
              </article>
              <article className="admin-stat-card"><span>Stocks</span><strong>{dashboard.totalStocks}</strong></article>
              <article className="admin-stat-card"><span>Portfolios</span><strong>{dashboard.totalPortfolios}</strong></article>
              <article className="admin-stat-card"><span>Holdings</span><strong>{dashboard.totalHoldings}</strong></article>
              <article className="admin-stat-card"><span>Watchlist entries</span><strong>{dashboard.totalWatchlistEntries}</strong></article>
              <article className="admin-stat-card"><span>Virtual transactions</span><strong>{dashboard.totalVirtualTransactions}</strong><small>{dashboard.buyTransactions} BUY · {dashboard.sellTransactions} SELL</small></article>
            </div>
            <section className="panel admin-price-availability">
              <div>
                <h2>Stock price availability</h2>
                <p className="panel-caption">Availability reflects stored source and price validity; it does not mean live data.</p>
              </div>
              <div className="admin-availability-values">
                <strong>{dashboard.stocksWithAvailablePriceSource}<span>Available</span></strong>
                <strong>{dashboard.stocksWithUnavailablePriceSource}<span>Unavailable</span></strong>
              </div>
            </section>
          </>
        ) : <p className="empty-inline">Admin summary is not available.</p>
      ) : section === 'users' ? (
        <section className="admin-management-grid">
          <article className="panel">
            <div className="panel-header"><div><h2>Users</h2><p className="panel-caption">{users.length} registered account{users.length === 1 ? '' : 's'}</p></div></div>
            {users.length === 0 ? <p className="empty-inline">No users are registered.</p> : (
              <div className="admin-record-list">
                {users.map((item) => (
                  <button
                    type="button"
                    key={item.id}
                    className={String(item.id) === selectedUserId ? 'admin-record selected' : 'admin-record'}
                    onClick={() => {
                      setError('')
                      setDetailLoading(true)
                      setSelectedUserId(String(item.id))
                    }}
                  >
                    <span><strong>{item.username}</strong><small>{item.email}</small></span>
                    <small>{(item.roles || []).join(', ') || 'No role'}</small>
                  </button>
                ))}
              </div>
            )}
          </article>
          <article className="panel">
            <div className="panel-header"><div><h2>User details</h2><p className="panel-caption">Credentials and password hashes are never returned.</p></div></div>
            {detailLoading ? <p className="empty-inline" role="status">Loading user…</p> : selectedUser ? (
              <>
                <dl className="admin-detail-list">
                  <div><dt>Username</dt><dd>{selectedUser.username}</dd></div>
                  <div><dt>Email</dt><dd>{selectedUser.email}</dd></div>
                  <div><dt>Current role(s)</dt><dd>{(selectedUser.roles || []).join(', ') || 'None'}</dd></div>
                </dl>
                {selectedUser.username === user.username ? (
                  <p className="admin-inline-note">Your own role cannot be changed here.</p>
                ) : (
                  <form className="admin-inline-form" onSubmit={changeRole}>
                    <label>Set role
                      <select value={userRole} onChange={(event) => setUserRole(event.target.value)}>
                        <option value="USER">USER</option>
                        <option value="ADMIN">ADMIN</option>
                      </select>
                    </label>
                    <button className="primary-btn" type="submit" disabled={busy || !userRole}>Save role</button>
                  </form>
                )}
              </>
            ) : <p className="empty-inline">Select a user to view details.</p>}
          </article>
        </section>
      ) : (
        <section className="admin-management-grid">
          <article className="panel">
            <div className="panel-header"><div><h2>Stocks</h2><p className="panel-caption">{stocks.length} supported stock{stocks.length === 1 ? '' : 's'}</p></div></div>
            {stocks.length === 0 ? <p className="empty-inline">No stocks are configured.</p> : (
              <div className="admin-record-list">
                {stocks.map((item) => (
                  <button
                    type="button"
                    key={item.symbol}
                    className={item.symbol === selectedSymbol ? 'admin-record selected' : 'admin-record'}
                    onClick={() => {
                      setError('')
                      setDetailLoading(true)
                      setSelectedSymbol(item.symbol)
                      setEditingSymbol('')
                      setStockForm({ symbol: '', companyName: '', sector: '' })
                    }}
                  >
                    <span><strong>{item.symbol}</strong><small>{item.companyName}</small></span>
                    <small>{item.sector}</small>
                  </button>
                ))}
              </div>
            )}
            {selectedStock && (
              <div className="admin-stock-detail">
                <strong>{selectedStock.symbol} · {selectedStock.companyName}</strong>
                <span>{selectedStock.sector}</span>
                <small>Price: {formatMoney(selectedStock.currentPrice)} · {selectedStock.marketSource || 'Source unavailable'}</small>
                <p className="panel-caption">Market price and source are read-only and remain provider-managed.</p>
                <div className="admin-stock-actions">
                  <button className="ghost-btn" type="button" onClick={beginStockEdit}>Edit metadata</button>
                  <button className="danger-btn" type="button" disabled={busy} onClick={deleteStock}>Delete stock</button>
                </div>
              </div>
            )}
          </article>
          <article className="panel">
            <div className="panel-header">
              <div><h2>{editingSymbol ? `Edit ${editingSymbol}` : 'Add a stock'}</h2><p className="panel-caption">Stock prices cannot be entered or changed here.</p></div>
            </div>
            <form className="admin-stock-form" onSubmit={saveStock}>
              {!editingSymbol && (
                <label>Symbol
                  <input
                    required
                    maxLength={10}
                    pattern="[A-Za-z0-9.^-]{1,10}"
                    title="Use 1–10 letters, digits, periods, or hyphens."
                    value={stockForm.symbol}
                    onChange={(event) => setStockForm((current) => ({ ...current, symbol: event.target.value }))}
                  />
                </label>
              )}
              <label>Company name
                <input required maxLength={255} value={stockForm.companyName}
                  onChange={(event) => setStockForm((current) => ({ ...current, companyName: event.target.value }))} />
              </label>
              <label>Sector
                <input required maxLength={255} value={stockForm.sector}
                  onChange={(event) => setStockForm((current) => ({ ...current, sector: event.target.value }))} />
              </label>
              <div className="modal-actions">
                {editingSymbol && <button className="ghost-btn" type="button" onClick={() => {
                  setEditingSymbol('')
                  setStockForm({ symbol: '', companyName: '', sector: '' })
                }}>Cancel</button>}
                <button className="primary-btn" type="submit" disabled={busy}>
                  {busy ? 'Saving…' : editingSymbol ? 'Save metadata' : 'Add stock'}
                </button>
              </div>
            </form>
            {detailLoading && selectedSymbol && <p className="panel-caption" role="status">Loading stock details…</p>}
          </article>
        </section>
      )}
    </main>
  )
}

function App() {
  const [credentials, setCredentials] = useState(null)
  const [user, setUser] = useState(null)
  const [authBusy, setAuthBusy] = useState(false)
  const [authError, setAuthError] = useState('')
  const [loading, setLoading] = useState(false)
  const [dataError, setDataError] = useState('')
  const [stocks, setStocks] = useState([])
  const [watchlist, setWatchlist] = useState([])
  const [watchlistLoading, setWatchlistLoading] = useState(false)
  const [watchlistLoadError, setWatchlistLoadError] = useState('')
  const [watchlistRefreshNumber, setWatchlistRefreshNumber] = useState(0)
  const [watchlistError, setWatchlistError] = useState('')
  const [watchlistSymbol, setWatchlistSymbol] = useState('')
  const [watchlistBusy, setWatchlistBusy] = useState(false)
  const [removingWatchlistSymbol, setRemovingWatchlistSymbol] = useState('')
  const [portfolios, setPortfolios] = useState([])
  const [selectedPortfolioId, setSelectedPortfolioId] = useState('')
  const [activePage, setActivePage] = useState('dashboard')
  const [intelligenceSymbol, setIntelligenceSymbol] = useState('')
  const [search, setSearch] = useState('')
  const [stockSort, setStockSort] = useState('symbol')
  const [createOpen, setCreateOpen] = useState(false)
  const [portfolioName, setPortfolioName] = useState('')
  const [editingPortfolioName, setEditingPortfolioName] = useState(false)
  const [portfolioNameDraft, setPortfolioNameDraft] = useState('')
  const [portfolioNameError, setPortfolioNameError] = useState('')
  const [renameBusy, setRenameBusy] = useState(false)
  const [tradeStock, setTradeStock] = useState(null)
  const [tradeType, setTradeType] = useState('buy')
  const [quantity, setQuantity] = useState('1')
  const [actionBusy, setActionBusy] = useState(false)
  const [actionError, setActionError] = useState('')
  const [notice, setNotice] = useState('')
  const [refreshNumber, setRefreshNumber] = useState(0)

  useEffect(() => {
    if (!credentials) return undefined

    let cancelled = false

    Promise.all([
      api.getStocks(credentials),
      api.getPortfolios(credentials),
    ])
      .then(([stockData, portfolioData]) => {
        if (cancelled) return
        setStocks(stockData)
        setPortfolios(portfolioData)
        setSelectedPortfolioId((currentId) => (
          portfolioData.some((portfolio) => String(portfolio.id) === String(currentId))
            ? currentId
            : portfolioData[0] ? String(portfolioData[0].id) : ''
        ))
      })
      .catch((requestError) => {
        if (!cancelled) setDataError(requestError.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [credentials, refreshNumber])

  useEffect(() => {
    if (!credentials) return undefined

    let cancelled = false
    api.getWatchlist(credentials)
      .then((items) => {
        if (!cancelled) setWatchlist(items)
      })
      .catch((requestError) => {
        if (!cancelled) setWatchlistLoadError(requestError.message)
      })
      .finally(() => {
        if (!cancelled) setWatchlistLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [credentials, watchlistRefreshNumber])

  const selectedPortfolio = portfolios.find(
    (portfolio) => String(portfolio.id) === String(selectedPortfolioId),
  )

  const allocationColors = ['#087b5b', '#4a9b78', '#86b99f', '#d0a34a', '#7190b5', '#a982a8']
  const allocationHoldings = (selectedPortfolio?.holdings || [])
    .map((holding, index) => ({
      ...holding,
      allocationValue: Number(holding.marketValue ?? 0),
      allocationColor: allocationColors[index % allocationColors.length],
    }))
    .filter((holding) => holding.allocationValue > 0)
  const holdingsValue = allocationHoldings.reduce((sum, holding) => sum + holding.allocationValue, 0)
  const allocationSlices = getAllocationSlices(allocationHoldings, holdingsValue)
  const largestHolding = allocationHoldings.reduce(
    (largest, holding) => holding.allocationValue > (largest?.allocationValue ?? 0) ? holding : largest,
    null,
  )
  const largestHoldingValue = Math.max(...allocationHoldings.map((holding) => holding.allocationValue), 0)
  const recentTransactions = (selectedPortfolio?.transactions || []).slice(0, 5)
  const allocationGradient = allocationSlices.length > 0
    ? `conic-gradient(${allocationSlices.map((slice) => (
      `${slice.allocationColor} ${slice.start}% ${slice.end}%`
    )).join(', ')})`
    : 'conic-gradient(var(--line) 0% 100%)'

  const profitLossMetrics = [
    { label: 'Realized P/L', value: Number(selectedPortfolio?.realizedProfitLoss ?? 0) },
    { label: 'Unrealized P/L', value: Number(selectedPortfolio?.unrealizedProfitLoss ?? 0) },
    { label: 'Total P/L', value: Number(selectedPortfolio?.totalProfitLoss ?? 0) },
  ]
  const largestProfitLoss = Math.max(...profitLossMetrics.map((metric) => Math.abs(metric.value)), 1)

  const visibleStocks = useMemo(() => {
    const query = search.trim().toLowerCase()
    const matchingStocks = stocks.filter((stock) => (
      !query
      || stock.symbol.toLowerCase().includes(query)
      || stock.companyName.toLowerCase().includes(query)
      || (stock.sector || '').toLowerCase().includes(query)
    ))

    return matchingStocks.sort((left, right) => {
      if (stockSort === 'price-ascending' || stockSort === 'price-descending') {
        const priceDifference = Number(left.currentPrice) - Number(right.currentPrice)
        return stockSort === 'price-ascending'
          ? priceDifference || left.symbol.localeCompare(right.symbol)
          : -priceDifference || left.symbol.localeCompare(right.symbol)
      }
      return left.symbol.localeCompare(right.symbol)
    })
  }, [search, stockSort, stocks])

  async function handleAuthenticate({ mode, username, email, password }) {
    setAuthBusy(true)
    setAuthError('')
    try {
      if (mode === 'register') {
        await api.register({ username, email, password })
      }
      const account = await api.login({ username, password })
      setUser(account)
      setActivePage('dashboard')
      setWatchlistLoading(true)
      setWatchlistLoadError('')
      setCredentials({ username, password })
      setLoading(true)
      setDataError('')
    } catch (requestError) {
      setAuthError(requestError.message)
    } finally {
      setAuthBusy(false)
    }
  }

  function signOut() {
    setCredentials(null)
    setUser(null)
    setActivePage('dashboard')
    setStocks([])
    setWatchlist([])
    setWatchlistLoading(false)
    setWatchlistLoadError('')
    setWatchlistError('')
    setWatchlistSymbol('')
    setPortfolios([])
    setSelectedPortfolioId('')
    setActivePage('dashboard')
    setDataError('')
    setLoading(false)
    setNotice('')
  }

  function openTrade(stock, type) {
    setTradeStock(stock)
    setTradeType(type)
    setQuantity('1')
    setActionError('')
  }

  async function handleCreatePortfolio(event) {
    event.preventDefault()
    if (!credentials) return
    setActionBusy(true)
    setActionError('')
    try {
      const portfolio = await api.createPortfolio(credentials, portfolioName.trim())
      setPortfolios((current) => [...current, portfolio])
      setSelectedPortfolioId(String(portfolio.id))
      setPortfolioName('')
      setCreateOpen(false)
      setNotice(`Portfolio “${portfolio.name}” created.`)
    } catch (requestError) {
      setActionError(requestError.message)
    } finally {
      setActionBusy(false)
    }
  }

  async function handleRenamePortfolio(event) {
    event.preventDefault()
    if (!credentials || !selectedPortfolio) return

    setRenameBusy(true)
    setPortfolioNameError('')
    try {
      const updatedPortfolio = await api.renamePortfolio(
        credentials,
        selectedPortfolio.id,
        portfolioNameDraft.trim(),
      )
      setPortfolios((current) => current.map((portfolio) => (
        portfolio.id === updatedPortfolio.id ? updatedPortfolio : portfolio
      )))
      setEditingPortfolioName(false)
      setNotice(`Portfolio renamed to “${updatedPortfolio.name}”.`)
    } catch (requestError) {
      setPortfolioNameError(requestError.message)
    } finally {
      setRenameBusy(false)
    }
  }

  async function handleTrade(event) {
    event.preventDefault()
    if (!credentials || !selectedPortfolio || !tradeStock) return
    setActionBusy(true)
    setActionError('')
    try {
      const updatedPortfolio = await api.trade(
        credentials,
        selectedPortfolio.id,
        tradeType,
        {
          symbol: tradeStock.symbol,
          quantity,
          price: String(tradeStock.currentPrice),
        },
      )
      setPortfolios((current) => current.map((portfolio) => (
        portfolio.id === updatedPortfolio.id ? updatedPortfolio : portfolio
      )))
      setTradeStock(null)
      setNotice(`${tradeType === 'buy' ? 'Buy' : 'Sell'} order completed for ${tradeStock.symbol}.`)
    } catch (requestError) {
      setActionError(requestError.message)
    } finally {
      setActionBusy(false)
    }
  }

  async function handleAddWatchlistStock(event) {
    event.preventDefault()
    const symbol = watchlistSymbol.trim()
    if (!credentials || !symbol) return

    setWatchlistBusy(true)
    setWatchlistError('')
    try {
      const item = await api.addWatchlistStock(credentials, symbol)
      setWatchlist((current) => [item, ...current])
      setWatchlistSymbol('')
      setNotice(`${item.symbol} added to your watchlist.`)
    } catch (requestError) {
      setWatchlistError(requestError.message)
    } finally {
      setWatchlistBusy(false)
    }
  }

  async function handleRemoveWatchlistStock(symbol) {
    if (!credentials) return

    setRemovingWatchlistSymbol(symbol)
    setWatchlistError('')
    try {
      await api.removeWatchlistStock(credentials, symbol)
      setWatchlist((current) => current.filter((item) => item.symbol !== symbol))
      setNotice(`${symbol} removed from your watchlist.`)
    } catch (requestError) {
      setWatchlistError(requestError.message)
    } finally {
      setRemovingWatchlistSymbol('')
    }
  }

  if (!credentials) {
    return <AuthPanel onAuthenticate={handleAuthenticate} error={authError} busy={authBusy} />
  }

  const isAdmin = user?.roles?.includes('ADMIN') === true

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">S</div>
          <h2>SmartStock</h2>
        </div>

        <nav className="nav" aria-label="Main navigation">
          {activePage === 'dashboard' ? (
            <>
              <a
                className="nav-item active"
                href="#dashboard"
                aria-current="page"
              >
                Dashboard
              </a>
              <button className="nav-item intelligence-nav-item" type="button" onClick={() => setActivePage('intelligence')}>Stock Intelligence</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('heatmap')}>Market Heatmap</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('journal')}>Investment Journal</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('simulator')}>Investment Simulator</button>
              {isAdmin && <button className="nav-item admin-nav-item" type="button" onClick={() => setActivePage('admin')}>Admin portal</button>}
            </>
          ) : activePage === 'admin' ? (
            <>
              <button className="nav-item" type="button" onClick={() => setActivePage('dashboard')}>Investor dashboard</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('journal')}>Investment Journal</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('heatmap')}>Market Heatmap</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('simulator')}>Investment Simulator</button>
              <button className="nav-item active admin-nav-item" type="button" aria-current="page">Admin portal</button>
            </>
          ) : activePage === 'journal' ? (
            <>
              <button className="nav-item" type="button" onClick={() => setActivePage('dashboard')}>Dashboard</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('heatmap')}>Market Heatmap</button>
              <button className="nav-item active" type="button" aria-current="page">Investment Journal</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('intelligence')}>Stock Intelligence</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('simulator')}>Investment Simulator</button>
              {isAdmin && <button className="nav-item admin-nav-item" type="button" onClick={() => setActivePage('admin')}>Admin portal</button>}
            </>
          ) : activePage === 'heatmap' ? (
            <>
              <button className="nav-item" type="button" onClick={() => setActivePage('dashboard')}>Dashboard</button>
              <button className="nav-item active" type="button" aria-current="page">Market Heatmap</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('intelligence')}>Stock Intelligence</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('journal')}>Investment Journal</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('simulator')}>Investment Simulator</button>
              {isAdmin && <button className="nav-item admin-nav-item" type="button" onClick={() => setActivePage('admin')}>Admin portal</button>}
            </>
          ) : activePage === 'simulator' ? (
            <>
              <button className="nav-item" type="button" onClick={() => setActivePage('dashboard')}>Dashboard</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('intelligence')}>Stock Intelligence</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('heatmap')}>Market Heatmap</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('journal')}>Investment Journal</button>
              <button className="nav-item active" type="button" aria-current="page">Investment Simulator</button>
              {isAdmin && <button className="nav-item admin-nav-item" type="button" onClick={() => setActivePage('admin')}>Admin portal</button>}
            </>
          ) : (
            <>
              <button className="nav-item" type="button" onClick={() => setActivePage('dashboard')}>Dashboard</button>
              <button className="nav-item active" type="button" aria-current="page" onClick={() => setActivePage('intelligence')}>Stock Intelligence</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('heatmap')}>Market Heatmap</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('journal')}>Investment Journal</button>
              <button className="nav-item" type="button" onClick={() => setActivePage('simulator')}>Investment Simulator</button>
              {isAdmin && <button className="nav-item admin-nav-item" type="button" onClick={() => setActivePage('admin')}>Admin portal</button>}
            </>
          )}
        </nav>

        <div className="sidebar-card">
          <p>Signed in as</p>
          <strong className="username">{user?.username || credentials.username}</strong>
          <span>Virtual trading account</span>
        </div>
        <button className="nav-item signout-btn" type="button" onClick={signOut}>Sign out</button>
      </aside>

      {activePage === 'simulator' ? (
        <InvestmentSimulator credentials={credentials} />
      ) : activePage === 'journal' ? (
        <InvestmentJournal
          credentials={credentials}
          onBackToDashboard={() => setActivePage('dashboard')}
        />
      ) : activePage === 'heatmap' ? (
        <MarketHeatmap
          stocks={stocks}
          loading={loading}
          error={dataError}
          onRetry={() => {
            setLoading(true)
            setDataError('')
            setRefreshNumber((current) => current + 1)
          }}
          onSelectStock={(symbol) => {
            setIntelligenceSymbol(symbol)
            setActivePage('intelligence')
          }}
          onBackToDashboard={() => setActivePage('dashboard')}
        />
      ) : activePage === 'admin' && isAdmin ? (
        <AdminPortal
          credentials={credentials}
          user={user}
          onBackToDashboard={() => setActivePage('dashboard')}
        />
      ) : activePage === 'dashboard' || activePage === 'admin' ? (
      <main className="main-panel">
        <header className="topbar">
          <div>
            <p className="eyebrow">Investor portal</p>
            <h1>Portfolio overview</h1>
          </div>
          <div className="topbar-actions">
            {portfolios.length > 0 && (
              <label className="portfolio-picker">
                <span>Portfolio</span>
                <select
                  value={selectedPortfolioId}
                  onChange={(event) => {
                    setSelectedPortfolioId(event.target.value)
                    setEditingPortfolioName(false)
                    setPortfolioNameError('')
                  }}
                  aria-label="Select portfolio"
                >
                  {portfolios.map((portfolio) => (
                    <option key={portfolio.id} value={portfolio.id}>{portfolio.name}</option>
                  ))}
                </select>
              </label>
            )}
            {selectedPortfolio && !editingPortfolioName && (
              <button className="ghost-btn" type="button" onClick={() => {
                setPortfolioNameDraft(selectedPortfolio.name)
                setPortfolioNameError('')
                setEditingPortfolioName(true)
              }}>
                Rename portfolio
              </button>
            )}
            {selectedPortfolio && editingPortfolioName && (
              <form className="portfolio-rename-form" onSubmit={handleRenamePortfolio}>
                <label className="visually-hidden" htmlFor="portfolio-name-edit">Portfolio name</label>
                <input
                  id="portfolio-name-edit"
                  autoFocus
                  required
                  maxLength={80}
                  value={portfolioNameDraft}
                  onChange={(event) => setPortfolioNameDraft(event.target.value)}
                />
                <button className="primary-btn" type="submit" disabled={renameBusy || !portfolioNameDraft.trim()}>
                  {renameBusy ? 'Saving…' : 'Save name'}
                </button>
                <button className="ghost-btn" type="button" disabled={renameBusy} onClick={() => {
                  setEditingPortfolioName(false)
                  setPortfolioNameError('')
                }}>
                  Cancel
                </button>
              </form>
            )}
            <button className="primary-btn" type="button" onClick={() => {
              setActionError('')
              setCreateOpen(true)
            }}>
              + Create portfolio
            </button>
          </div>
        </header>

        {portfolioNameError && <div className="alert error-alert" role="alert">{portfolioNameError}</div>}

        <div className="data-source-note" role="note">
          Market prices are supplied by the backend and may be simulated demo data. Demo prices are not live market data.
        </div>

        {notice && (
          <div className="alert success-alert" role="status">
            <span>{notice}</span>
            <button type="button" aria-label="Dismiss notification" onClick={() => setNotice('')}>×</button>
          </div>
        )}
        {dataError && (
          <div className="alert error-alert" role="alert">
            <span>{dataError}</span>
            <button type="button" onClick={() => {
              setLoading(true)
              setDataError('')
              setRefreshNumber((current) => current + 1)
            }}>Retry</button>
          </div>
        )}

        {loading ? (
          <section className="loading-state" aria-live="polite">Loading stocks and portfolio data…</section>
        ) : (
          <>
            <section id="dashboard" className="summary-grid" aria-label="Portfolio summary">
              <article className="stat-card primary">
                <label>Total portfolio value</label>
                <strong>{selectedPortfolio ? formatMoney(selectedPortfolio.totalValue) : '—'}</strong>
                <span>Cash plus current holding values</span>
              </article>
              <article className="stat-card">
                <label>Cash balance</label>
                <strong>{selectedPortfolio ? formatMoney(selectedPortfolio.cashBalance) : '—'}</strong>
                <span>Virtual cash available to trade</span>
              </article>
              <article className="stat-card">
                <label>Holdings value</label>
                <strong>{selectedPortfolio ? formatMoney(holdingsValue) : '—'}</strong>
                <span>Current market value of invested stocks</span>
              </article>
            </section>

            {!selectedPortfolio && (
              <section className="empty-state panel">
                <h2>No portfolio yet</h2>
                <p>Create a virtual portfolio to start tracking your investments.</p>
                <button className="primary-btn" type="button" onClick={() => setCreateOpen(true)}>
                  Create your first portfolio
                </button>
              </section>
            )}

            <section id="analytics" className="analytics-grid" aria-label="Portfolio analytics">
              {selectedPortfolio ? (
                <>
                <article className="panel analytics-panel">
                  <div className="panel-header">
                    <div>
                      <h3>Portfolio allocation</h3>
                      <p className="panel-caption">Current holdings value by stock</p>
                    </div>
                  </div>
                  {allocationSlices.length === 0 ? (
                    <p className="empty-inline">No holdings to show in the allocation chart yet.</p>
                  ) : (
                    <div className="allocation-content">
                      <div
                        className="allocation-chart"
                        role="img"
                        aria-label={`Portfolio allocation by stock. Total holdings value ${formatMoney(holdingsValue)}.`}
                        style={{ background: allocationGradient }}
                      >
                        <div className="allocation-chart-center">
                          <span>Holdings</span>
                          <strong>{formatMoney(holdingsValue)}</strong>
                        </div>
                      </div>
                      <ul className="allocation-legend">
                        {allocationSlices.map((slice) => (
                          <li key={slice.id}>
                            <span className="allocation-legend-symbol">
                              <i style={{ backgroundColor: slice.allocationColor }} aria-hidden="true" />
                              <strong>{slice.symbol}</strong>
                            </span>
                            <span>{formatMoney(slice.allocationValue)}</span>
                            <span>{((slice.allocationValue / holdingsValue) * 100).toFixed(1)}%</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </article>

                <article className="panel analytics-panel">
                  <div className="panel-header">
                    <div>
                      <h3>Holdings comparison</h3>
                      <p className="panel-caption">Current market value by stock</p>
                    </div>
                  </div>
                  {allocationHoldings.length === 0 ? (
                    <p className="empty-inline">No holdings to compare yet.</p>
                  ) : (
                    <div className="holdings-chart">
                      {allocationHoldings.map((holding) => (
                        <div className="holdings-chart-row" key={holding.id}>
                          <div className="holdings-chart-label">
                            <strong>{holding.symbol}</strong>
                            <span>{formatMoney(holding.marketValue)}</span>
                          </div>
                          <div
                            className="holdings-chart-track"
                            role="img"
                            aria-label={`${holding.symbol}: ${formatMoney(holding.marketValue)}`}
                          >
                            <span
                              style={{
                                width: `${(holding.allocationValue / largestHoldingValue) * 100}%`,
                                backgroundColor: holding.allocationColor,
                              }}
                            />
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </article>

                <article className="panel analytics-panel">
                  <div className="panel-header">
                    <div>
                      <h3>Profit / loss overview</h3>
                      <p className="panel-caption">Backend-calculated portfolio performance</p>
                    </div>
                  </div>
                  {selectedPortfolio.transactions.length === 0 && allocationSlices.length === 0 ? (
                    <p className="empty-inline">Make a virtual trade to see portfolio profit and loss.</p>
                  ) : (
                    <div className="profit-loss-chart">
                      {profitLossMetrics.map((metric) => (
                        <div className="profit-loss-row" key={metric.label}>
                          <div className="profit-loss-label">
                            <span>{metric.label}</span>
                            <strong className={metric.value >= 0 ? 'positive' : 'negative'}>
                              {formatMoney(metric.value)}
                            </strong>
                          </div>
                          <div className="profit-loss-track" aria-hidden="true">
                            <span
                              className={metric.value >= 0 ? 'positive-bar' : 'negative-bar'}
                              style={{ width: `${(Math.abs(metric.value) / largestProfitLoss) * 100}%` }}
                            />
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </article>
                </>
              ) : (
                <article className="panel analytics-panel analytics-empty-panel">
                  <div className="panel-header">
                    <div>
                      <h3>Portfolio analytics</h3>
                      <p className="panel-caption">Allocation and profit/loss insights</p>
                    </div>
                  </div>
                  <p className="empty-inline">Create a portfolio to see your allocation, holdings comparison, and profit/loss analytics.</p>
                </article>
              )}
            </section>

            <section id="discover-stocks" className="content-grid">
              <div className="panel market-panel">
                <div className="panel-header">
                  <div>
                    <h3>Supported stocks</h3>
                    <p className="panel-caption">Search the stocks available to this demo</p>
                  </div>
                  <div className="stock-discovery-controls">
                    <label className="search-control">
                      <span className="visually-hidden">Search stocks by symbol, company, or sector</span>
                      <input
                        type="search"
                        value={search}
                        onChange={(event) => setSearch(event.target.value)}
                        placeholder="Search symbols or companies"
                      />
                    </label>
                    <label className="stock-sort-control">
                      <span className="visually-hidden">Sort stocks</span>
                      <select value={stockSort} onChange={(event) => setStockSort(event.target.value)}>
                        <option value="symbol">Symbol A–Z</option>
                        <option value="price-ascending">Price: low to high</option>
                        <option value="price-descending">Price: high to low</option>
                      </select>
                    </label>
                  </div>
                </div>
                {visibleStocks.length === 0 ? (
                  <p className="empty-inline">
                    {stocks.length === 0 ? 'No stocks are currently available.' : 'No stocks match your search.'}
                  </p>
                ) : (
                  <div className="stock-list">
                    {visibleStocks.map((stock) => {
                      const isDemo = (stock.marketSource || '').toLowerCase() === 'demo'
                      return (
                        <div key={stock.symbol} className="stock-row">
                          <div className="stock-identity">
                            <div className="ticker">{stock.symbol}</div>
                            <small>{stock.companyName}</small>
                          </div>
                          <div className="stock-quote">
                            <strong>{formatMoney(stock.currentPrice)}</strong>
                            <span className="source-tag">
                              {isDemo ? 'Demo price' : `Source: ${stock.marketSource || 'unspecified'}`}
                            </span>
                          </div>
                          <div className={`trend ${Number(stock.dayChange) >= 0 ? 'positive' : 'negative'}`}>
                            {stock.dayChange == null ? '—' : formatMoney(stock.dayChange)}
                          </div>
                          <button
                            className="ghost-btn trade-open-btn"
                            type="button"
                            disabled={!selectedPortfolio}
                            onClick={() => {
                              setTradeStock(stock)
                              setTradeType('buy')
                              setQuantity('1')
                              setActionError('')
                            }}
                          >
                            Trade
                          </button>
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>

              <div className="panel insight-panel">
                <div className="panel-header">
                  <div>
                    <h3>Portfolio insight</h3>
                    <p className="panel-caption">Based on current holdings values</p>
                  </div>
                </div>
                {largestHolding ? (
                  <>
                    <p className="insight-symbol">{largestHolding.symbol}</p>
                    <p className="insight-description">
                      Your largest holding by current market value is {largestHolding.companyName}.
                    </p>
                    <strong className="insight-value">{formatMoney(largestHolding.marketValue)}</strong>
                  </>
                ) : (
                  <p className="empty-inline">Add a virtual holding to see portfolio insights.</p>
                )}
                <p className="panel-caption insight-footnote">
                  Market prices may be simulated demo data and are not live quotes.
                </p>
              </div>
            </section>

            <section id="watchlist" className="panel watchlist-panel" aria-labelledby="watchlist-title">
              <div className="panel-header">
                <div>
                  <h3 id="watchlist-title">My Watchlist</h3>
                  <p className="panel-caption">Track supported stocks using backend-provided quotes</p>
                </div>
              </div>

              <form className="watchlist-add-form" onSubmit={handleAddWatchlistStock}>
                <label className="search-control">
                  <span className="visually-hidden">Stock symbol to add to watchlist</span>
                  <input
                    type="search"
                    list="supported-stock-symbols"
                    value={watchlistSymbol}
                    onChange={(event) => setWatchlistSymbol(event.target.value)}
                    placeholder="Search or enter a stock symbol"
                    maxLength={10}
                    required
                  />
                </label>
                <datalist id="supported-stock-symbols">
                  {stocks.map((stock) => (
                    <option key={stock.symbol} value={stock.symbol}>{stock.companyName}</option>
                  ))}
                </datalist>
                <button className="primary-btn" type="submit" disabled={watchlistBusy || !watchlistSymbol.trim()}>
                  {watchlistBusy ? 'Adding…' : 'Add stock'}
                </button>
              </form>

              {watchlistError && (
                <div className="alert error-alert watchlist-alert" role="alert">
                  <span>{watchlistError}</span>
                  <button type="button" onClick={() => setWatchlistError('')}>Dismiss</button>
                </div>
              )}

              {watchlistLoading ? (
                <p className="empty-inline" role="status">Loading your watchlist…</p>
              ) : watchlistLoadError ? (
                <div className="alert error-alert watchlist-alert" role="alert">
                  <span>{watchlistLoadError}</span>
                  <button type="button" onClick={() => {
                    setWatchlistLoading(true)
                    setWatchlistLoadError('')
                    setWatchlistRefreshNumber((current) => current + 1)
                  }}>
                    Retry
                  </button>
                </div>
              ) : watchlist.length === 0 ? (
                <p className="empty-inline">Your watchlist is empty. Add a supported stock symbol to start tracking it.</p>
              ) : (
                <div className="watchlist-items">
                  {watchlist.map((item) => {
                    const isDemo = (item.marketSource || '').toLowerCase() === 'demo'
                    return (
                      <article className="watchlist-item" key={item.id}>
                        <div className="watchlist-stock-identity">
                          <strong>{item.symbol}</strong>
                          <span>{item.companyName}</span>
                          <small>{item.sector || 'Sector not available'}</small>
                        </div>
                        <div className="watchlist-stock-quote">
                          <strong>{formatMoney(item.currentPrice)}</strong>
                          <span className="source-tag">
                            {isDemo ? 'Demo price · not live' : `Source: ${item.marketSource || 'unspecified'}`}
                          </span>
                        </div>
                        <div className={`watchlist-change ${item.dayChange == null
                          ? ''
                          : Number(item.dayChange) >= 0 ? 'positive' : 'negative'}`}
                        >
                          <span>Day change</span>
                          <strong>{item.dayChange == null ? '—' : formatMoney(item.dayChange)}</strong>
                          {item.dayChangePercent != null && (
                            <small>{Number(item.dayChangePercent).toFixed(2)}%</small>
                          )}
                        </div>
                        <button
                          className="ghost-btn watchlist-remove-btn"
                          type="button"
                          disabled={removingWatchlistSymbol === item.symbol}
                          onClick={() => handleRemoveWatchlistStock(item.symbol)}
                          aria-label={`Remove ${item.symbol} from watchlist`}
                        >
                          {removingWatchlistSymbol === item.symbol ? 'Removing…' : 'Remove'}
                        </button>
                      </article>
                    )
                  })}
                </div>
              )}
            </section>

            <section className="bottom-grid">
              <div id="my-portfolio" className="panel holdings-panel">
                <div className="panel-header">
                  <div>
                    <h3>My Holdings</h3>
                    <p className="panel-caption">Current position values and unrealized P/L</p>
                  </div>
                </div>
                {!selectedPortfolio ? (
                  <p className="empty-inline">Create or select a portfolio to view holdings.</p>
                ) : selectedPortfolio.holdings.length === 0 ? (
                  <p className="empty-inline">No holdings yet. Use Trade on a supported stock to make a virtual buy.</p>
                ) : (
                  <div className="table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>Symbol</th>
                          <th>Shares</th>
                          <th>Current value</th>
                          <th>Unrealized P/L</th>
                        </tr>
                      </thead>
                      <tbody>
                        {selectedPortfolio.holdings.map((holding) => (
                          <tr key={holding.id}>
                            <td><strong>{holding.symbol}</strong><small className="table-subtitle">{holding.companyName}</small></td>
                            <td>{holding.quantity}</td>
                            <td>{formatMoney(holding.marketValue)}</td>
                            <td className={Number(holding.unrealizedProfitLoss) >= 0 ? 'positive' : 'negative'}>
                              {formatMoney(holding.unrealizedProfitLoss)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              <div className="panel txn-panel recent-transactions-panel">
                <div className="panel-header">
                  <div>
                    <h3>Recent Transactions</h3>
                    <p className="panel-caption">Latest completed virtual trades</p>
                  </div>
                </div>
                {!selectedPortfolio ? (
                  <p className="empty-inline">Select a portfolio to view transactions.</p>
                ) : selectedPortfolio.transactions.length === 0 ? (
                  <p className="empty-inline">No transactions recorded yet.</p>
                ) : (
                  <ul className="recent-transaction-list">
                    {recentTransactions.map((transaction) => (
                      <li key={transaction.id}>
                        <div>
                          <strong>{transaction.tradeType} {transaction.symbol}</strong>
                          <small>{formatDate(transaction.executedAt)}</small>
                        </div>
                        <span>{transaction.quantity} shares</span>
                        <b>{formatMoney(transaction.totalAmount)}</b>
                      </li>
                    ))}
                  </ul>
                )}
                {selectedPortfolio?.transactions.length > 0 && (
                  <a className="text-link" href="#transaction-history">View full transaction history</a>
                )}
              </div>
            </section>

            <section id="transaction-history" className="panel full-history-panel">
              <div className="panel-header">
                <div>
                  <h3>Transaction History</h3>
                  <p className="panel-caption">All completed virtual trades</p>
                </div>
              </div>
              {!selectedPortfolio ? (
                <p className="empty-inline">Select a portfolio to view transactions.</p>
              ) : selectedPortfolio.transactions.length === 0 ? (
                <p className="empty-inline">No transactions recorded yet.</p>
              ) : (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Transaction</th>
                        <th>Date</th>
                        <th>Shares</th>
                        <th>Amount</th>
                        <th>Realized P/L</th>
                      </tr>
                    </thead>
                    <tbody>
                      {selectedPortfolio.transactions.map((transaction) => (
                        <tr key={transaction.id}>
                          <td><strong>{transaction.tradeType} {transaction.symbol}</strong></td>
                          <td>{formatDate(transaction.executedAt)}</td>
                          <td>{transaction.quantity}</td>
                          <td>{formatMoney(transaction.totalAmount)}</td>
                          <td className={transaction.tradeType === 'SELL'
                            ? Number(transaction.realizedProfitLoss) >= 0 ? 'positive' : 'negative'
                            : undefined}
                          >
                            {transaction.tradeType === 'SELL'
                              ? formatMoney(transaction.realizedProfitLoss)
                              : '—'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          </>
        )}
      </main>
      ) : (
        <StockIntelligence
          stocks={stocks}
          credentials={credentials}
          hasPortfolio={Boolean(selectedPortfolio)}
          initialSymbol={intelligenceSymbol}
          onBackToDashboard={() => setActivePage('dashboard')}
          onTrade={openTrade}
        />
      )}

      {createOpen && (
        <div className="modal-backdrop" onMouseDown={(event) => {
          if (event.target === event.currentTarget) setCreateOpen(false)
        }}>
          <section className="modal-card" role="dialog" aria-modal="true" aria-labelledby="create-title">
            <div className="panel-header">
              <h2 id="create-title">Create a virtual portfolio</h2>
              <button className="icon-btn" type="button" aria-label="Close" onClick={() => setCreateOpen(false)}>×</button>
            </div>
            {actionError && <p className="alert error-alert" role="alert">{actionError}</p>}
            <form className="modal-form" onSubmit={handleCreatePortfolio}>
              <label>
                Portfolio name
                <input
                  autoFocus
                  required
                  maxLength={80}
                  value={portfolioName}
                  onChange={(event) => setPortfolioName(event.target.value)}
                  placeholder="e.g. Long-term growth"
                />
              </label>
              <p className="panel-caption">A new portfolio starts with the backend's virtual cash balance.</p>
              <div className="modal-actions">
                <button className="ghost-btn" type="button" onClick={() => setCreateOpen(false)}>Cancel</button>
                <button className="primary-btn" type="submit" disabled={actionBusy}>
                  {actionBusy ? 'Creating…' : 'Create portfolio'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}

      {tradeStock && (
        <div className="modal-backdrop" onMouseDown={(event) => {
          if (event.target === event.currentTarget) setTradeStock(null)
        }}>
          <section className="modal-card" role="dialog" aria-modal="true" aria-labelledby="trade-title">
            <div className="panel-header">
              <div>
                <p className="eyebrow">Virtual trade</p>
                <h2 id="trade-title">{tradeStock.symbol} · {tradeStock.companyName}</h2>
              </div>
              <button className="icon-btn" type="button" aria-label="Close" onClick={() => setTradeStock(null)}>×</button>
            </div>
            {actionError && <p className="alert error-alert" role="alert">{actionError}</p>}
            <form className="modal-form" onSubmit={handleTrade}>
              <div className="trade-switch" role="group" aria-label="Trade type">
                <button
                  className={tradeType === 'buy' ? 'selected' : ''}
                  type="button"
                  onClick={() => setTradeType('buy')}
                >Buy</button>
                <button
                  className={tradeType === 'sell' ? 'selected' : ''}
                  type="button"
                  onClick={() => setTradeType('sell')}
                >Sell</button>
              </div>
              <label>
                Quantity
                <input
                  type="number"
                  required
                  min="0.000001"
                  step="any"
                  value={quantity}
                  onChange={(event) => setQuantity(event.target.value)}
                />
              </label>
              <div className="trade-estimate">
                <span>Price per share ({tradeStock.marketSource === 'demo' ? 'demo' : 'backend source'})</span>
                <strong>{formatMoney(tradeStock.currentPrice)}</strong>
              </div>
              <p className="panel-caption">This submits a simulated trade to the SmartStock backend; no real order is placed.</p>
              <div className="modal-actions">
                <button className="ghost-btn" type="button" onClick={() => setTradeStock(null)}>Cancel</button>
                <button className="primary-btn" type="submit" disabled={actionBusy}>
                  {actionBusy ? 'Submitting…' : `Confirm ${tradeType}`}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  )
}

export default App
