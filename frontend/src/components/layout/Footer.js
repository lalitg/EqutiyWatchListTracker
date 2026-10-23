import React from 'react';
import './Footer.css';

/**
 * The regulatory disclaimer, on every page.
 *
 * <p>Rendered once in the app shell, outside the router, so it survives navigation and cannot be
 * forgotten on a new page — which is the whole point of a disclaimer. A reader must not be able to
 * reach a screen of ours where it is absent.
 *
 * <p>Static rather than a fixed bar: at seventy words a pinned version would take a tenth of a
 * laptop screen and a third of a phone's, on every page, for text that is read once. It sits at the
 * end of the content instead, and the app shell's flex column keeps it at the bottom of the
 * viewport even on short pages like login.
 *
 * <p>The wording is fixed by compliance, not by us. If it needs to change, change it here and it
 * changes everywhere.
 */
const Footer = () => (
  <footer className="nf-footer" role="contentinfo">
    <p className="nf-footer-text">
      <strong>Disclaimer:</strong> The content, tools, and information provided on NiveshFlow are
      strictly for educational and informational purposes only and do not constitute financial,
      legal, or tax advice. NiveshFlow is not a SEBI-registered Investment Adviser (IA) or Research
      Analyst (RA). Users should conduct their own research or consult a qualified financial
      professional before making any investment decisions. Investments in the securities market are
      subject to market risks; read all related documents carefully before investing.
    </p>
  </footer>
);

export default Footer;
