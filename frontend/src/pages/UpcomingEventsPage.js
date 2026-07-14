import React, { useEffect, useState, useCallback } from 'react';
import './UpcomingEventsPage.css';

const CATEGORY_META = {
  RBI_MPC:        { label: 'RBI MPC',   color: '#16a34a', bg: '#dcfce7', flag: '🇮🇳', country: 'IN' },
  FOMC:           { label: 'FOMC',      color: '#2563eb', bg: '#dbeafe', flag: '🇺🇸', country: 'US' },
  US_CPI:         { label: 'US CPI',    color: '#d97706', bg: '#fef3c7', flag: '🇺🇸', country: 'US' },
  US_NFP:         { label: 'US NFP',    color: '#ea580c', bg: '#ffedd5', flag: '🇺🇸', country: 'US' },
  BUDGET:         { label: 'Budget',    color: '#7c3aed', bg: '#ede9fe', flag: '🇮🇳', country: 'IN' },
  ELECTION:       { label: 'Election',  color: '#db2777', bg: '#fce7f3', flag: '🇮🇳', country: 'IN' },
  MUHURAT_TRADING:{ label: 'Muhurat',   color: '#b45309', bg: '#fef9c3', flag: '🇮🇳', country: 'IN' },
};

const COUNTRY_FILTERS = [
  { key: 'ALL', label: 'All' },
  { key: 'IN',  label: '🇮🇳 India' },
  { key: 'US',  label: '🇺🇸 US' },
];

function daysUntil(dateStr) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const ev = new Date(dateStr);
  ev.setHours(0, 0, 0, 0);
  return Math.round((ev - today) / 86400000);
}

function DaysChip({ dateStr }) {
  const d = daysUntil(dateStr);
  if (d === 0)  return <span className="ue-days-chip ue-days-today">Today</span>;
  if (d === 1)  return <span className="ue-days-chip ue-days-soon">Tomorrow</span>;
  if (d <= 7)   return <span className="ue-days-chip ue-days-soon">{d} days</span>;
  return <span className="ue-days-chip ue-days-future">{d} days</span>;
}

function monthLabel(isoDate) {
  const [year, month] = isoDate.split('-');
  const d = new Date(year, month - 1, 1);
  return d.toLocaleString('en-IN', { month: 'long', year: 'numeric' }).toUpperCase();
}

function EventCard({ event }) {
  const meta = CATEGORY_META[event.category] || { label: event.category, color: '#64748b', bg: '#f1f5f9', flag: '' };
  const [year, month, day] = event.eventDate.split('-');
  const displayDate = new Date(year, month - 1, day).toLocaleDateString('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
  });

  return (
    <div className="ue-card" style={{ borderLeftColor: meta.color }}>
      <div className="ue-card-header">
        <div className="ue-card-badges">
          <span className="ue-flag">{meta.flag}</span>
          <span className="ue-category-badge" style={{ color: meta.color, background: meta.bg }}>
            {meta.label}
          </span>
        </div>
        <DaysChip dateStr={event.eventDate} />
      </div>
      <div className="ue-card-name">{event.eventName}</div>
      <div className="ue-card-datetime">
        <span className="ue-card-date">📅 {displayDate}</span>
        {event.eventTime && <span className="ue-card-time">🕐 {event.eventTime}</span>}
      </div>
      {event.description && (
        <div className="ue-card-desc">{event.description}</div>
      )}
    </div>
  );
}

const UpcomingEventsPage = () => {
  const [events, setEvents]           = useState([]);
  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState(null);
  const [countryFilter, setCountry]   = useState('ALL');
  const [categoryFilter, setCategory] = useState('ALL');

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    fetch('/api/macro-events?upcoming=true')
      .then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); })
      .then(setEvents)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const filtered = events.filter(ev => {
    const meta = CATEGORY_META[ev.category];
    if (countryFilter !== 'ALL' && meta?.country !== countryFilter) return false;
    if (categoryFilter !== 'ALL' && ev.category !== categoryFilter) return false;
    return true;
  });

  // Group by month
  const grouped = filtered.reduce((acc, ev) => {
    const key = ev.eventDate.substring(0, 7);
    (acc[key] = acc[key] || []).push(ev);
    return acc;
  }, {});
  const months = Object.keys(grouped).sort();

  // Available categories after country filter
  const availableCategories = [...new Set(
    events
      .filter(ev => countryFilter === 'ALL' || CATEGORY_META[ev.category]?.country === countryFilter)
      .map(ev => ev.category)
  )];

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Upcoming Events</h1>
        <button className="btn btn-secondary" onClick={load}>Refresh</button>
      </div>

      {/* ── Filters ─────────────────────────────────────────────────────────── */}
      <div className="ue-filters">
        <div className="ue-filter-group">
          {COUNTRY_FILTERS.map(f => (
            <button
              key={f.key}
              className={`ue-filter-chip ${countryFilter === f.key ? 'ue-filter-chip--active' : ''}`}
              onClick={() => { setCountry(f.key); setCategory('ALL'); }}
            >
              {f.label}
            </button>
          ))}
        </div>

        <div className="ue-filter-group">
          <button
            className={`ue-filter-chip ${categoryFilter === 'ALL' ? 'ue-filter-chip--active' : ''}`}
            onClick={() => setCategory('ALL')}
          >
            All types
          </button>
          {availableCategories.map(cat => {
            const meta = CATEGORY_META[cat];
            return (
              <button
                key={cat}
                className={`ue-filter-chip ${categoryFilter === cat ? 'ue-filter-chip--active' : ''}`}
                style={categoryFilter === cat ? { background: meta?.color, borderColor: meta?.color, color: '#fff' } : {}}
                onClick={() => setCategory(cat)}
              >
                {meta?.label || cat}
              </button>
            );
          })}
        </div>
      </div>

      {/* ── Content ─────────────────────────────────────────────────────────── */}
      {loading && <div className="page-loading"><p>Loading events…</p></div>}

      {error && !loading && (
        <div className="page-error">
          <p>{error}</p>
          <button className="btn btn-primary" style={{ marginTop: 16 }} onClick={load}>Retry</button>
        </div>
      )}

      {!loading && !error && months.length === 0 && (
        <div className="ue-empty">No upcoming events for the selected filter.</div>
      )}

      {!loading && !error && months.map(month => (
        <div key={month} className="ue-month-section">
          <div className="ue-month-label">{monthLabel(month + '-01')}</div>
          <div className="ue-cards-grid">
            {grouped[month].map(ev => <EventCard key={ev.id} event={ev} />)}
          </div>
        </div>
      ))}
    </div>
  );
};

export default UpcomingEventsPage;
