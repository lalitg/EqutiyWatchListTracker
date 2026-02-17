import React from 'react';
import './SectorSelector.css';

const DEFAULT_OPTIONS = ['All', 'IT', 'Banking', 'Pharma', 'Auto', 'FMCG'];

const SectorSelector = ({ selected, onSelect, options }) => {
  const items = options || DEFAULT_OPTIONS;
  return (
    <div className="sector-selector">
      {items.map((sector) => (
        <button
          key={sector}
          className={`sector-tab ${selected === sector ? 'active' : ''}`}
          onClick={() => onSelect(sector)}
        >
          {sector}
        </button>
      ))}
    </div>
  );
};

export default SectorSelector;
