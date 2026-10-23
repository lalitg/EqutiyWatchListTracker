import React from 'react';
import { Link } from 'react-router-dom';
import { useEntitlements } from '../../context/EntitlementsContext';
import './Locked.css';

/**
 * The paywall, as the reader meets it.
 *
 * <p>Three shapes, one rule: show what is behind the lock, do not pretend it is not there.
 *
 * <ul>
 *   <li>{@link Locked} — a section the reader can see but not use: the real content, blurred, with
 *       a card over it.</li>
 *   <li>{@link LockBadge} — a small padlock where a single value would be, in a table cell or
 *       beside a tab.</li>
 *   <li>{@link LockedNote} — a line under a shortened list saying what the rest of it costs.</li>
 * </ul>
 *
 * <p>Every one of them leads to the same place: /subscribe, carrying the feature that sent them, so
 * the pricing page can point at the plan that answers the question the reader just asked.
 *
 * <p>This is presentation, not enforcement. A determined reader can still read a blurred payload in
 * the network tab — the gate that matters is the one on the server, and it comes later. What this
 * buys now is an honest shop window: the reader learns what exists and what it costs.
 */

/** Where a lock sends the reader, tagged with what they were trying to reach. */
export function subscribeHref(feature) {
  return feature ? `/subscribe?feature=${encodeURIComponent(feature)}` : '/subscribe';
}

/**
 * A section the reader cannot use yet.
 *
 * Renders children untouched when the feature is open, so unlocked pages carry no extra markup and
 * no extra wrapper to fight with the layout.
 *
 * @param {?string} feature  key from FEATURES, or null when this section is not gated
 * @param {string}  title    what is behind the lock, in the reader's words
 * @param {string}  note     one line on why it is worth having
 * @param {string}  cta      button text
 * @param {boolean} compact  smaller card, for a panel rather than a page
 */
export function Locked({ feature, title, note, cta = 'See plans', compact = false, children }) {
  const { can } = useEntitlements();

  // A null feature is not a locked feature: it lets a caller whose gating depends on its own state
  // - one tab paid, another free - use the same wrapper either way.
  if (!feature || can(feature)) return <>{children}</>;

  return (
    <div className={`nf-locked ${compact ? 'nf-locked--compact' : ''}`}>
      {/* aria-hidden and inert together: a screen reader should not read out content the page is
          refusing to show, and Tab should not land on a button hidden behind the blur. inert is a
          React 19 prop; the CSS blocks the mouse separately, because a click must not reach a
          control the reader cannot see well enough to aim at. */}
      <div className="nf-locked-behind" aria-hidden="true" inert>
        {children}
      </div>

      <div className="nf-locked-card" role="note">
        <div className="nf-locked-icon" aria-hidden="true">🔒</div>
        <h3 className="nf-locked-title">{title}</h3>
        {note && <p className="nf-locked-note">{note}</p>}
        <Link className="nf-locked-cta" to={subscribeHref(feature)}>{cta}</Link>
      </div>
    </div>
  );
}

/**
 * A padlock standing in for one value.
 *
 * Used where a card would not fit: a sentiment cell in a table of thirty rows, or the label of a
 * tab. It is a link rather than a bare icon so the reader can act on it where they met it.
 *
 * @param {string} feature key from FEATURES
 * @param {string} label   optional text beside the padlock
 * @param {string} title   tooltip; defaults to a plain explanation
 */
export function LockBadge({ feature, label, title }) {
  return (
    <Link
      className="nf-lock-badge"
      to={subscribeHref(feature)}
      title={title || 'Included in a paid plan — click to see plans'}
      onClick={(e) => e.stopPropagation()}   // the row underneath navigates somewhere else
    >
      <span aria-hidden="true">🔒</span>
      {label && <span className="nf-lock-badge-label">{label}</span>}
    </Link>
  );
}

/**
 * The line that closes a shortened list.
 *
 * Shown where free readers get a sample rather than nothing — a teaser only works if it says it is
 * one, otherwise five headlines look like all the news there is.
 */
export function LockedNote({ feature, children }) {
  return (
    <p className="nf-locked-inline" role="note">
      <span aria-hidden="true">🔒 </span>
      {children}{' '}
      <Link to={subscribeHref(feature)}>See plans</Link>
    </p>
  );
}

export default Locked;
