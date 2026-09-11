import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import TabBar from '../components/shared/TabBar';
import SentimentBadge from '../components/shared/SentimentBadge';
import {
  fetchExtremesDays,
  fetchDailyExtremes,
  fetchCumulativeExtremes,
} from '../services/newsService';
import './ExtremesPage.css';

const MODE_TABS = [
  { key: 'daily',      label: 'Single Day' },
  { key: 'cumulative', label: 'Cumulative' },
];

const CUMULATIVE_RANGES = [
  { key: 'WEEK_1',    label: 'Last 1 week',    days: 7 },
  { key: 'WEEK_2',    label: 'Last 2 weeks',   days: 14 },
  { key: 'MONTH_1',   label: 'Last 1 month',   days: 30 },
  { key: 'QUARTER_1', label: 'Last 1 quarter', days: 90 },
];

const TOOLTIPS = {
  mode: 'Single Day ranks each calendar day on its own. Cumulative averages every article across '
      + 'a rolling period, so it moves more slowly and rests on far more articles.',
  positives: 'The 10 companies whose news sentiment is most positive for the selected period. '
           + 'Positive means the news reads well — it is not a view on the share price.',
  negatives: 'The 10 companies whose news sentiment is most negative for the selected period. '
           + 'Negative means the news reads badly — it is not a view on the share price.',
  score: 'Average sentiment of the news in this period, from -5 (very negative) to '
       + '+5 (very positive).',
  articles: 'How many news articles this score is averaged over. A score resting on one article '
          + 'is a single headline, not a trend — check this number before reading anything into '
          + 'the ranking.',
  row: 'Click to open this company’s Sentiments tab',
};

/**
 * One Top Positives or Top Negatives board.
 *
 * The article count has a column of its own rather than living in a tooltip. There is no minimum
 * article count behind these rankings, so a company whose whole day is one dramatic headline sits
 * directly alongside one carrying twenty — and because single-article scores cluster at the ends of
 * the scale, the lone headline usually ranks higher. Showing the count is what lets a reader tell
 * those two rows apart.
 */
