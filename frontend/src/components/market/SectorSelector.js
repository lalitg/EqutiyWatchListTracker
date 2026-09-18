import React from 'react';
import './SectorSelector.css';

const DEFAULT_OPTIONS = ['All', 'IT', 'Banking', 'Pharma', 'Auto', 'FMCG'];

const SectorSelector = ({ selected, onSelect, options, labels, tooltips }) => {
  const items = options || DEFAULT_OPTIONS;
  return (
    <div className="sector-selector">
      {items.map((item) => (
        <button
          key={item}
          className={`sector-tab ${selected === item ? 'active' : ''}`}
          onClick={() => onSelect(item)}
          data-tooltip={tooltips ? tooltips[item] : undefined}
        >
          {labels ? (labels[item] || item) : item}
        </button>
      ))}
    </div>
  );
};

export default SectorSelector;
