import React from 'react';
import { Link } from 'react-router-dom';
import { EFFECTIVE_DATE, POLICY_VERSION } from './policyMeta';
import './LegalPage.css';

/**
 * The shell every policy page is drawn in, and the renderer that turns a content module into it.
 *
 * <p>One shell rather than seven layouts means the effective date and version stamp cannot be
 * forgotten on the one page somebody adds in a hurry, and a reader who has seen one of these
 * pages already knows how to read the rest.
 *
 * <p>The date stamp is not decoration. When a charge is disputed, the question is what the policy
 * said on the day the customer agreed to it, and a page that does not say which revision it is
 * cannot answer that.
 *
 * <h2>Why the prose lives in ./content and not here</h2>
 * Legal wording changes on a different clock from layout, and is reviewed by different people.
 * Keeping it in plain data modules means editing it never involves touching JSX, and the markup
 * below is written and tested once instead of six times. It stays in version control either way,
 * which is what gives a dated record of what each policy said on any given day.
 */

// ─── inline formatting ──────────────────────────────────────────────────────

/**
 * Two rules, because across six pages only two are ever used inside a sentence:
 * {@code **bold**} and {@code [text](/path)}.
 *
 * <p>A link is rendered as a react-router {@code Link} when it points inside the app, so
 * navigation stays client-side; a {@code mailto:} or external target falls back to a plain anchor.
 * Writing this by hand rather than reaching for a Markdown renderer avoids a dependency, and
 * avoids the override that one would need anyway to stop every internal link reloading the page.
 */
const INLINE = /(\*\*[^*]+\*\*|\[[^\]]+\]\([^)]+\))/g;

export function formatInline(text) {
  return String(text).split(INLINE).map((piece, i) => {
    if (!piece) return null;

    const bold = /^\*\*([^*]+)\*\*$/.exec(piece);
    if (bold) return <strong key={i}>{bold[1]}</strong>;

    const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(piece);
    if (link) {
      const [, label, href] = link;
      return href.startsWith('/')
        ? <Link key={i} to={href}>{label}</Link>
        : <a key={i} href={href}>{label}</a>;
    }
    return <React.Fragment key={i}>{piece}</React.Fragment>;
  });
}

// ─── blocks ─────────────────────────────────────────────────────────────────

/**
 * The five shapes the policy pages actually use. Deliberately not a general-purpose document
 * model: anything these pages do not contain is better added when something needs it than
 * guessed at now.
 */
function Block({ block, index }) {
  switch (block.type) {
    case 'h3':
      return <h3>{formatInline(block.text)}</h3>;

    // A clause reproduced word for word. Set apart so it is visibly a quotation and harder to
    // reword by accident.
    case 'quote':
      return <p className="legal-quote">{formatInline(block.text)}</p>;

    case 'ul':
      return (
        <ul>
          {block.items.map((item, i) => <li key={i}>{formatInline(item)}</li>)}
        </ul>
      );

    // Facts rather than prose: contact rows, business details.
    case 'dl':
      return (
        <dl className="legal-contact-block">
          {block.rows.map(([term, value], i) => (
            <React.Fragment key={i}>
              <dt>{term}</dt>
              <dd>{formatInline(value)}</dd>
            </React.Fragment>
          ))}
        </dl>
      );

    case 'p':
    default:
      return <p>{formatInline(block.text)}</p>;
  }
}

export const Section = ({ id, heading, children }) => (
  <section className="legal-section" id={id}>
    <h2>{heading}</h2>
    {children}
  </section>
);

export const LegalPage = ({ title, intro, children, showStamp = true }) => (
  <div className="page-container legal-page">
    <header className="legal-header">
      <h1 className="page-title">{title}</h1>
      {showStamp && (
        <p className="legal-stamp">
          Effective {EFFECTIVE_DATE} · Version {POLICY_VERSION}
        </p>
      )}
      {intro && <p className="legal-intro">{intro}</p>}
    </header>

    <div className="legal-body">{children}</div>

    <nav className="legal-nav" aria-label="Other policies">
      <Link to="/terms">Terms of Service</Link>
      <Link to="/refund-policy">Refund &amp; Cancellation</Link>
      <Link to="/privacy">Privacy Policy</Link>
      <Link to="/disclaimer">Disclaimer</Link>
      <Link to="/contact">Contact</Link>
    </nav>
  </div>
);

/** Draws a whole document from ./content. */
export const PolicyDocument = ({ doc }) => (
  <LegalPage title={doc.title} intro={doc.intro} showStamp={doc.versioned !== false}>
    {doc.sections.map((section) => (
      <Section key={section.id} id={section.id} heading={section.heading}>
        {section.blocks.map((block, i) => <Block key={i} block={block} index={i} />)}
      </Section>
    ))}
  </LegalPage>
);

export default LegalPage;
