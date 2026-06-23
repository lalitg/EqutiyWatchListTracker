import React from 'react';
import './NewsList.css';

function formatIST(dateStr) {
  if (!dateStr) return dateStr;
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  return d.toLocaleString('en-IN', {
    timeZone: 'Asia/Kolkata',
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }) + ' IST';
}

const NewsList = ({ news }) => {
  if (!news || news.length === 0) {
    return <p className="news-empty">No news available.</p>;
  }

  return (
    <div className="news-list">
      <div className="news-items">
        {news.map((item, idx) => (
          <div key={idx} className="news-item">
            <div className="news-item-meta">
              <span className="news-date">{formatIST(item.date)}</span>
              {item.source && <span className="news-source">{item.source}</span>}
            </div>
            <div className="news-item-title">
              {item.link ? (
                <a href={item.link} target="_blank" rel="noopener noreferrer" className="news-item-link">
                  {item.summary || item.title}
                </a>
              ) : (
                item.summary || item.title
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default NewsList;
