import { useMemo, useState } from 'react'

const moneyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
})

function formatMoney(value) {
  return Number.isFinite(value) ? moneyFormatter.format(value) : 'N/A'
}

function validNumber(value) {
  if (value == null || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function reportedDailyChange(stock) {
  const price = validNumber(stock.currentPrice)
  const absoluteChange = validNumber(stock.dayChange)
  const percentChange = validNumber(stock.dayChangePercent)
  if (price == null || price <= 0 || absoluteChange == null || percentChange == null) return null
  if (price - absoluteChange <= 0) return null
  return percentChange
}

function sourceLabel(source) {
  if (!source || !source.trim()) return 'Source unavailable'
  if (source.trim().toLowerCase() === 'demo') return 'Simulated demo · not live'
  if (source.trim().toLowerCase() === 'unavailable') return 'Price source unavailable'
  return `Source: ${source.trim()}`
}

function changeClass(change) {
  if (change == null || change === 0) return 'neutral'
  const magnitude = Math.abs(change)
  const intensity = magnitude >= 5 ? 'strong' : magnitude >= 2 ? 'medium' : 'light'
  return `${change > 0 ? 'positive' : 'negative'} ${intensity}`
}

function MarketHeatmap({ stocks, loading, error, onRetry, onSelectStock, onBackToDashboard }) {
  const [search, setSearch] = useState('')
  const [sector, setSector] = useState('')
  const [movement, setMovement] = useState('all')
  const [sort, setSort] = useState('symbol')

  const sectors = useMemo(() => [...new Set(
    stocks.map((stock) => stock.sector?.trim()).filter(Boolean),
  )].sort((left, right) => left.localeCompare(right)), [stocks])

  const visibleStocks = useMemo(() => {
    const query = search.trim().toLocaleLowerCase()
    return stocks
      .filter((stock) => (
        (!query
          || stock.symbol?.toLocaleLowerCase().includes(query)
          || stock.companyName?.toLocaleLowerCase().includes(query))
        && (!sector || stock.sector?.trim() === sector)
        && (movement === 'all'
          || (movement === 'gainers' && reportedDailyChange(stock) > 0)
          || (movement === 'losers' && reportedDailyChange(stock) < 0))
      ))
      .sort((left, right) => {
        if (sort === 'change-desc' || sort === 'change-asc') {
          const leftChange = reportedDailyChange(left)
          const rightChange = reportedDailyChange(right)
          if (leftChange == null && rightChange != null) return 1
          if (rightChange == null && leftChange != null) return -1
          if (leftChange != null && rightChange != null && leftChange !== rightChange) {
            return sort === 'change-desc' ? rightChange - leftChange : leftChange - rightChange
          }
        }
        return (left.symbol || '').localeCompare(right.symbol || '')
      })
  }, [movement, search, sector, sort, stocks])

  const summary = visibleStocks.reduce((counts, stock) => {
    const change = reportedDailyChange(stock)
    if (change == null) {
      counts.unavailable += 1
    } else if (change > 0) {
      counts.advancing += 1
    } else if (change < 0) {
      counts.declining += 1
    } else {
      counts.unchanged += 1
    }
    return counts
  }, { advancing: 0, declining: 0, unchanged: 0, unavailable: 0 })

  function resetFilters() {
    setSearch('')
    setSector('')
    setMovement('all')
    setSort('symbol')
  }

  return (
    <main className="main-panel heatmap-page">
      <header className="topbar">
        <div>
          <p className="eyebrow">Supported stock universe</p>
          <h1>Market Heatmap</h1>
          <p className="panel-caption">Compare the stock records currently available in SmartStock. This is not a complete exchange view.</p>
        </div>
        <button className="ghost-btn" type="button" onClick={onBackToDashboard}>Investor dashboard</button>
      </header>

      <p className="heatmap-data-note" role="note">
        Prices and percentage changes are the values reported by the existing stock API. Demo prices are simulated, not live.
        The stock API does not include quote timestamps; the as-of time is unavailable.
      </p>

      {loading ? (
        <section className="panel loading-state" role="status">Loading supported stock data…</section>
      ) : error ? (
        <section className="panel empty-state">
          <h2>Heatmap data unavailable</h2>
          <p>{error}</p>
          <p>Try again when the backend is available.</p>
          <button className="primary-btn" type="button" onClick={onRetry}>Retry</button>
        </section>
      ) : stocks.length === 0 ? (
        <section className="panel empty-state">
          <h2>No supported stocks</h2>
          <p>The stock API returned no stock records to display.</p>
        </section>
      ) : (
        <>
          <section className="heatmap-summary" aria-label="Counts for current heatmap view" aria-live="polite">
            <article><strong>{visibleStocks.length}</strong><span>Displayed</span></article>
            <article className="summary-positive"><strong>{summary.advancing}</strong><span>Advancing</span></article>
            <article className="summary-negative"><strong>{summary.declining}</strong><span>Declining</span></article>
            <article><strong>{summary.unchanged}</strong><span>Unchanged</span></article>
            <article><strong>{summary.unavailable}</strong><span>Data unavailable</span></article>
          </section>

          <section className="panel heatmap-controls" aria-label="Filter and sort heatmap">
            <label className="heatmap-search">
              <span>Search</span>
              <input type="search" value={search} onChange={(event) => setSearch(event.target.value)}
                placeholder="Symbol or company name" />
            </label>
            <label>
              <span>Sector</span>
              <select value={sector} onChange={(event) => setSector(event.target.value)} disabled={sectors.length === 0}>
                <option value="">All sectors</option>
                {sectors.map((item) => <option key={item} value={item}>{item}</option>)}
              </select>
            </label>
            <label>
              <span>Movement</span>
              <select value={movement} onChange={(event) => setMovement(event.target.value)}>
                <option value="all">All stocks</option>
                <option value="gainers">Gainers</option>
                <option value="losers">Losers</option>
              </select>
            </label>
            <label>
              <span>Sort</span>
              <select value={sort} onChange={(event) => setSort(event.target.value)}>
                <option value="symbol">Symbol A–Z</option>
                <option value="change-desc">Change: high to low</option>
                <option value="change-asc">Change: low to high</option>
              </select>
            </label>
            <button className="ghost-btn heatmap-reset" type="button" onClick={resetFilters}>Reset filters</button>
          </section>

          <section className="heatmap-legend" aria-label="Daily change colour legend">
            <strong>Reported daily change</strong>
            <span><i className="legend-swatch positive-light" /> Positive</span>
            <span><i className="legend-swatch negative-light" /> Negative</span>
            <span><i className="legend-swatch neutral" /> Unchanged / unavailable</span>
            <small>Colour intensity increases with the absolute reported percentage.</small>
          </section>

          {visibleStocks.length === 0 ? (
            <section className="panel empty-state heatmap-empty">
              <h2>No stocks match these filters</h2>
              <p>Try a different search, sector, or movement filter.</p>
              <button className="ghost-btn" type="button" onClick={resetFilters}>Reset filters</button>
            </section>
          ) : (
            <section className="heatmap-grid" aria-label="Stocks heatmap">
              {visibleStocks.map((stock) => {
                const price = validNumber(stock.currentPrice)
                const change = reportedDailyChange(stock)
                const tileClass = price == null || price <= 0 || change == null
                  ? 'neutral'
                  : changeClass(change)
                const changeLabel = change == null
                  ? 'N/A'
                  : `${change > 0 ? '+' : ''}${change.toFixed(2)}%`
                return (
                  <button
                    className={`heatmap-tile ${tileClass}`}
                    type="button"
                    key={stock.symbol}
                    onClick={() => onSelectStock(stock.symbol)}
                    aria-label={`${stock.symbol}, ${stock.companyName}, price ${price != null && price > 0 ? formatMoney(price) : 'not available'}, reported daily change ${changeLabel}. Open Stock Intelligence.`}
                    title="Open this stock in Stock Intelligence"
                  >
                    <span className="heatmap-symbol">{stock.symbol}</span>
                    <span className="heatmap-company">{stock.companyName}</span>
                    {stock.sector?.trim() && <span className="heatmap-sector">{stock.sector.trim()}</span>}
                    <strong className="heatmap-price">{price != null && price > 0 ? formatMoney(price) : 'N/A'}</strong>
                    <span className="heatmap-change">{changeLabel}</span>
                    <span className="heatmap-source">{sourceLabel(stock.marketSource)}</span>
                    <span className="heatmap-source">As of: unavailable</span>
                    <span className="heatmap-open-hint">Open Stock Intelligence →</span>
                  </button>
                )
              })}
            </section>
          )}
        </>
      )}
    </main>
  )
}

export default MarketHeatmap
