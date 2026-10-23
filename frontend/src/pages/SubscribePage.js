import React, { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useEntitlements, FEATURES } from '../context/EntitlementsContext';
import { fetchPlans } from '../services/billingService';
import './SubscribePage.css';

/**
 * The pricing page — where every padlock in the app leads.
 *
 * <p>Prices come from the server, because they change: {@code GET /api/v1/plans} reads the rows
 * marketing edits, so a price rise needs no deployment. Free is drawn here and not fetched, because
 * there is no free row to sell — free is simply what a reader has before they buy anything.
 *
 * <p>The comparison table below the cards is sales copy and says so. The live answer to "may I see
 * this?" always comes from the entitlements call, never from this list; if the two ever disagree it
 * is this file that is out of date, and the reader still gets what the server grants them.
 *
 * <p>Arriving with {@code ?feature=sentiments} highlights the row the reader was blocked on and
 * marks the cheapest plan that includes it. A padlock that forgets what the reader wanted makes
 * them hunt for it on a page of nine rows.
 */

const TIERS = ['FREE', 'BASIC', 'ADVANCED'];

/** Shown for the tier nobody buys. */
const FREE_CARD = {
  id: 'free',
  tier: 'FREE',
  displayName: 'Free',
  description: 'Watch a handful of companies and read the day’s news.',
  priceDisplay: '₹0',
  billingCycle: 'MONTHLY',
};

/**
 * One row per thing a reader might be looking for, in the order they meet them in the app.
 *
 * `feature` ties a row to the padlock that sent the reader here. `from` is the cheapest tier that
 * includes it, which is also what decides the ticks.
 */
const COMPARISON = [
  { label: 'Watchlists',                      from: 'FREE',     value: { FREE: '1', BASIC: '10', ADVANCED: '20' } },
  { label: 'Companies per watchlist',         from: 'FREE',     value: { FREE: '5', BASIC: '10', ADVANCED: '25' } },
  { label: 'Company fundamentals and P/E',    from: 'FREE' },
  { label: 'Latest company news',             from: 'FREE' },
  { label: 'Global market news — all regions', from: 'BASIC',   feature: FEATURES.GLOBAL_ALL_REGIONS },
  { label: 'Full domestic market news',       from: 'BASIC',    feature: FEATURES.DOMESTIC_DRILLDOWN },
  { label: 'Index and sector company lists',  from: 'BASIC',    feature: FEATURES.DOMESTIC_DRILLDOWN },
  { label: 'Important news, events and regulatory filings', from: 'BASIC', feature: FEATURES.COMPANY_NEWS_TABS },
  { label: 'Upcoming events',                 from: 'BASIC',    feature: FEATURES.EVENTS },
  { label: 'NSE holiday calendar',            from: 'BASIC',    feature: FEATURES.CALENDAR },
  { label: 'Mutual fund documents',           from: 'BASIC',    feature: FEATURES.MUTUAL_FUNDS },
  { label: 'News sentiment scores',           from: 'ADVANCED', feature: FEATURES.SENTIMENTS },
  { label: 'Sentiment extremes boards',       from: 'ADVANCED', feature: FEATURES.EXTREMES },
  { label: 'News alerts and notifications',   from: 'ADVANCED', feature: FEATURES.ALERTS },
];

/** What each plan leads with on its card. */
const HIGHLIGHTS = {
  FREE: ['1 watchlist, 5 companies', 'Latest company news', 'Fundamentals and P/E'],
  BASIC: ['10 watchlists, 10 companies each', 'All market news and events', 'Index and sector lists',
          'Mutual fund documents and calendar'],
  ADVANCED: ['20 watchlists, 25 companies each', 'Everything in Basic', 'News sentiment everywhere',
             'Extremes boards', 'Alerts and notifications'],
};

const includes = (row, tier) => TIERS.indexOf(tier) >= TIERS.indexOf(row.from);

