import { BUSINESS, CONTACT_EMAIL } from '../policyMeta';

/**
 * About Us.
 *
 * Required by the payment gateway during merchant onboarding, and by the Consumer Protection
 * (E-Commerce) Rules, which want a legal name and address on a consumer-facing site.
 *
 * The copy is the supplied text. The company block below it is fact rather than voice, so it
 * stays as a definition list in the same shape as the one on the Contact page.
 *
 * Not versioned: this is an informational page, not a policy anybody agrees to, so stamping it
 * with a version would imply a document that had been consented to.
 */
const about = {
  title: 'About NiveshFlow',
  intro: 'A comprehensive equity intelligence and financial news ecosystem designed for global '
       + 'markets.',
  versioned: false,
  sections: [
    {
      id: 'welcome',
      heading: 'Welcome to NiveshFlow',
      blocks: [
        { type: 'p', text:
            'Built at the intersection of robust backend engineering and deep financial insights, '
          + 'NiveshFlow serves retail investors, market enthusiasts, and finance professionals '
          + 'with data-driven market clarity.' },
        { type: 'p', text:
            'We track, analyse, and present real-time financial updates spanning global '
          + 'macro-economies, domestic indices, individual corporate entities, and mutual fund '
          + 'performances.' },
      ],
    },
    {
      id: 'what-we-do',
      heading: 'What We Do',
      blocks: [
        { type: 'ul', items: [
          '**Global & domestic insights:** unified tracking of index movements, domestic market '
          + 'extremes, and key macroeconomic news.',
          '**Equity & sentiment intelligence:** advanced processing of market news sentiments to '
          + 'help you understand the core market drivers behind the tickers.',
          '**Mutual fund tracking:** up-to-date AMFI data, tracking sheets, and performance '
          + 'shifts simplified for the modern investor.',
          '**Event calendars:** never miss a market shift with our active domestic corporate and '
          + 'holiday tracking infrastructure.',
        ] },
      ],
    },
    {
      id: 'engineering',
      heading: 'Engineered for Fault-Tolerant Accuracy',
      blocks: [
        { type: 'p', text:
            'As software engineers and equity investors, we understand that financial data '
          + 'demands low latency and dependable delivery. NiveshFlow is architected on a modular '
          + 'multi-service backend, operating on a high-availability ingestion pipeline. Our '
          + 'engineering prioritises a data-deduplication engine and fault-tolerant architecture '
          + 'so that the news, metrics, and stock intelligence you see are delivered accurately '
          + 'and consistently.' },
        { type: 'p', text:
            'The data itself comes from exchanges, regulators and third-party feeds, and is '
          + 'presented as we receive it — see our [Disclaimer](/disclaimer) for what that '
          + 'means in practice.' },
      ],
    },
    {
      id: 'mission',
      heading: 'Our Mission',
      blocks: [
        { type: 'p', text:
            'To eliminate the noise in retail investing. We believe financial information should '
          + 'not be hidden behind dense terminology or slow interfaces. NiveshFlow is built to '
          + 'make systemic financial tracking clean, fast, and accessible to everyone aiming to '
          + 'build long-term wealth.' },
        { type: 'p', text:
            'Everything we publish is for education and information. NiveshFlow is not a '
          + 'SEBI-registered Investment Adviser or Research Analyst and does not give investment '
          + 'advice.' },
      ],
    },
    {
      id: 'company',
      heading: 'Business Information',
      blocks: [
        { type: 'dl', rows: [
          ['Legal name', BUSINESS.legalName],
          ['Registered address', BUSINESS.address],
          ['Email', `[${CONTACT_EMAIL}](mailto:${CONTACT_EMAIL})`],
        ] },
        { type: 'p', text:
            'Questions, feedback and complaints are all welcome — see '
          + '[Contact Us](/contact).' },
      ],
    },
  ],
};

export default about;
