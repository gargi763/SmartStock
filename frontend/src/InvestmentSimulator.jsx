import { useRef, useState } from 'react'
import { api } from './services/api'

const initialInputs = {
  initialInvestment: '10000',
  monthlyContribution: '500',
  durationMonths: '120',
  annualReturnPercent: '7',
  stockSymbol: '',
  scenarioName: '',
}

const moneyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
})

function formatMoney(value) {
  const amount = Number(value)
  return Number.isFinite(amount) ? moneyFormatter.format(amount) : '—'
}

function compactMoney(value) {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return ''
  return new Intl.NumberFormat('en-US', {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(amount)
}

function validateInputs(inputs) {
  const errors = []
  const amountPattern = /^\d{1,12}(?:\.\d{1,2})?$/
  const percentPattern = /^-?\d{1,3}(?:\.\d{1,4})?$/
  const initial = inputs.initialInvestment.trim()
  const monthly = inputs.monthlyContribution.trim() || '0'
  const annual = inputs.annualReturnPercent.trim()

  if (!amountPattern.test(initial)) {
    errors.push('Enter an initial investment from 0 to 999,999,999,999.99 with up to two decimal places.')
  }
  if (!amountPattern.test(monthly)) {
    errors.push('Enter a monthly contribution from 0 to 999,999,999,999.99 with up to two decimal places.')
  }
  if (amountPattern.test(initial) && amountPattern.test(monthly)
      && Number(initial) === 0 && Number(monthly) === 0) {
    errors.push('Initial investment and monthly contribution cannot both be zero.')
  }

  const duration = /^\d+$/.test(inputs.durationMonths) ? Number(inputs.durationMonths) : NaN
  if (!Number.isInteger(duration) || duration < 1 || duration > 600) {
    errors.push('Duration must be a whole number of months from 1 to 600.')
  }

  if (!percentPattern.test(annual)
      || !Number.isFinite(Number(annual))
      || Number(annual) < -100
      || Number(annual) > 100) {
    errors.push('Annual return must be between -100% and 100%, with up to four decimal places.')
  }

  if (inputs.stockSymbol && !/^[A-Za-z0-9][A-Za-z0-9.-]{0,9}$/.test(inputs.stockSymbol.trim())) {
    errors.push('Stock symbol must be 1-10 letters, digits, dots, or hyphens.')
  }
  if (inputs.scenarioName.length > 60) {
    errors.push('Scenario name must be at most 60 characters.')
  }
  return errors
}

function createChartPoints(timeline, key, maximum) {
  const width = 900
  const height = 300
  const left = 76
  const right = 22
  const top = 20
  const bottom = 42
  const plotWidth = width - left - right
  const plotHeight = height - top - bottom
  return timeline.map((point, index) => {
    const x = timeline.length === 1
      ? left + plotWidth
      : left + (index / (timeline.length - 1)) * plotWidth
    const value = Number(point[key])
    const y = top + plotHeight - (maximum > 0 ? value / maximum : 0) * plotHeight
    return { x, y, value, month: point.month }
  })
}

function ProjectionChart({ timeline }) {
  const maximum = Math.max(
    ...timeline.map((point) => Number(point.projectedValue)),
    ...timeline.map((point) => Number(point.cumulativeContributions)),
    1,
  )
  const valuePoints = createChartPoints(timeline, 'projectedValue', maximum)
  const contributionPoints = createChartPoints(timeline, 'cumulativeContributions', maximum)
  const width = 900
  const height = 300
  const left = 76
  const right = 22
  const top = 20
  const plotHeight = height - top - 42
  const finalMonth = timeline[timeline.length - 1].month
  const ticks = [0, 0.25, 0.5, 0.75, 1]

  return (
    <div className="simulator-chart-wrap">
      <svg
        className="simulator-chart"
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="Chart comparing cumulative contributions with projected portfolio value over time"
      >
        {ticks.map((fraction) => {
          const y = top + plotHeight * (1 - fraction)
          const value = maximum * fraction
          return (
            <g key={fraction}>
              <line className="simulator-chart-grid" x1={left} y1={y} x2={width - right} y2={y} />
              <text className="simulator-chart-axis-label" x={left - 10} y={y + 4} textAnchor="end">
                {compactMoney(value)}
              </text>
            </g>
          )
        })}
        <polyline
          className="simulator-chart-contributions"
          points={contributionPoints.map((point) => `${point.x},${point.y}`).join(' ')}
        />
        <polyline
          className="simulator-chart-projection"
          points={valuePoints.map((point) => `${point.x},${point.y}`).join(' ')}
        />
        {valuePoints.filter((point) => point.month === 0 || point.month === finalMonth).map((point) => (
          <circle className="simulator-chart-point" key={`value-${point.month}`} cx={point.x} cy={point.y} r="4">
            <title>Month {point.month}: projected value {formatMoney(point.value)}</title>
          </circle>
        ))}
        {contributionPoints.filter((point) => point.month === 0 || point.month === finalMonth).map((point) => (
          <circle className="simulator-chart-contribution-point" key={`contribution-${point.month}`} cx={point.x} cy={point.y} r="4">
            <title>Month {point.month}: cumulative contributions {formatMoney(point.value)}</title>
          </circle>
        ))}
        <text className="simulator-chart-axis-label" x={left} y={height - 12}>Month 0</text>
        <text className="simulator-chart-axis-label" x={width - right} y={height - 12} textAnchor="end">
          Month {finalMonth}
        </text>
      </svg>
      <div className="simulator-chart-legend" aria-label="Chart legend">
        <span><i className="simulator-legend-projection" />Projected value</span>
        <span><i className="simulator-legend-contributions" />Cumulative contributions</span>
      </div>
    </div>
  )
}

function ScenarioComparison({ scenarios }) {
  if (scenarios.length === 0) return null
  return (
    <section className="simulator-panel" aria-labelledby="simulator-comparison-title">
      <div className="simulator-panel-heading">
        <div>
          <p className="eyebrow">Temporary comparison</p>
          <h2 id="simulator-comparison-title">Saved scenarios</h2>
        </div>
        <span>{scenarios.length} of 3</span>
      </div>
      <p className="simulator-note">Scenarios are kept in this page only and are not saved to your account.</p>
      <div className="simulator-table-wrap">
        <table className="simulator-table">
          <thead>
            <tr>
              <th scope="col">Scenario</th>
              <th scope="col">Assumptions</th>
              <th scope="col">Contributions</th>
              <th scope="col">Projected ending value</th>
              <th scope="col">Hypothetical gain/loss</th>
            </tr>
          </thead>
          <tbody>
            {scenarios.map((scenario, index) => (
              <tr key={`${scenario.scenarioName}-${index}`}>
                <th scope="row">{scenario.scenarioName || `Scenario ${index + 1}`}</th>
                <td>{scenario.durationMonths} mo · {scenario.annualReturnPercent}%</td>
                <td>{formatMoney(scenario.totalContributions)}</td>
                <td>{formatMoney(scenario.projectedEndingValue)}</td>
                <td className={Number(scenario.hypotheticalGainLoss) < 0 ? 'simulator-negative' : 'simulator-positive'}>
                  {formatMoney(scenario.hypotheticalGainLoss)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

export default function InvestmentSimulator({ credentials }) {
  const [inputs, setInputs] = useState(initialInputs)
  const [result, setResult] = useState(null)
  const [scenarios, setScenarios] = useState([])
  const [errors, setErrors] = useState([])
  const [requestError, setRequestError] = useState('')
  const [busy, setBusy] = useState(false)
  const submissionInProgress = useRef(false)

  function updateInput(event) {
    const { name, value } = event.target
    setInputs((current) => ({ ...current, [name]: value }))
    setResult(null)
    setErrors([])
    setRequestError('')
  }

  async function calculate(event) {
    event.preventDefault()
    if (submissionInProgress.current) return
    const validationErrors = validateInputs(inputs)
    setErrors(validationErrors)
    setRequestError('')
    if (validationErrors.length > 0) {
      setResult(null)
      return
    }

    submissionInProgress.current = true
    setBusy(true)
    try {
      const response = await api.calculateSimulation(credentials, {
        ...inputs,
        initialInvestment: inputs.initialInvestment.trim(),
        monthlyContribution: inputs.monthlyContribution.trim() || '0',
        durationMonths: Number(inputs.durationMonths),
        annualReturnPercent: inputs.annualReturnPercent.trim(),
        stockSymbol: inputs.stockSymbol.trim() || null,
        scenarioName: inputs.scenarioName.trim() || null,
      })
      setResult(response)
    } catch (error) {
      setResult(null)
      setRequestError(error.message)
    } finally {
      submissionInProgress.current = false
      setBusy(false)
    }
  }

  function reset() {
    setInputs({ ...initialInputs })
    setResult(null)
    setScenarios([])
    setErrors([])
    setRequestError('')
  }

  function saveScenario() {
    if (!result || scenarios.length >= 3) return
    setScenarios((current) => current.length < 3 ? [...current, result] : current)
  }

  const isSaved = result && scenarios.includes(result)

  return (
    <main className="simulator-page">
      <header className="simulator-page-heading">
        <p className="eyebrow">Planning tools</p>
        <h1>Investment Simulator</h1>
        <p>Explore how contributions and an assumed return could affect a hypothetical investment over time.</p>
      </header>

      <section className="simulator-disclaimer" role="note">
        <strong>Hypothetical estimates based on user-provided assumptions.</strong>
        <span>Projections are not guarantees, actual profits, or predictions of any stock&apos;s future performance.</span>
      </section>

      <section className="simulator-panel" aria-labelledby="simulator-input-title">
        <div className="simulator-panel-heading">
          <div>
            <p className="eyebrow">Your assumptions</p>
            <h2 id="simulator-input-title">Build a projection</h2>
          </div>
        </div>
        <form className="simulator-form" onSubmit={calculate} noValidate>
          <label>
            <span>Initial investment (USD)</span>
            <input
              name="initialInvestment"
              type="number"
              disabled={busy}
              min="0"
              max="999999999999.99"
              step="0.01"
              inputMode="decimal"
              value={inputs.initialInvestment}
              onChange={updateInput}
              aria-describedby="simulator-initial-hint"
              required
            />
            <small id="simulator-initial-hint">Up to two decimal places.</small>
          </label>
          <label>
            <span>Monthly contribution (USD)</span>
            <input
              name="monthlyContribution"
              type="number"
              disabled={busy}
              min="0"
              max="999999999999.99"
              step="0.01"
              inputMode="decimal"
              value={inputs.monthlyContribution}
              onChange={updateInput}
              aria-describedby="simulator-monthly-hint"
            />
            <small id="simulator-monthly-hint">Optional; contributions are assumed at each month&apos;s end.</small>
          </label>
          <label>
            <span>Duration (months)</span>
            <input
              name="durationMonths"
              type="number"
              disabled={busy}
              min="1"
              max="600"
              step="1"
              inputMode="numeric"
              value={inputs.durationMonths}
              onChange={updateInput}
              required
            />
            <small>Choose 1–600 months.</small>
          </label>
          <label>
            <span>Assumed annual return (%)</span>
            <input
              name="annualReturnPercent"
              type="number"
              disabled={busy}
              min="-100"
              max="100"
              step="0.0001"
              inputMode="decimal"
              value={inputs.annualReturnPercent}
              onChange={updateInput}
              required
            />
            <small>-100% means the initial balance is lost after the first month; end-of-month deposits remain for that month.</small>
          </label>
          <label>
            <span>Stock symbol for reference <em>(optional)</em></span>
            <input
              name="stockSymbol"
              type="text"
              disabled={busy}
              maxLength="10"
              autoCapitalize="characters"
              value={inputs.stockSymbol}
              onChange={updateInput}
              placeholder="e.g. AAPL"
              aria-describedby="simulator-symbol-hint"
            />
            <small id="simulator-symbol-hint">A label only; no prices or forecasts are used.</small>
          </label>
          <label>
            <span>Scenario name <em>(optional)</em></span>
            <input
              name="scenarioName"
              type="text"
              disabled={busy}
              maxLength="60"
              value={inputs.scenarioName}
              onChange={updateInput}
              placeholder="e.g. Steady contributions"
            />
            <small>Up to 60 characters.</small>
          </label>

          {errors.length > 0 && (
            <div className="simulator-errors" role="alert">
              <strong>Check your inputs:</strong>
              <ul>{errors.map((error) => <li key={error}>{error}</li>)}</ul>
            </div>
          )}
          {requestError && <p className="simulator-errors" role="alert">{requestError}</p>}

          <div className="simulator-actions">
            <button className="primary-btn" type="submit" disabled={busy}>
              {busy ? 'Calculating…' : 'Calculate projection'}
            </button>
            <button className="ghost-btn" type="button" onClick={reset} disabled={busy}>Reset</button>
          </div>
        </form>
      </section>

      {busy && <p className="simulator-state" role="status">Calculating your hypothetical projection…</p>}
      {!result && !busy && !requestError && (
        <p className="simulator-state">Enter assumptions and calculate to see a hypothetical estimate.</p>
      )}
      {result && !busy && (
        <>
          <section className="simulator-results" aria-label="Projection summary">
            <article className="simulator-summary-card simulator-summary-featured">
              <span>Projected ending value</span>
              <strong>{formatMoney(result.projectedEndingValue)}</strong>
              <small>Hypothetical estimate</small>
            </article>
            <article className="simulator-summary-card">
              <span>Total contributions</span>
              <strong>{formatMoney(result.totalContributions)}</strong>
              <small>Initial plus monthly deposits</small>
            </article>
            <article className="simulator-summary-card">
              <span>Hypothetical gain/loss</span>
              <strong className={Number(result.hypotheticalGainLoss) < 0 ? 'simulator-negative' : 'simulator-positive'}>
                {formatMoney(result.hypotheticalGainLoss)}
              </strong>
              <small>Projected value less contributions</small>
            </article>
          </section>

          <section className="simulator-panel" aria-labelledby="simulator-chart-title">
            <div className="simulator-panel-heading">
              <div>
                <p className="eyebrow">{result.durationMonths} months · {result.annualReturnPercent}% assumed annual return</p>
                <h2 id="simulator-chart-title">
                  {result.scenarioName || 'Projected value over time'}
                  {result.stockSymbol ? ` · ${result.stockSymbol}` : ''}
                </h2>
              </div>
              <button
                className="ghost-btn"
                type="button"
                onClick={saveScenario}
                disabled={scenarios.length >= 3 || isSaved}
              >
                {isSaved ? 'Added to comparison' : scenarios.length >= 3 ? 'Comparison full' : 'Add to comparison'}
              </button>
            </div>
            {result.timeline?.length > 0
              ? <ProjectionChart timeline={result.timeline} />
              : <p className="simulator-state" role="alert">The API returned no projection timeline.</p>}
            <p className="simulator-note">
              Monthly rate is derived from the effective annual return: (1 + annual rate)<sup>1/12</sup> − 1.
              Currency results are rounded to cents using half-even rounding.
            </p>
          </section>
        </>
      )}

      <ScenarioComparison scenarios={scenarios} />
    </main>
  )
}
