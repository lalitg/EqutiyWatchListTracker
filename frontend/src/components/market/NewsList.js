import React from 'react';
import './NewsList.css';

const NewsList = ({ news }) => {
  if (!news || news.length === 0) {
    return <p className="news-empty">No news available.</p>;
  }

  return (
    <div className="news-list">
      <h3 className="news-list-title">Latest News</h3>
      <div className="news-items">
        {news.map((item) => (
          <div key={item.id} className="news-item">
            <div className="news-item-title">{item.title}</div>
            <div className="news-item-meta">
              <span className="news-source">{item.source}</span>
              <span className="news-date">{item.date}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default NewsList;
