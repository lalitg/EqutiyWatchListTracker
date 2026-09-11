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

const SENTIMENT_TEXT = {
  POSITIVE: 'Positive',
  NEGATIVE: 'Negative',
  NEUTRAL:  'Neutral',
};

/**
 * Per-article sentiment chip.
 *
 * Shows the score alongside the label, unlike the company-level badge used in tables. Here the
 * reader has the headline the score came from sitting right next to it, so the number is
 * checkable rather than an invitation to compare companies on differences within the model's
 * noise. One decimal: the score is stored to two, but a second would imply precision the model
 * does not have.
 *
 * Renders nothing when an article has no score. Articles fetched before scoring existed, and any
 * whose scoring failed, carry null — and an absent score is not a neutral one.
 */
const ArticleSentiment = ({ item }) => {
  const score = item.sentimentScore;
  const label = item.sentimentLabel;
  if (score === null || score === undefined || !label) return null;

  const key = label.toUpperCase();
  const scoreText = `${score > 0 ? '+' : ''}${score.toFixed(1)}`;

  return (
    <span
      className={`news-sentiment news-sentiment--${key.toLowerCase()}`}
      title={`This headline scored ${scoreText} on a -5 (very negative) to +5 (very positive) scale`}
    >
      {SENTIMENT_TEXT[key] || key} {scoreText}
    </span>
  );
};

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
              <ArticleSentiment item={item} />
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
