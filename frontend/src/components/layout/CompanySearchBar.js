import React, { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCompanyList } from '../../context/CompanyListContext';

const MAX_RESULTS = 8;

const CompanySearchBar = () => {
  const { companies } = useCompanyList();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);

  const q = query.trim().toUpperCase();
  const results = q.length < 2 ? [] : companies
    .filter(c =>
      c.symbol.toUpperCase().includes(q) ||
      (c.companyName && c.companyName.toUpperCase().includes(q))
    )
    .slice(0, MAX_RESULTS);

  useEffect(() => {
    const handleClick = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  const handleSelect = (symbol) => {
    setQuery('');
    setOpen(false);
    navigate(`/company/${encodeURIComponent(symbol)}`);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Escape') { setQuery(''); setOpen(false); }
    if (e.key === 'Enter' && results.length > 0) handleSelect(results[0].symbol);
  };

  return (
    <div className="csb-container" ref={containerRef}>
      <input
        className="csb-input"
        type="text"
        placeholder="Search company..."
        value={query}
        onChange={e => { setQuery(e.target.value); setOpen(true); }}
        onFocus={() => setOpen(true)}
        onKeyDown={handleKeyDown}
        autoComplete="off"
      />
      {open && results.length > 0 && (
        <ul className="csb-dropdown">
          {results.map(c => (
            <li key={c.symbol} className="csb-item" onMouseDown={() => handleSelect(c.symbol)}>
              <span className="csb-symbol">{c.symbol}</span>
              {c.companyName && <span className="csb-name">{c.companyName}</span>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default CompanySearchBar;
