import React from 'react';

const TabBar = ({ tabs, activeTab, onTabChange }) => {
  const containerStyle = {
    display: 'flex',
    gap: '4px',
    borderBottom: '2px solid #e5e7eb',
    marginBottom: '20px',
  };

  const tabStyle = (isActive) => ({
    padding: '10px 20px',
    fontSize: '14px',
    fontWeight: isActive ? 600 : 500,
    color: isActive ? '#667eea' : '#64748b',
    background: 'none',
    border: 'none',
    borderBottom: isActive ? '2px solid #667eea' : '2px solid transparent',
    marginBottom: '-2px',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
  });

  return (
    <div style={containerStyle}>
      {tabs.map((tab) => (
        <button
          key={tab.key}
          style={tabStyle(activeTab === tab.key)}
          onClick={() => onTabChange(tab.key)}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
};

export default TabBar;
