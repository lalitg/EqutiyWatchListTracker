import React from 'react';
import './SentimentBadge.css';

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

/** Absolute date, e.g. "3 Sep 2026", in Indian market time. */
export function formatSentimentDate(millis) {
  return new Date(millis).toLocaleDateString('en-IN', {
    timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short', year: 'numeric',
  });
}

/**
 * "today" / "yesterday" / "12 days ago".
 *
 * Paired with the absolute date rather than replacing it. "12 days ago" is what makes staleness
 * register at a glance; the calendar date is what lets the reader match the reading against the
 * article list further down the page.
 */
export function formatSentimentAge(millis) {
  const days = Math.floor((Date.now() - millis) / 86400000);
  if (days <= 0) return 'today';
  if (days === 1) return 'yesterday';
  if (days < 30) return `${days} days ago`;
  const months = Math.round(days / 30);
  return months === 1 ? 'about a month ago' : `about ${months} months ago`;
}

/**
 * Renders a sentiment indicator.
 *
 * Three call styles:
 *
 *   <SentimentBadge sentiment="BULLISH" />                              market trend (existing)
 *   <SentimentBadge sentiment={{ score, label, articleCount }} />       a period average
 *   <SentimentBadge sentiment={{ score, label, publishedAt }} variant="latest" />   one article
 *
 * `variant` changes only the tooltip, never the colours — the bands mean the same thing either
 * way. It matters because the two readings are not the same kind of claim and must not be read as
 * interchangeable: "latest" is one article, the default is an average over a period. A reader who
 * mistakes one for the other draws the wrong conclusion from a row where they disagree — which is
 * exactly the case the two table columns exist to surface.
 *
 * SHOWING THE NUMERIC SCORE (`showScore`): off by default, and deliberately so. Measured accuracy
 * on our own headlines is roughly 0.75 macro-F1, so about one prediction in four is wrong. In a
 * dense table, printing "+3.7" invites comparing companies on differences that are within the
 * model's noise. On the company detail page the number is genuinely useful — the reader has the
 * contributing articles right there to judge it against — so that page opts in. Rendered to one
 * decimal, never more: the underlying score is stored to two, but a second decimal would imply
 * precision that simply is not there.
 */
const SentimentBadge = ({
  sentiment,
  compact = false,
  showScore = false,
  variant = 'aggregate',
  onClick,
}) => {
  const isObject = sentiment !== null && typeof sentiment === 'object';
  const clickable = typeof onClick === 'function';

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
  if (isObject && variant === 'latest') {
    if (!hasScore) {
      title = 'No scored news yet for this company';
    } else {
      // The date is not decoration. This reading is deliberately never suppressed for being old,
      // so disclosing when it happened is the only thing standing between a months-old headline
      // and a reader who assumes it describes today.
      const when = sentiment.publishedAt
        ? ` Published ${formatSentimentDate(sentiment.publishedAt)}`
          + ` (${formatSentimentAge(sentiment.publishedAt)}).`
        : '';
      title = `Sentiment of the single most recent news article — not an average.${when}`
            + ` Score ${scoreText} on a -5 (very negative) to +5 (very positive) scale.`;
    }
  } else if (isObject) {
    title = !hasScore
      ? 'No scored news in this period'
      : `Score ${scoreText} on a -5 (very negative) to +5 (very positive) scale, `
        + `averaged over ${sentiment.articleCount} `
        + `article${sentiment.articleCount === 1 ? '' : 's'}`;
  }

  if (!clickable) {
    return <span style={style} title={title}>{text}</span>;
  }

  // A span with onClick is invisible to the keyboard: it cannot be reached by Tab and does not
  // fire on Enter. role + tabIndex + the key handler are what stop this being a mouse-only
  // feature. Space is included because a control announced as a button is expected to accept it,
  // and its default page-scroll has to be suppressed.
  const handleKeyDown = (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onClick(e);
    }
  };

  return (
    <span
      className="sentiment-badge--clickable"
      style={style}
      title={title ? `${title} — click to open the Sentiments tab` : undefined}
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={handleKeyDown}
    >
      {text}
    </span>
  );
};

export default SentimentBadge;