const SubscribePage = () => {
  const { isLoggedIn } = useAuth();
  const { tier: currentTier, expiresAt, failed } = useEntitlements();
  const [searchParams] = useSearchParams();
  const wanted = searchParams.get('feature');

  const [plans, setPlans]     = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);

  useEffect(() => {
    let cancelled = false;
    fetchPlans()
      .then(result => { if (!cancelled) setPlans(result || []); })
      .catch(e => { if (!cancelled) setError(e.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  /** Free first, then whatever the server sells, in tier order. */
  const cards = useMemo(() => {
    const paid = TIERS
      .filter(t => t !== 'FREE')
      .map(t => plans.find(p => p.tier === t))
      .filter(Boolean);
    return [FREE_CARD, ...paid];
  }, [plans]);

  /** The cheapest tier that answers what the reader clicked. */
  const neededTier = useMemo(() => {
    if (!wanted) return null;
    const row = COMPARISON.find(r => r.feature === wanted);
    return row ? row.from : null;
  }, [wanted]);

  const wantedLabel = useMemo(() => {
    const row = COMPARISON.find(r => r.feature === wanted);
    return row ? row.label : null;
  }, [wanted]);

  return (
    <div className="page-container sub-page">
      <div className="sub-header">
        <h1 className="page-title">Plans</h1>
        <p className="sub-subtitle">
          Niveshflow is free to use. Paid plans open up the market pages, and the news sentiment
          the app is built around.
        </p>
      </div>

      {wantedLabel && (
        <div className="sub-wanted" role="note">
          <strong>{wantedLabel}</strong> is part of the{' '}
          {neededTier === 'ADVANCED' ? 'Advanced' : 'Basic'} plan.
        </div>
      )}

      {isLoggedIn && !failed && (
        <p className="sub-current-line">
          You are on the <strong>{currentTier === 'FREE' ? 'Free' : currentTier === 'BASIC' ? 'Basic' : 'Advanced'}</strong> plan
          {expiresAt && currentTier !== 'FREE' && (
            <> — your period runs to {new Date(expiresAt).toLocaleDateString('en-IN', {
              day: 'numeric', month: 'short', year: 'numeric',
            })}</>
          )}
          .
        </p>
      )}

      {loading && <div className="page-loading"><p>Loading plans…</p></div>}

      {error && !loading && (
        // Worth saying plainly: without prices there is nothing on this page to decide from.
        <div className="page-error">
          <p>Could not load the plans: {error}</p>
        </div>
      )}

      {!loading && (
        <div className="sub-cards">
          {cards.map(plan => {
            const isCurrent   = isLoggedIn && !failed && plan.tier === currentTier;
            const isSuggested = plan.tier === neededTier;
            return (
              <div
                key={plan.id}
                className={`sub-card sub-card--${plan.tier.toLowerCase()}
                            ${isSuggested ? 'sub-card--suggested' : ''}
                            ${isCurrent ? 'sub-card--current' : ''}`}
              >
                {isSuggested && <div className="sub-card-flag">Answers what you clicked</div>}
                {isCurrent && !isSuggested && <div className="sub-card-flag sub-card-flag--current">Your plan</div>}

                <h2 className="sub-card-name">{plan.displayName}</h2>
                <div className="sub-card-price">
                  {plan.priceDisplay ?? `₹${Math.round((plan.pricePaise ?? 0) / 100)}`}
                  <span className="sub-card-period">
                    {plan.tier === 'FREE' ? '' : plan.billingCycle === 'ANNUAL' ? '/year' : '/month'}
                  </span>
                </div>
                {plan.description && <p className="sub-card-desc">{plan.description}</p>}

                <ul className="sub-card-list">
                  {(HIGHLIGHTS[plan.tier] || []).map(item => (
                    <li key={item}><span aria-hidden="true">✓</span> {item}</li>
                  ))}
                </ul>

                {plan.tier === 'FREE' ? (
                  <div className="sub-card-cta sub-card-cta--muted">
                    {isCurrent ? 'Your current plan' : 'Always free'}
                  </div>
                ) : isCurrent ? (
                  <div className="sub-card-cta sub-card-cta--muted">Your current plan</div>
                ) : (
                  // Checkout arrives with the payment flow. A button that opened nothing would be
                  // worse than one that says so.
                  <button className="sub-card-cta" type="button" disabled title="Payments are not switched on yet">
                    Coming soon
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}

      <div className="sub-compare-wrap">
        <h2 className="sub-compare-title">What each plan includes</h2>
        <div className="sub-compare-scroll">
          <table className="sub-compare">
            <thead>
              <tr>
                <th>&nbsp;</th>
                {TIERS.map(t => (
                  <th key={t} className={t === neededTier ? 'sub-col-suggested' : ''}>
                    {t === 'FREE' ? 'Free' : t === 'BASIC' ? 'Basic' : 'Advanced'}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {COMPARISON.map(row => (
                <tr key={row.label} className={row.feature && row.feature === wanted ? 'sub-row-wanted' : ''}>
                  <td className="sub-compare-label">{row.label}</td>
                  {TIERS.map(t => (
                    <td key={t} className={t === neededTier ? 'sub-col-suggested' : ''}>
                      {row.value
                        ? row.value[t]
                        : includes(row, t)
                          ? <span className="sub-yes" aria-label="included">✓</span>
                          : <span className="sub-no" aria-label="not included">—</span>}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="sub-footnote" role="note">
        Prices are in Indian rupees and include nothing beyond access to Niveshflow. Sentiment scores
        are produced automatically by a model reading news headlines.{' '}
        {!isLoggedIn && (
          <>You will need an account to subscribe — <Link to="/login">sign in or create one</Link>.</>
        )}
      </div>
    </div>
  );
};

export default SubscribePage;
