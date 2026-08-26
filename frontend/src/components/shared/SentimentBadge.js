import React from 'react';

const COLORS = {
  BULLISH:  { bg: '#dcfce7', color: '#16a34a', border: '#bbf7d0' },
  BEARISH:  { bg: '#fef2f2', color: '#dc2626', border: '#fecaca' },
  NEUTRAL:  { bg: '#fefce8', color: '#ca8a04', border: '#fef08a' },
  POSITIVE: { bg: '#dcfce7', color: '#16a34a', border: '#bbf7d0' },
  NEGATIVE: { bg: '#fef2f2', color: '#dc2626', border: '#fecaca' },
  NO_DATA:  { bg: '#f4f4f5', color: '#71717a', border: '#e4e4e7' },
};

const DISPLAY_TEXT = {
  POSITIVE: 'Positive',
  NEGATIVE: 'Negative',
  NEUTRAL:  'Neutral',
  NO_DATA:  'No news',
};

/**
 * Renders a sentiment indicator.
 *
 * Two call styles, both supported:
 *
 *   <SentimentBadge sentiment="BULLISH" />                  market trend (existing usage)
 *   <SentimentBadge sentiment={{ score, label, articleCount }} />   news sentiment
 *
 * The object form accepts the SentimentDto returned by /api/news/sentiment and by the
 * currentSentiment field of /api/news.
 *
 * SHOWING THE NUMERIC SCORE (`showScore`): off by default, and deliberately so. Measured
 * accuracy on our own headlines is roughly 0.75 macro-F1, so about one prediction in four
 * is wrong. In a dense table, printing "+3.7" invites comparing companies on differences
 * that are within the model's noise. On the company detail page the number is genuinely
 * useful — the reader has the contributing articles right there to judge it against — so
 * that page opts in. Rendered to one decimal, never more: the underlying score is stored
 * to two, but a second decimal would imply precision that simply is not there.
 */
const SentimentBadge = ({ sentiment, compact = false, showScore = false }) => {
  const isObject = sentiment !== null && typeof sentiment === 'object';

  const key = isObject
    ? (sentiment.label || 'NO_DATA').toUpperCase()
    : (sentiment || 'NEUTRAL').toUpperCase();

  const scheme = COLORS[key] || COLORS.NEUTRAL;

  const hasScore = isObject
    && sentiment.score !== null
    && sentiment.score !== undefined;

  // Always sign the number. A bare "1.5" reads as a magnitude on a 0-5 scale; "+1.5"
  // makes it unambiguous that this is a point on a -5..+5 axis with zero in the middle.
  const scoreText = hasScore
    ? `${sentiment.score > 0 ? '+' : ''}${sentiment.score.toFixed(1)}`
    : null;

  const label = DISPLAY_TEXT[key] || key;
  const text = showScore && scoreText ? `${label} ${scoreText}` : label;

  const style = {
    display: 'inline-block',
    padding: compact ? '2px 8px' : '4px 12px',
    borderRadius: '20px',
    fontSize: compact ? '11px' : '12px',
    fontWeight: 600,
    letterSpacing: '0.5px',
    whiteSpace: 'nowrap',
    background: scheme.bg,
    color: scheme.color,
    border: `1px solid ${scheme.border}`,
  };

  let title;
  if (isObject) {
    title = !hasScore
      ? 'No scored news yet for this company'
      : `Score ${scoreText} on a -5 (very negative) to +5 (very positive) scale, `
        + `averaged over the latest ${sentiment.articleCount} `
        + `article${sentiment.articleCount === 1 ? '' : 's'}`;
  }

  return <span style={style} title={title}>{text}</span>;
};

export default SentimentBadge;
