import React from 'react';
import './SentimentWindowList.css';

/**
 * Renders a company's sentiment broken down by period — the Sentiments tab.
 *
 * Data comes from the `sentimentWindows` field of GET /api/news?key=, which the company page
 * already fetches, so this component makes no request of its own.
 *
 * Three things about how these numbers are presented are deliberate:
 *
 * LATEST IS NOT A PERIOD. The first row is one article, not an average, so it is separated from
 * the rest by a rule and shows that article's date. Without the separation a reader scans seven
 * rows as one series and quietly assumes the first is just the shortest window — which would make
 * a single volatile headline look like a measurement.
 *
 * NESTED PERIODS. Every row below Latest is a lookback, so the quarter contains the month, which
 * contains the fortnight. Neighbouring rows will often read almost the same, and that is correct
 * rather than broken — it means nothing much has changed. The note under the table says so,
 * because the alternative is a reader concluding the page is buggy.
 *
 * ARTICLE COUNT IS NOT DECORATION. These are plain averages, so the score alone cannot tell you
 * whether it rests on one article or forty. Showing the count next to every row is what makes a
 * single-article reading legible as the weak signal it is.
 */

const COLORS = {
  POSITIVE: { bg: '#dcfce7', color: '#16a34a', border: '#bbf7d0' },
  NEGATIVE: { bg: '#fef2f2', color: '#dc2626', border: '#fecaca' },
  NEUTRAL:  { bg: '#fefce8', color: '#ca8a04', border: '#fef08a' },
  NO_DATA:  { bg: '#f4f4f5', color: '#71717a', border: '#e4e4e7' },
};

const DISPLAY_TEXT = {
  POSITIVE: 'Positive',
  NEGATIVE: 'Negative',
  NEUTRAL:  'Neutral',
  NO_DATA:  'No news',
};

const LATEST_ROW_TOOLTIP =
  'The single most recent news article — not an average. Shown regardless of age, so check the '
  + 'date on the right: it may be from today or from months ago.';

/** Absolute date in Indian market time, e.g. "3 Sep 2026". */
function formatDate(millis) {
  return new Date(millis).toLocaleDateString('en-IN', {
    timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short', year: 'numeric',
  });
}

// Score is stored to two decimals but shown to one. A second decimal would imply a precision
// the model does not have — roughly one prediction in four is wrong.
function formatScore(score) {
  if (score === null || score === undefined) return '—';
  return `${score > 0 ? '+' : ''}${score.toFixed(1)}`;
}

// Position of a score on the -5..+5 axis, as a percentage. Used to place the marker on the bar.
function axisPosition(score) {
  const clamped = Math.max(-5, Math.min(5, score));
  return ((clamped + 5) / 10) * 100;
}

const SentimentWindowList = ({ windows, symbol }) => {
  if (!windows || windows.length === 0) {
    return (
      <div className="cdp-fund-empty">
        No sentiment data available for {symbol}.
      </div>
    );
  }

  const hasAnyReading = windows.some(w => w.score !== null && w.score !== undefined);

  return (
    <div className="swl">
      <div className="swl-rows">
        {windows.map(w => {
          const key = (w.sentiment || 'NO_DATA').toUpperCase();
          const scheme = COLORS[key] || COLORS.NEUTRAL;
          const hasScore = w.score !== null && w.score !== undefined;

          const isLatest = w.window === 'LATEST';

          return (
            <div
              className={`swl-row ${hasScore ? '' : 'swl-row--empty'} ${isLatest ? 'swl-row--latest' : ''}`}
              key={w.window}
              title={isLatest ? LATEST_ROW_TOOLTIP : undefined}
            >
              <div className="swl-label">{w.label}</div>

              <div className="swl-bar" title={hasScore
                ? `${formatScore(w.score)} on a -5 (very negative) to +5 (very positive) scale`
                : 'No scored articles published in this period'}>
                <span className="swl-bar-zero" />
                {hasScore && (
                  <span
                    className="swl-bar-marker"
                    style={{
                      left: `${axisPosition(w.score)}%`,
                      background: scheme.color,
                    }}
                  />
                )}
              </div>

              <div className="swl-score" style={{ color: hasScore ? scheme.color : '#94a3b8' }}>
                {formatScore(w.score)}
              </div>

              <div className="swl-badge">
                <span
                  style={{
                    background: scheme.bg,
                    color: scheme.color,
                    border: `1px solid ${scheme.border}`,
                  }}
                >
                  {DISPLAY_TEXT[key] || key}
                </span>
              </div>

              {/* Zero and "no news" must never look alike: 0.0 is a real reading, absence is not.
                  For Latest the count is always 1 and says nothing, so the article's DATE takes
                  that slot instead — it is the only thing that tells the reader whether this
                  reading is hours or months old. */}
              <div className="swl-count">
                {isLatest
                  ? (w.publishedAt ? formatDate(w.publishedAt) : (hasScore ? 'date unknown' : '—'))
                  : (w.articleCount > 0
                      ? `${w.articleCount} article${w.articleCount === 1 ? '' : 's'}`
                      : '—')}
              </div>
            </div>
          );
        })}
      </div>

      <p className="swl-note">
        {hasAnyReading ? (
          <>
            <strong>Latest</strong> is a single article, shown whatever its age — its date is on
            the right. Every row below it is an average of the news published within that period,
            scored from <strong>-5</strong> (very negative) to <strong>+5</strong> (very positive).
            Longer periods include the shorter ones, so neighbouring rows often read similarly —
            that means little has changed, not that a figure is missing. A reading based on one or
            two articles is a weak signal; check the article count beside it.
          </>
        ) : (
          <>No scored news for {symbol} yet. Sentiment appears once articles have been fetched
          and scored.</>
        )}
      </p>
    </div>
  );
};

export default SentimentWindowList;
