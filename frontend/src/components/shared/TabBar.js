import React from 'react';

const TabBar = ({ tabs, activeTab, onTabChange }) => {
  const containerStyle = {
    display: 'flex',
    gap: '4px',
    borderBottom: '2px solid #e5e7eb',
    marginBottom: '20px',
  };

  const tabStyle = (isActive) => ({
    // 44px at every width rather than in a media query: these styles are inline, where media
    // queries cannot follow, and a tab bar that is comfortable on a phone is not worse on a
    // desktop.
    minHeight: '44px',
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

  // minHeight only takes effect on a button if its content is allowed to centre in it.
  const tabLayout = { display: 'inline-flex', alignItems: 'center', justifyContent: 'center' };

  return (
    <div style={containerStyle}>
      {tabs.map((tab) => (
        <button
          key={tab.key}
          style={{ ...tabStyle(activeTab === tab.key), ...tabLayout }}
          onClick={() => onTabChange(tab.key)}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
};

export default TabBar;
