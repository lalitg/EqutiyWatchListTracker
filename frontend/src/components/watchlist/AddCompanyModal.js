import React, { useState, useRef, useEffect } from 'react';
import ReactDOM from 'react-dom';
import Modal from '../shared/Modal';
import { useCompanyList } from '../../context/CompanyListContext';
import './AddCompanyModal.css';

const MAX_SUGGESTIONS = 8;

const AddCompanyModal = ({ isOpen, onClose, onAdd, isLoading }) => {
  const { companies, loaded } = useCompanyList();
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [selected, setSelected] = useState(null);
  const [highlightedIdx, setHighlightedIdx] = useState(-1);
  const [dropdownStyle, setDropdownStyle] = useState({});
  const inputRef = useRef(null);

  // Reposition dropdown whenever suggestions change or window scrolls
  useEffect(() => {
    if (suggestions.length === 0 || !inputRef.current) return;
    const rect = inputRef.current.getBoundingClientRect();
    setDropdownStyle({
      position: 'fixed',
      top: rect.bottom + 4,
      left: rect.left,
      width: rect.width,
      zIndex: 9999,
    });
  }, [suggestions]);

  useEffect(() => {
    if (!query.trim() || !loaded) {
      setSuggestions([]);
      setHighlightedIdx(-1);
      return;
    }
    const q = query.trim().toLowerCase();
    const filtered = companies.filter(
      (c) =>
        c.symbol.toLowerCase().includes(q) ||
        c.companyName.toLowerCase().includes(q)
    );
    setSuggestions(filtered.slice(0, MAX_SUGGESTIONS));
    setHighlightedIdx(-1);
  }, [query, companies, loaded]);

  const handleSelect = (company) => {
    setSelected(company);
    setQuery(`${company.symbol} — ${company.companyName}`);
    setSuggestions([]);
    setHighlightedIdx(-1);
  };

  const handleInputChange = (e) => {
    setSelected(null);
    setQuery(e.target.value);
  };

  const handleKeyDown = (e) => {
    if (suggestions.length === 0) {
      if (e.key === 'Enter' && selected) handleSubmit();
      return;
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightedIdx((i) => Math.min(i + 1, suggestions.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightedIdx((i) => Math.max(i - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (highlightedIdx >= 0) {
        handleSelect(suggestions[highlightedIdx]);
      } else if (suggestions.length === 1) {
        handleSelect(suggestions[0]);
      }
    } else if (e.key === 'Escape') {
      setSuggestions([]);
    }
  };

  const handleSubmit = () => {
    if (!selected) return;
    onAdd(selected.symbol);
    reset();
  };

  const reset = () => {
    setQuery('');
    setSelected(null);
    setSuggestions([]);
    setHighlightedIdx(-1);
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const dropdown = suggestions.length > 0
    ? ReactDOM.createPortal(
        <ul className="autocomplete-list" style={dropdownStyle}>
          {suggestions.map((c, idx) => (
            <li
              key={c.symbol}
              className={`autocomplete-item${idx === highlightedIdx ? ' highlighted' : ''}`}
              onMouseDown={() => handleSelect(c)}
            >
              <span className="autocomplete-symbol">{c.symbol}</span>
              <span className="autocomplete-name">{c.companyName}</span>
            </li>
          ))}
        </ul>,
        document.body
      )
    : null;

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title="Add Company">
      <div className="autocomplete-wrapper">
        <label className="autocomplete-label">Search by symbol or name</label>
        <input
          ref={inputRef}
          type="text"
          className="autocomplete-input"
          value={query}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          placeholder={loaded ? 'e.g. INFY or Infosys' : 'Loading companies…'}
          autoFocus
          disabled={isLoading}
          autoComplete="off"
        />
        {dropdown}
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12, marginTop: 24 }}>
        <button className="btn btn-secondary" onClick={handleClose} disabled={isLoading}>
          Cancel
        </button>
        <button
          className="btn btn-primary"
          onClick={handleSubmit}
          disabled={isLoading || !selected}
        >
          {isLoading ? 'Adding...' : 'Add'}
        </button>
      </div>
    </Modal>
  );
};

export default AddCompanyModal;
