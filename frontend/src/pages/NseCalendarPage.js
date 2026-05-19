import React, { useState, useEffect } from 'react';
import { fetchHolidays } from '../services/holidayService';
import { getUpcomingHolidays, isWeekend } from '../utils/nseCalendar';
import './NseCalendarPage.css';

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

const SHORT_DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function formatDate(isoStr) {
  const [y, m, d] = isoStr.split('-').map(Number);
  const date = new Date(y, m - 1, d);
  return `${SHORT_DAYS[date.getDay()]}, ${String(d).padStart(2, '0')} ${MONTHS[m - 1].slice(0, 3)} ${y}`;
}

function toISO(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function groupByMonth(holidays) {
  const groups = {};
  for (const h of holidays) {
    const month = parseInt(h.date.split('-')[1], 10) - 1;
    if (!groups[month]) groups[month] = [];
    groups[month].push(h);
  }
  return groups;
}

function countTradingDaysLeft(holidays, year) {
  const today = new Date();
  const endOfYear = new Date(year, 11, 31);
  if (today.getFullYear() !== year) return null;
  const holidaySet = new Set(holidays.map(h => h.date));
  let count = 0;
  const cursor = new Date(today);
  cursor.setHours(0, 0, 0, 0);
  while (cursor <= endOfYear) {
    if (!isWeekend(cursor) && !holidaySet.has(toISO(cursor))) count++;
    cursor.setDate(cursor.getDate() + 1);
  }
  return count;
}

const NseCalendarPage = () => {
  const thisYear = new Date().getFullYear();
  const selectedYear = thisYear;
  const [holidays, setHolidays] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const today = toISO(new Date());

  useEffect(() => {
    setLoading(true);
    setError(null);
    fetchHolidays(selectedYear)
      .then(data => { setHolidays(data); setLoading(false); })
      .catch(e => { setError(e.message); setLoading(false); });
  }, [selectedYear]);

  const grouped = groupByMonth(holidays);
  const upcoming = getUpcomingHolidays(holidays, 5);
  const tradingDaysLeft = countTradingDaysLeft(holidays, selectedYear);

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1 className="page-title">NSE Holiday Calendar</h1>
          <p className="cal-subtitle">Official NSE trading holidays — Capital Markets segment</p>
        </div>
        <div className="cal-year-tabs">
          <button className="cal-year-tab active">{thisYear}</button>
        </div>
      </div>

      {loading && <div className="page-loading"><p>Loading holidays...</p></div>}
      {error && <div className="page-error"><p>Could not load holiday data: {error}</p></div>}

      {!loading && !error && (
        <div className="cal-layout">
          <div className="cal-main">
            {selectedYear === thisYear && tradingDaysLeft !== null && (
              <div className="cal-trading-days-banner">
                <strong>{tradingDaysLeft}</strong> trading days remaining in {thisYear}
              </div>
            )}

            {holidays.length === 0 && (
              <div className="cal-empty">No holiday data available for {selectedYear}.</div>
            )}

            {MONTHS.map((monthName, monthIdx) => {
              const monthHolidays = grouped[monthIdx];
              if (!monthHolidays) return null;
              return (
                <div key={monthIdx} className="cal-month-card">
                  <div className="cal-month-header">{monthName} {selectedYear}</div>
                  <div className="cal-holiday-list">
                    {monthHolidays.map(h => {
                      const isToday = h.date === today;
                      const isPast = h.date < today;
                      return (
                        <div
                          key={h.date}
                          className={`cal-holiday-row ${isToday ? 'today' : ''} ${isPast ? 'past' : ''}`}
                        >
                          <span className="cal-holiday-date">{formatDate(h.date)}</span>
                          <span className="cal-holiday-name">{h.name}</span>
                          {isToday && <span className="cal-today-badge">Today</span>}
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>

          <div className="cal-sidebar">
            <div className="cal-sidebar-card">
              <div className="cal-sidebar-title">Next Holidays</div>
              {upcoming.length === 0 ? (
                <p className="cal-sidebar-empty">No upcoming holidays this year.</p>
              ) : (
                upcoming.map(h => (
                  <div key={h.date} className={`cal-upcoming-row ${h.date === today ? 'today' : ''}`}>
                    <span className="cal-upcoming-date">{formatDate(h.date)}</span>
                    <span className="cal-upcoming-name">{h.name}</span>
                  </div>
                ))
              )}
            </div>

            <div className="cal-sidebar-card cal-legend-card">
              <div className="cal-sidebar-title">Legend</div>
              <div className="cal-legend-row">
                <span className="cal-legend-dot today-dot" /> Today / Holiday
              </div>
              <div className="cal-legend-row">
                <span className="cal-legend-dot past-dot" /> Past holiday
              </div>
              <div className="cal-legend-row">
                <span className="cal-legend-dot upcoming-dot" /> Upcoming holiday
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default NseCalendarPage;
