import React from 'react';
import { Link } from 'react-router-dom';
import { RISK_LINE } from '../../pages/legal/policyMeta';
import './RiskBanner.css';

/**
 * The standing market-risk line, pinned to the bottom of the viewport on every screen.
 *
 * <p>Pinned rather than placed in the footer, because the point of this one is to be visible
 * without scrolling: a reader looking at a sentiment score should be able to see it at that
 * moment, not after they reach the end of the page. The longer disclaimer in the footer stays
 * where it is and says the same thing at greater length.
 *
 * <p>Kept to a single line precisely so pinning is affordable. The footer disclaimer runs to
 * seventy words and would take a third of a phone screen if it were pinned; this is one sentence
 * and costs a strip. {@code .App} reserves the height so the banner never covers page content.
 *
 * <p>Not dismissible, on purpose. A risk warning the reader can close is a risk warning that is
 * absent from most sessions, which defeats the reason for showing it at all.
 */
const RiskBanner = () => (
  <aside className="nf-risk-banner" role="note" aria-label="Market risk disclaimer">
    <p>
      {RISK_LINE}{' '}
      <Link to="/disclaimer">Read the full disclaimer</Link>
    </p>
  </aside>
);

export default RiskBanner;
