import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useEntitlements, FEATURES } from '../context/EntitlementsContext';
import {
  cancelSubscription, createSubscription, fetchPlans, verifySubscription,
} from '../services/billingService';
import { loadRazorpayCheckout } from '../services/razorpayCheckout';
import Modal from '../components/shared/Modal';
import Toast from '../components/shared/Toast';
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
 *
 * <p>This is also where a reader manages what they already have, which is why cancelling lives
 * here rather than on an account page of its own. Cancelling and downgrading are the same
 * decision taken with different amounts of patience, and someone heading for "cancel" often
 * actually wants the cheaper plan — so the plan cards stay in view at the moment of leaving.
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
  const { isLoggedIn, user } = useAuth();
  const { tier: currentTier, expiresAt, cancelAtPeriodEnd, failed, refresh } = useEntitlements();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const wanted = searchParams.get('feature');

  const [plans, setPlans]     = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);

  // Which plan's purchase is in flight, the message under the cards, and the confirmation that
  // replaces them once a payment has been verified.
  const [buying, setBuying]     = useState(null);
  const [payError, setPayError] = useState(null);
  const [bought, setBought]     = useState(null);

  // The cancellation: whether the confirmation is open, whether the call is in flight, what went
  // wrong if it did, and the note that confirms it afterwards.
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [cancelling, setCancelling]             = useState(false);
  const [cancelError, setCancelError]           = useState(null);
  const [cancelled, setCancelled]               = useState(false);

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

  /** The row behind the plan the reader is on, for its price and its real name. */
  const currentPlan = useMemo(
    () => plans.find(p => p.tier === currentTier) || null,
    [plans, currentTier],
  );

  /** Whether there is a paid plan here to manage at all. */
  const hasPaidPlan = isLoggedIn && !failed && currentTier !== 'FREE';

  /**
   * What cancelling actually takes away, read off the same table the page already compares with.
   *
   * Anything the reader's tier includes and Free does not. Generated rather than written out, so
   * it cannot drift from the comparison below it — or from the paywall, which is fed by the same
   * `from` values. Telling someone they will lose a feature they never had, or quietly not
   * mentioning one they will, is exactly the sort of thing that turns a cancellation into a
   * complaint.
   */
  const losing = useMemo(() => COMPARISON
    .filter(row => !row.value && includes(row, currentTier) && !includes(row, 'FREE'))
    .map(row => row.label), [currentTier]);

  const formatDate = (value) => (value
    ? new Date(value).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
    : null);

  /**
   * Stops the renewal.
   *
   * The server owns every consequence of this: it tells Razorpay to stop at cycle end, records the
   * intent, and keeps the tier granted until the period runs out. All that happens here is the
   * call, the refresh that makes the rest of the app agree, and saying so.
   */
  const doCancel = async () => {
    setCancelError(null);
    setCancelling(true);
    try {
      await cancelSubscription();
      // Everything gated in the app reads the context, and the plan is still live - so this is
      // what keeps the padlocks off until the period genuinely ends.
      await refresh();
      setConfirmingCancel(false);
      setCancelled(true);
    } catch (e) {
      setCancelError(e.message || 'The cancellation did not go through.');
    } finally {
      setCancelling(false);
    }
  };

  /**
   * Buys a plan.
   *
   * Three steps, in this order and no other: the server creates the Razorpay subscription and
   * tells us which key to open the window with; the reader pays inside Razorpay's own frame,
   * which is the only place their card details ever exist; the server verifies the signature on
   * what comes back and only then grants the plan. Nothing here decides that a payment succeeded
   * - this code cannot, and should not be able to.
   */
  const buy = async (plan) => {
    if (!isLoggedIn) {
      // A purchase has to belong to an account. Coming back here afterwards is the least a
      // reader expects after being interrupted.
      navigate('/login', { state: { from: `/subscribe${wanted ? `?feature=${wanted}` : ''}` } });
      return;
    }

    setPayError(null);
    setBuying(plan.id);

    try {
      await loadRazorpayCheckout();
      const order = await createSubscription(plan.id);

      const checkout = new window.Razorpay({
        key: order.razorpayKeyId,
        subscription_id: order.razorpaySubscriptionId,
        name: 'NiveshFlow',
        description: `${plan.displayName} plan`,
        prefill: { name: user?.username || '' },
        theme: { color: '#667eea' },

        /** Razorpay hands back the payment, the subscription and a signature over both. */
        handler: async (response) => {
          try {
            const result = await verifySubscription({
              razorpayPaymentId:      response.razorpay_payment_id,
              razorpaySubscriptionId: response.razorpay_subscription_id,
              razorpaySignature:      response.razorpay_signature,
            });
            // The locks across the app read from this context, so it is refreshed before the
            // reader goes anywhere: the page they were blocked on should simply work now.
            await refresh();
            setBought({ plan, until: result.currentPeriodEnd });
          } catch (e) {
            // Money may well have left their account; what failed is our confirmation of it.
            // Say so plainly rather than claiming the payment failed.
            setPayError(`We could not confirm the payment: ${e.message} `
                        + 'If you were charged, contact support with the payment id '
                        + `${response.razorpay_payment_id} and nothing will be lost.`);
          } finally {
            setBuying(null);
          }
        },

        modal: {
          // Closing the window is not a failure, so it gets no error message - only the button
          // back to its normal state.
          ondismiss: () => setBuying(null),
        },
      });

      checkout.on('payment.failed', (event) => {
        const reason = event?.error?.description || 'The payment did not go through.';
        setPayError(`${reason} Nothing has been charged — you can try again.`);
        setBuying(null);
      });

      checkout.open();
    } catch (e) {
      setPayError(e.message);
      setBuying(null);
    }
  };

  if (bought) {
    return (
      <div className="page-container sub-page">
        <div className="sub-done" role="status">
          <div className="sub-done-tick" aria-hidden="true">✓</div>
          <h1 className="page-title">You are on {bought.plan.displayName}</h1>
          <p className="sub-done-note">
            Your payment went through{bought.until && (
              <> and your plan runs to {new Date(bought.until).toLocaleDateString('en-IN', {
                day: 'numeric', month: 'short', year: 'numeric',
              })}</>
            )}. Everything it includes is unlocked now.
          </p>
          <Link className="sub-card-cta sub-done-cta" to="/watchlist">Go to my watchlist</Link>
        </div>
      </div>
    );
  }

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

      {/* The free plan needs no panel: there is nothing to manage and nothing to cancel, so one
          line is the honest size of the statement. */}
      {isLoggedIn && !failed && currentTier === 'FREE' && (
        <p className="sub-current-line">
          You are on the <strong>Free</strong> plan.
        </p>
      )}

      {hasPaidPlan && (
        <section className={`sub-plan-panel ${cancelAtPeriodEnd ? 'sub-plan-panel--ending' : ''}`}>
          <div className="sub-plan-main">
            <h2 className="sub-plan-heading">Your plan</h2>
            <p className="sub-plan-name">
              {currentPlan?.displayName || (currentTier === 'BASIC' ? 'Basic' : 'Advanced')}
              {!cancelAtPeriodEnd && currentPlan && (
                <span className="sub-plan-price">
                  {' · '}
                  {currentPlan.priceDisplay ?? `₹${Math.round((currentPlan.pricePaise ?? 0) / 100)}`}
                  {currentPlan.billingCycle === 'ANNUAL' ? '/year' : '/month'}
                </span>
              )}
            </p>

            {cancelAtPeriodEnd ? (
              <p className="sub-plan-status sub-plan-status--ending">
                <strong>Cancelled</strong> — this plan will not renew.
                {expiresAt && <> Everything it includes stays available until {formatDate(expiresAt)}.</>}
              </p>
            ) : (
              <p className="sub-plan-status">
                {expiresAt
                  ? <>Renews on {formatDate(expiresAt)}.</>
                  : <>Active.</>}
              </p>
            )}
          </div>

          <div className="sub-plan-actions">
            {cancelAtPeriodEnd ? (
              /* Not an "undo": Razorpay cannot revive a cancelled mandate, so coming back is a
                 fresh purchase through the ordinary checkout. Labelled for what it is. */
              <button
                className="sub-card-cta sub-plan-resume"
                type="button"
                onClick={() => currentPlan && buy(currentPlan)}
                disabled={buying !== null || !currentPlan}
              >
                {buying === currentPlan?.id ? 'Opening payment…' : 'Resubscribe'}
              </button>
            ) : (
              /* Deliberately quiet, and deliberately present. A cancel button that has to be
                 hunted for does not keep the customer - it turns the cancellation into a
                 chargeback, which costs the fee as well as the plan. */
              <button
                className="sub-plan-cancel"
                type="button"
                onClick={() => { setCancelError(null); setConfirmingCancel(true); }}
              >
                Cancel plan
              </button>
            )}
          </div>
        </section>
      )}

      {payError && (
        <div className="sub-pay-error" role="alert">{payError}</div>
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
                {isCurrent && !isSuggested && (
                  <div className={`sub-card-flag sub-card-flag--current
                                   ${cancelAtPeriodEnd ? 'sub-card-flag--ending' : ''}`}>
                    {cancelAtPeriodEnd ? 'Ending' : 'Your plan'}
                  </div>
                )}

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
                ) : isCurrent && cancelAtPeriodEnd ? (
                  // Their own plan, on its way out: the one action worth offering here is the one
                  // that keeps it. Same purchase as any other - see the panel above.
                  <button
                    className="sub-card-cta"
                    type="button"
                    onClick={() => buy(plan)}
                    disabled={buying !== null}
                  >
                    {buying === plan.id ? 'Opening payment…' : 'Resubscribe'}
                  </button>
                ) : isCurrent ? (
                  <div className="sub-card-cta sub-card-cta--muted">Your current plan</div>
                ) : (
                  <button
                    className="sub-card-cta"
                    type="button"
                    onClick={() => buy(plan)}
                    disabled={buying !== null}
                  >
                    {buying === plan.id
                      ? 'Opening payment…'
                      : currentTier === 'FREE'
                        ? `Get ${plan.displayName}`
                        : `Switch to ${plan.displayName}`}
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

      {/* Everything the reader needs to know is here, before they click - not in terms they would
          have to go and find. The no-refund line especially: a policy discovered afterwards is
          how a cancellation becomes a dispute. */}
      <Modal
        isOpen={confirmingCancel}
        onClose={() => { if (!cancelling) setConfirmingCancel(false); }}
        title={`Cancel your ${currentPlan?.displayName || currentTier} plan?`}
        maxWidth={480}
      >
        <p className="sub-cancel-lead">
          {expiresAt
            ? <>You keep everything this plan includes until <strong>{formatDate(expiresAt)}</strong>.
               After that your account returns to the free plan.</>
            : <>Your account will return to the free plan at the end of the period you have paid for.</>}
        </p>

        <ul className="sub-cancel-terms">
          <li>You will not be charged again.</li>
          <li>The remaining time is not refunded.</li>
          <li>Resubscribing later needs your card details again.</li>
        </ul>

        {losing.length > 0 && (
          <div className="sub-cancel-losing">
            <h3>What you will lose on {formatDate(expiresAt) || 'that date'}</h3>
            <ul>
              {losing.map(label => <li key={label}>{label}</li>)}
            </ul>
            <p className="sub-cancel-limits">
              Your watchlists drop to 1, with up to 5 companies in it. Watchlists above that limit
              are not deleted — you keep them if you subscribe again.
            </p>
          </div>
        )}

        {cancelError && <div className="sub-cancel-error" role="alert">{cancelError}</div>}

        <div className="sub-cancel-buttons">
          {/* "Keep" is the one that looks like the default, because staying is the safer of the
              two outcomes to click by accident. */}
          <button
            className="sub-cancel-keep"
            type="button"
            onClick={() => setConfirmingCancel(false)}
            disabled={cancelling}
          >
            Keep my plan
          </button>
          <button
            className="sub-cancel-go"
            type="button"
            onClick={doCancel}
            disabled={cancelling}
          >
            {cancelling ? 'Cancelling…' : 'Cancel the plan'}
          </button>
        </div>
      </Modal>

      <Toast
        message={expiresAt
          ? `Your plan is cancelled. You keep it until ${formatDate(expiresAt)}.`
          : 'Your plan is cancelled.'}
        visible={cancelled}
        onHide={() => setCancelled(false)}
      />
    </div>
  );
};

export default SubscribePage;
