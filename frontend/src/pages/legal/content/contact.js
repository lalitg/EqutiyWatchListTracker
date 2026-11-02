import { BUSINESS, CONTACT_EMAIL, GRIEVANCE } from '../policyMeta';

/**
 * Contact Us.
 *
 * Email only, by decision: no telephone number is published. Worth knowing at merchant
 * onboarding -- the gateway's guidance asks for a contact number alongside the email and address,
 * so an email-only page may clear automated validation and still be queried in manual review.
 * Adding a number later is one value in policyMeta plus one row here.
 *
 * Not versioned, for the same reason as About: it is contact information, not a policy anybody
 * agreed to.
 */
const contact = {
  title: 'Contact Us',
  intro: 'How to reach us about your account, a payment, or anything you have read on the site.',
  versioned: false,
  sections: [
    {
      id: 'support',
      heading: 'Support',
      blocks: [
        { type: 'dl', rows: [
          ['Email', `[${CONTACT_EMAIL}](mailto:${CONTACT_EMAIL})`],
        ] },
        { type: 'p', text:
            'We answer email enquiries as quickly as we can, normally within two working days. '
          + 'When writing about a payment, please include the payment reference shown on your '
          + 'receipt so we can find the transaction.' },
      ],
    },
    {
      id: 'grievance',
      heading: 'Grievance Officer',
      blocks: [
        { type: 'p', text:
            'For complaints that have not been resolved through support, you can write to our '
          + 'Grievance Officer, appointed under the Consumer Protection (E-Commerce) Rules, '
          + '2020.' },
        { type: 'dl', rows: [
          ['Name', GRIEVANCE.name],
          ['Designation', GRIEVANCE.designation],
          ['Email', `[${GRIEVANCE.email}](mailto:${GRIEVANCE.email})`],
        ] },
        { type: 'p', text:
            `Complaints are acknowledged within ${GRIEVANCE.acknowledgeHours} hours and resolved `
          + `within ${GRIEVANCE.resolveDays} days.` },
      ],
    },
    {
      id: 'business',
      heading: 'Business Details',
      blocks: [
        { type: 'dl', rows: [
          ['Legal name', BUSINESS.legalName],
          ['Registered address', BUSINESS.address],
        ] },
      ],
    },
    {
      id: 'elsewhere',
      heading: 'Before You Write',
      blocks: [
        { type: 'p', text:
            'Cancellations and plan changes are handled from the [Plans](/subscribe) page in your '
          + 'account and do not need an email. How refunds work is set out in the '
          + '[Refund & Cancellation Policy](/refund-policy).' },
        { type: 'p', text:
            'We cannot answer questions asking whether to buy or sell a particular security. '
          + 'NiveshFlow is an educational service and does not give investment advice — see '
          + 'our [Disclaimer](/disclaimer).' },
      ],
    },
  ],
};

export default contact;
