import { useEffect, useState } from 'react'
import { api } from './services/api'

const emptyForm = {
  title: '',
  thesis: '',
  lessonsLearned: '',
  tags: '',
  transactionId: '',
}

function formatJournalDate(value) {
  if (!value) return 'Date unavailable'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function InvestmentJournal({ credentials, onBackToDashboard }) {
  const [entries, setEntries] = useState([])
  const [trades, setTrades] = useState([])
  const [form, setForm] = useState(emptyForm)
  const [editingId, setEditingId] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [refresh, setRefresh] = useState(0)

  useEffect(() => {
    let cancelled = false
    Promise.all([
      api.getInvestmentJournalEntries(credentials),
      api.getInvestmentJournalTrades(credentials),
    ])
      .then(([entryData, tradeData]) => {
        if (cancelled) return
        setEntries(entryData)
        setTrades(tradeData)
        setError('')
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
  }, [credentials, refresh])

  function updateField(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  function beginEdit(entry) {
    setEditingId(entry.id)
    setForm({
      title: entry.title,
      thesis: entry.thesis,
      lessonsLearned: entry.lessonsLearned,
      tags: (entry.tags || []).join(', '),
      transactionId: entry.transactionId == null ? '' : String(entry.transactionId),
    })
    setError('')
    setNotice('')
  }

  function cancelEdit() {
    setEditingId(null)
    setForm(emptyForm)
    setError('')
  }

  async function saveEntry(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    setNotice('')
    const request = {
      title: form.title.trim(),
      thesis: form.thesis.trim(),
      lessonsLearned: form.lessonsLearned.trim(),
      tags: form.tags.split(',').map((tag) => tag.trim()).filter(Boolean),
      transactionId: form.transactionId ? Number(form.transactionId) : null,
    }
    try {
      const saved = editingId
        ? await api.updateInvestmentJournalEntry(credentials, editingId, request)
        : await api.createInvestmentJournalEntry(credentials, request)
      setEntries((current) => editingId
        ? current.map((entry) => entry.id === saved.id ? saved : entry)
        : [saved, ...current])
      setNotice(editingId ? 'Journal entry updated.' : 'Journal entry saved.')
      setEditingId(null)
      setForm(emptyForm)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setBusy(false)
    }
  }

  async function deleteEntry(entry) {
    if (!window.confirm(`Delete “${entry.title}” from your investment journal?`)) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await api.deleteInvestmentJournalEntry(credentials, entry.id)
      setEntries((current) => current.filter((item) => item.id !== entry.id))
      if (editingId === entry.id) cancelEdit()
      setNotice('Journal entry deleted.')
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="main-panel journal-page">
      <header className="topbar">
        <div>
          <p className="eyebrow">Reflect · learn · improve</p>
          <h1>Investment Journal</h1>
          <p className="panel-caption">Record the reasoning and lessons behind your virtual investing decisions.</p>
        </div>
        <button className="ghost-btn" type="button" onClick={onBackToDashboard}>Investor dashboard</button>
      </header>

      {notice && (
        <div className="alert success-alert" role="status">
          <span>{notice}</span>
          <button type="button" aria-label="Dismiss notification" onClick={() => setNotice('')}>×</button>
        </div>
      )}
      {error && (
        <div className="alert error-alert" role="alert">
          <span>{error}</span>
          <button type="button" onClick={() => {
            setLoading(true)
            setRefresh((value) => value + 1)
          }}>Retry</button>
        </div>
      )}

      <div className="journal-layout">
        <section className="panel journal-editor" aria-labelledby="journal-editor-title">
          <div className="panel-header">
            <div>
              <h2 id="journal-editor-title">{editingId ? 'Edit journal entry' : 'New journal entry'}</h2>
              <p className="panel-caption">Your notes are private to your account.</p>
            </div>
          </div>
          <form className="journal-form" onSubmit={saveEntry}>
            <label>
              Title
              <input name="title" value={form.title} onChange={updateField}
                maxLength={120} required placeholder="e.g. Why I added to my position" />
            </label>
            <label>
              Trade thesis / reason
              <textarea name="thesis" value={form.thesis} onChange={updateField}
                maxLength={4000} required rows={4} placeholder="What informed this decision?" />
            </label>
            <label>
              Lessons learned
              <textarea name="lessonsLearned" value={form.lessonsLearned} onChange={updateField}
                maxLength={4000} required rows={3} placeholder="What would you repeat or change?" />
            </label>
            <label>
              Tags <span className="journal-label-hint">(optional, comma-separated)</span>
              <input name="tags" value={form.tags} onChange={updateField}
                maxLength={400} placeholder="long-term, risk, earnings" />
            </label>
            <label>
              Link a completed virtual trade <span className="journal-label-hint">(optional)</span>
              <select name="transactionId" value={form.transactionId} onChange={updateField}>
                <option value="">No linked trade</option>
                {trades.map((trade) => (
                  <option key={trade.id} value={trade.id}>
                    {trade.tradeType} {trade.symbol} · {formatJournalDate(trade.executedAt)}
                  </option>
                ))}
              </select>
            </label>
            <p className="journal-helper">A journal entry never places or changes a trade. Only your completed trades can be linked.</p>
            <div className="journal-form-actions">
              {editingId != null && <button className="ghost-btn" type="button" onClick={cancelEdit}>Cancel</button>}
              <button className="primary-btn" type="submit" disabled={busy}>
                {busy ? 'Saving…' : editingId ? 'Save changes' : 'Save entry'}
              </button>
            </div>
          </form>
        </section>

        <section className="journal-entry-list" aria-labelledby="journal-entries-title">
          <div className="panel-header journal-list-header">
            <div>
              <h2 id="journal-entries-title">Your entries</h2>
              <p className="panel-caption">{entries.length} journal entr{entries.length === 1 ? 'y' : 'ies'}</p>
            </div>
          </div>
          {loading ? (
            <section className="panel loading-state" role="status">Loading your journal…</section>
          ) : error && entries.length === 0 ? (
            <section className="panel empty-state">
              <h2>Journal unavailable</h2>
              <p>Check your connection and try loading your entries again.</p>
            </section>
          ) : entries.length === 0 ? (
            <section className="panel empty-state">
              <h2>Your journal is ready</h2>
              <p>Save your first thesis and lesson to start building a record of your decisions.</p>
            </section>
          ) : (
            <div className="journal-cards">
              {entries.map((entry) => (
                <article className="panel journal-entry-card" key={entry.id}>
                  <div className="journal-entry-topline">
                    <div>
                      <h3>{entry.title}</h3>
                      <p className="panel-caption">Updated {formatJournalDate(entry.updatedAt)}</p>
                    </div>
                    <div className="journal-entry-actions">
                      <button className="ghost-btn" type="button" disabled={busy} onClick={() => beginEdit(entry)}>Edit</button>
                      <button className="danger-btn" type="button" disabled={busy} onClick={() => deleteEntry(entry)}>Delete</button>
                    </div>
                  </div>
                  {entry.transactionId != null && (
                    <p className="journal-trade-link">
                      Linked virtual trade: <strong>{entry.transactionType} {entry.transactionSymbol}</strong>
                      <span> · {formatJournalDate(entry.transactionDate)}</span>
                    </p>
                  )}
                  <div className="journal-note-section">
                    <h4>Thesis / reason</h4>
                    <p>{entry.thesis}</p>
                  </div>
                  <div className="journal-note-section">
                    <h4>Lessons learned</h4>
                    <p>{entry.lessonsLearned}</p>
                  </div>
                  {(entry.tags || []).length > 0 && (
                    <ul className="journal-tags" aria-label="Tags">
                      {entry.tags.map((tag) => <li key={tag}>{tag}</li>)}
                    </ul>
                  )}
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </main>
  )
}

export default InvestmentJournal
