import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { fetchHolidays, getCachedHolidays } from '../../services/holidayService';
import { getMarketStatus } from '../../utils/nseCalendar';
import './MarketStatusBanner.css';

const MarketStatusBanner = () => {
  const year = new Date().getFullYear();

  const [status, setStatus] = useState(() => {
    const cached = getCachedHolidays(year);
    return cached ? getMarketStatus(cached) : null;
  });

  useEffect(() => {
    fetchHolidays(year)
      .then(holidays => setStatus(getMarketStatus(holidays)))
      .catch(() => {});
  }, [year]);

  if (!status || status.isOpen) return null;

  return (
    <div className="market-status-banner">
      <span className="market-status-dot" />
      <span className="market-status-text">
        Market Closed Today
        {status.reason && <span className="market-status-reason"> — {status.reason}</span>}
      </span>
      <Link to="/market/calendar" className="market-status-link">View Calendar →</Link>
    </div>
  );
};

export default MarketStatusBanner;
