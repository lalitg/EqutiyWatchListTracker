import { CONTACT_EMAIL, GRIEVANCE } from '../policyMeta';

/**
 * Privacy Policy & Payment Data Disclosures.
 *
 * The payment gateway paragraph is reproduced word for word from the policy brief, and is
 * accurate: card and UPI details are entered inside the gateway's own frame and never reach our
 * servers. The data inventory below was written against the actual schema rather than the brief's
 * summary, which listed only email, phone, transactions and billing -- a privacy notice that
 * understates what is collected is worse than none.
 *
 * The Grievance Officer block is required on a consumer-facing site by the Consumer Protection
 * (E-Commerce) Rules, 2020. The itemised purpose notice, retention schedule and data-principal
 * rights that the DPDP Rules require from 13 May 2027 are deliberately not here yet.
 */
const privacy = {
  title: 'Privacy Policy & Payment Data Disclosures',
  intro: 'What we collect, who processes it on our behalf, and what we deliberately do not do '
       + 'with it.',
  versioned: true,
  sections: [
    {
      id: 'collected',
      heading: '1. Data We Collect',
      blocks: [
        { type: 'p', text: 'When you use NiveshFlow we hold the following.' },

        { type: 'h3', text: 'Account details' },
        { type: 'ul', items: [
          'Your name and username',
          'Email address, and whether it has been verified',
          'Phone number',
          'Your password, stored only as a one-way bcrypt hash — never in readable form',
          'The investment experience, investment amount band and investor category you choose '
          + 'when registering',
        ] },

        { type: 'h3', text: 'How you use the service' },
        { type: 'ul', items: [
          'Your watchlists and the companies on them',
          'The companies you have subscribed to for news alerts',
          'Notifications generated for you, including the headline, article link and sentiment '
          + 'score',
        ] },

        { type: 'h3', text: 'Billing' },
        { type: 'ul', items: [
          'Your plan, its status, and the current billing period',
          'Transaction history: amounts, dates, and the reference identifiers our payment gateway '
          + 'returns for each payment and subscription',
        ] },
      ],
    },
    {
      id: 'payments',
      heading: '2. Payment Gateway Disclaimer',
      blocks: [
        // Verbatim from the policy brief. Do not reword.
        { type: 'quote', text:
            'Sensitive payment information (UPI IDs, card details, banking credentials) is '
          + 'processed securely via PCI-DSS compliant third-party payment gateways and is not '
          + 'stored on NiveshFlow servers.' },
        { type: 'p', text:
            'Payment details are entered inside the gateway’s own secure window. We receive '
          + 'only the reference identifiers needed to confirm that a payment succeeded and to '
          + 'match later renewals to your subscription.' },
      ],
    },
    {
      id: 'processors',
      heading: '3. Who Processes Data On Our Behalf',
      blocks: [
        { type: 'ul', items: [
          '**Razorpay** — payment processing and recurring mandates.',
          '**Amazon Web Services (Simple Email Service), Mumbai region** — delivery of '
          + 'account email such as verification, password resets and news alerts. Email is sent '
          + 'from infrastructure located in India.',
        ] },
        { type: 'p', text: 'We do not sell, rent or trade your personal data with anyone.' },
      ],
    },
    {
      id: 'not-doing',
      heading: '4. What We Do Not Do',
      blocks: [
        { type: 'p', text:
            'NiveshFlow sets **no cookies**. Your session is kept in your own browser’s local '
          + 'storage and is cleared when you sign out.' },
        { type: 'p', text:
            'We run **no analytics, advertising or tracking services** of any kind. There are no '
          + 'third-party trackers, no advertising identifiers, and no profiling of your behaviour '
          + 'for marketing.' },
      ],
    },
    {
      id: 'external',
      heading: '5. Links To Other Sites',
      blocks: [
        { type: 'p', text:
            'News headlines, regulatory filings and mutual fund documents link out to exchanges, '
          + 'regulators, news publishers and other external sites. Once you follow such a link '
          + 'you are on a site we do not operate, and this policy no longer applies — their '
          + 'own privacy practices do. We do not share your personal data with any of them, and '
          + 'following a link tells us nothing about you.' },
      ],
    },
    {
      id: 'security',
      heading: '6. Security',
      blocks: [
        { type: 'p', text:
            'Traffic between your browser and NiveshFlow is encrypted in transit. Passwords are '
          + 'stored as bcrypt hashes and cannot be recovered or read by us, which is why a '
          + 'forgotten password is reset rather than retrieved. Access to production data is '
          + 'restricted to those who need it to operate the service.' },
      ],
    },
    {
      id: 'children',
      heading: '7. Children',
      blocks: [
        { type: 'p', text:
            'NiveshFlow is not directed at children. Accounts require you to be at least 18, and '
          + 'we do not knowingly collect personal data from anyone under that age. If you believe '
          + 'a child has created an account, contact us and we will remove it.' },
      ],
    },
    {
      id: 'grievance',
      heading: '8. Grievance Officer',
      blocks: [
        { type: 'p', text:
            'In accordance with the Consumer Protection (E-Commerce) Rules, 2020, the contact '
          + 'details of our Grievance Officer are set out below.' },
        { type: 'dl', rows: [
          ['Name', GRIEVANCE.name],
          ['Designation', GRIEVANCE.designation],
          ['Email', `[${GRIEVANCE.email}](mailto:${GRIEVANCE.email})`],
        ] },
        { type: 'p', text:
            `We acknowledge complaints within ${GRIEVANCE.acknowledgeHours} hours of receipt and `
          + `aim to resolve them within ${GRIEVANCE.resolveDays} days. General enquiries can go to `
          + `[${CONTACT_EMAIL}](mailto:${CONTACT_EMAIL}); see also our [Contact](/contact) page.` },
      ],
    },
  ],
};

export default privacy;
