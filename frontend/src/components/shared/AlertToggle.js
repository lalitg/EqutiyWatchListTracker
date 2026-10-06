import React, { useCallback, useEffect, useState } from 'react';
import { getSubscriptionStatus, subscribe, unsubscribe } from '../../services/alertService';
import './AlertToggle.css';

/**
 * "Alert me" — the one control that authorises email about a company.
 *
 * <p>Deliberately separate from the watchlist. Adding a company to a watchlist says "I want to look
 * at this"; this button says "put this in my inbox". Conflating them is how a reading list turns
 * into mail nobody asked for.
 *
 * Hidden entirely when signed out rather than shown disabled: a control that cannot do anything
 * teaches the reader nothing, and the company page is readable without an account.
 *
 * @param {string}  symbol    the company this button subscribes to
 * @param {boolean} isLoggedIn whether there is a session to subscribe with
 */
const AlertToggle = ({ symbol, isLoggedIn }) => {
  const [subscribed, setSubscribed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isLoggedIn || !symbol) {
      setLoading(false);
      return;
    }

    let cancelled = false;
    setLoading(true);

    getSubscriptionStatus(symbol)
      .then(result => { if (!cancelled) setSubscribed(Boolean(result.subscribed)); })
      // A failed status read must not cost the reader the whole company page; the button simply
      // shows the unsubscribed state, and pressing it still works.
      .catch(() => {})
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, [symbol, isLoggedIn]);

  const toggle = useCallback(async () => {
    setBusy(true);
    setError('');

    // Optimistic: the button reflects the intent immediately and reverts if the server disagrees.
    const next = !subscribed;
    setSubscribed(next);

    try {
      if (next) await subscribe(symbol);
      else await unsubscribe(symbol);
    } catch (err) {
      setSubscribed(!next);
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }, [subscribed, symbol]);

  if (!isLoggedIn || loading) return null;

  const title = subscribed
    ? `You get an email when ${symbol} makes news that reads strongly positive or negative. `
      + 'Click to stop.'
    : `Email me when ${symbol} makes news that reads strongly positive or negative — at most one `
      + 'digest per batch of news, never more than a few a day.';

  return (
    <div className="cdp-alert-wrap">
      <button
        type="button"
        className={`cdp-alert-btn ${subscribed ? 'cdp-alert-btn--on' : ''}`}
        onClick={toggle}
        disabled={busy}
        title={title}
        aria-pressed={subscribed}
      >
        <span aria-hidden="true">{subscribed ? '🔔' : '🔕'}</span>
        {subscribed ? 'Alerts on' : 'Alert me'}
      </button>
      {error && <span className="cdp-alert-error">{error}</span>}
    </div>
  );
};

export default AlertToggle;