const Board = ({ title, tone, tooltip, entries, loading, emptyNote, onSelect }) => (
  <div className={`ext-board ext-board--${tone}`}>
    <div className="ext-board-head" title={tooltip}>
      <h2>{title}</h2>
      <span className="ext-board-hint" aria-hidden="true">?</span>
    </div>

    {loading ? (
      <div className="ext-empty">Loading…</div>
    ) : !entries || entries.length === 0 ? (
      <div className="ext-empty">{emptyNote}</div>
    ) : (
      <table className="ext-table">
        <thead>
          <tr>
            <th className="ext-col-rank">#</th>
            <th>Company</th>
            <th className="ext-col-score" title={TOOLTIPS.score}>Sentiment</th>
            <th className="ext-col-articles" title={TOOLTIPS.articles}>Articles</th>
          </tr>
        </thead>
        <tbody>
          {entries.map(e => (
            <tr key={e.symbol} onClick={() => onSelect(e.symbol)} title={TOOLTIPS.row}>
              <td className="ext-col-rank">{e.rank}</td>
              <td>
                <strong className="ext-symbol">{e.symbol}</strong>
                {e.companyName && <span className="ext-name">{e.companyName}</span>}
              </td>
              <td className="ext-col-score">
                <SentimentBadge
                  sentiment={{ score: e.score, label: e.label, articleCount: e.articleCount }}
                  compact
                  showScore
                />
              </td>
              <td className="ext-col-articles" title={TOOLTIPS.articles}>
                {e.articleCount}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    )}
  </div>
);

const ExtremesPage = () => {
  const navigate = useNavigate();

  const [mode, setMode] = useState('daily');
  const [days, setDays] = useState([]);
  const [selectedDay, setSelectedDay] = useState(null);
  const [range, setRange] = useState('WEEK_1');
  const [board, setBoard] = useState(null);
  const [loading, setLoading] = useState(true);

  // The day list comes from the backend so both sides agree on which day is "today": the boards are
  // bucketed in Indian market time, and a viewer in another timezone would otherwise request a day
  // the server does not consider current.
  useEffect(() => {
    fetchExtremesDays().then(result => {
      setDays(result);
      if (result.length > 0) setSelectedDay(result[0].day);
    });
  }, []);

  const load = useCallback(() => {
    setLoading(true);
    const request = mode === 'daily'
      ? fetchDailyExtremes(selectedDay)
      : fetchCumulativeExtremes(range);

    let cancelled = false;
    request
      .then(result => { if (!cancelled) setBoard(result); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [mode, selectedDay, range]);

  useEffect(() => {
    if (mode === 'daily' && !selectedDay) return;   // wait for the day list
    return load();
  }, [load, mode, selectedDay]);

  const openSentiments = (symbol) =>
    navigate(`/company/${encodeURIComponent(symbol)}?tab=sentiments`);

  // Said differently for each mode. On a single day the honest reason for an empty board is almost
  // always that nothing was published — weekends carry roughly a quarter of a weekday's articles —
  // and a bare "no data" would read as a fault instead.
  const emptyNote = mode === 'daily'
    ? 'No company news was published on this day.'
    : 'No company news in this period yet.';

  return (
    <div className="ext-page">
      <div className="ext-header">
        <h1 className="page-title">Extremes (News Sentiments)</h1>
        <p className="ext-subtitle">
          The 10 companies with the most positive and the most negative news sentiment.
        </p>
      </div>

      <div title={TOOLTIPS.mode}>
        <TabBar tabs={MODE_TABS} activeTab={mode} onTabChange={setMode} />
      </div>

      {mode === 'daily' ? (
        <div className="ext-range-row" role="tablist" aria-label="Select a day">
          {days.map(d => (
            <button
              key={d.day}
              role="tab"
              aria-selected={selectedDay === d.day}
              className={`ext-range-btn ${selectedDay === d.day ? 'active' : ''}`}
              onClick={() => setSelectedDay(d.day)}
              title={`${d.label} — ${d.weekday} ${d.day}`}
            >
              <span className="ext-range-label">{d.label}</span>
              <span className="ext-range-sub">{d.weekday}</span>
            </button>
          ))}
        </div>
      ) : (
        <div className="ext-range-row" role="tablist" aria-label="Select a period">
          {CUMULATIVE_RANGES.map(r => (
            <button
              key={r.key}
              role="tab"
              aria-selected={range === r.key}
              className={`ext-range-btn ${range === r.key ? 'active' : ''}`}
              onClick={() => setRange(r.key)}
              title={`Average sentiment across every article from the last ${r.days} days`}
            >
              <span className="ext-range-label">{r.label}</span>
              <span className="ext-range-sub">{r.days} days</span>
            </button>
          ))}
        </div>
      )}

      <div className="ext-boards">
        <Board
          title="Top Positives"
          tone="positive"
          tooltip={TOOLTIPS.positives}
          entries={board?.topPositives}
          loading={loading}
          emptyNote={emptyNote}
          onSelect={openSentiments}
        />
        <Board
          title="Top Negatives"
          tone="negative"
          tooltip={TOOLTIPS.negatives}
          entries={board?.topNegatives}
          loading={loading}
          emptyNote={emptyNote}
          onSelect={openSentiments}
        />
      </div>

      {board?.generatedAt && (
        // Worth showing: the Today board moves on the 15-minute fetch cycle, so a reader needs to
        // know how fresh what they are looking at actually is.
        <p className="ext-generated" title="Boards are recalculated as new news arrives, roughly every 15 minutes">
          Last updated {new Date(board.generatedAt).toLocaleString('en-IN', {
            timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short',
            hour: '2-digit', minute: '2-digit', hour12: false,
          })} IST
        </p>
      )}

      {/* Closes the page, below the boards it qualifies.

          It is rendered unconditionally — not gated on there being results — so the notice is
          present even on an empty weekend board, and its own top border separates it from the data
          rather than letting it read as another row of it. */}
      <div className="ext-disclaimer" role="note">
        <strong>Please read:</strong> these sentiment scores are generated automatically by an AI
        model reading news headlines. They are provided <strong>for analysis and information
        only</strong> and are <strong>not investment advice, and not a recommendation</strong> to
        buy, sell or hold any security. The model can be wrong, headlines can be misleading, and a
        score based on very few articles is especially unreliable — always check the article count
        and do your own research before making any decision.
      </div>
    </div>
  );
};

export default ExtremesPage;
