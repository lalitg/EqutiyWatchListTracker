/**
 * Terms of Service & Subscription Agreement.
 *
 * The Non-Advisory Clarification and the Account Usage restrictions are reproduced word for word
 * from the policy brief. The scope and the cancellation sentence were corrected against the
 * product: cancellation happens on the Plans page, not on a settings screen, and describing a
 * screen that does not exist is worse than describing an unusual location.
 */
const terms = {
  title: 'Terms of Service & Subscription Agreement',
  intro: 'These terms form the agreement between you and NiveshFlow when you create an account or '
       + 'take out a paid plan. Please read them alongside our Refund & Cancellation Policy, '
       + 'Privacy Policy and Disclaimer.',
  versioned: true,
  sections: [
    {
      id: 'scope',
      heading: '1. Scope of Service',
      blocks: [
        { type: 'p', text:
            'NiveshFlow provides equity market and individual company news, machine-generated '
          + 'sentiment analysis, watchlists, an events and holiday calendar, and mutual fund '
          + 'documents, offered on tiered plans. The service is provided for **educational and '
          + 'informational purposes only**.' },
      ],
    },
    {
      id: 'non-advisory',
      heading: '2. Non-Advisory Clarification',
      blocks: [
        // Verbatim from the policy brief. Do not reword.
        { type: 'quote', text:
            'The Subscription Fee grants access to educational media and analytical software tools '
          + 'only. Payment does not entitle the subscriber to personalised financial advice, trade '
          + 'signals, or buy/sell recommendations.' },
        { type: 'p', text:
            'Nothing on NiveshFlow should be read as a recommendation to buy, sell or hold any '
          + 'security. See our [Disclaimer](/disclaimer) for the full position, including our '
          + 'regulatory status.' },
      ],
    },
    {
      id: 'eligibility',
      heading: '3. Eligibility',
      blocks: [
        { type: 'p', text:
            'You must be at least 18 years old and competent to contract under the Indian Contract '
          + 'Act, 1872. By creating an account you confirm that you meet these conditions and that '
          + 'the information you give us is accurate.' },
      ],
    },
    {
      id: 'billing',
      heading: '4. Billing & Auto-Renewal',
      blocks: [
        { type: 'p', text:
            'Paid plans are billed **monthly and recur automatically** until cancelled. The price '
          + 'of your plan is shown on the [Plans](/subscribe) page before you subscribe and on the '
          + 'payment screen before you pay.' },
        { type: 'p', text:
            'Recurring payments are collected through an electronic mandate set up with our '
          + 'payment gateway. You will receive a notification from the gateway before each renewal '
          + 'is charged, as required for recurring card and UPI mandates in India.' },
        { type: 'p', text:
            'You may cancel at any time from the [Plans](/subscribe) page in your account. '
          + 'Cancelling stops future billing; access continues until the end of the period you '
          + 'have already paid for. See the [Refund & Cancellation Policy](/refund-policy) for '
          + 'details.' },
      ],
    },
    {
      id: 'usage',
      heading: '5. Account Usage & Restrictions',
      blocks: [
        { type: 'p', text: 'Your account is personal to you. You are prohibited from:' },
        { type: 'ul', items: [
          'sharing your login credentials with any other person;',
          'redistributing premium educational material obtained through a paid plan;',
          'selling, sub-licensing or otherwise supplying our content to third parties.',
        ] },
        { type: 'p', text:
            'You also agree not to scrape the service, circumvent plan limits, or attempt to '
          + 'access features your plan does not include.' },
      ],
    },
    {
      id: 'suspension',
      heading: '6. Suspension & Termination',
      blocks: [
        { type: 'p', text:
            'We may suspend or close an account that breaches these terms, in particular where '
          + 'login credentials are shared or paid content is redistributed. Where the breach is '
          + 'not serious we will normally contact you first. No refund is due on an account closed '
          + 'for breach.' },
        { type: 'p', text:
            'You may close your account at any time. Closing your account cancels any active plan '
          + 'under the terms set out in the Refund & Cancellation Policy.' },
      ],
    },
    {
      id: 'third-party',
      heading: '7. Third-Party Links and Content',
      blocks: [
        { type: 'p', text:
            'The service displays news, filings and documents drawn from exchanges, regulators, '
          + 'news publishers and other external sources, and links out to them. NiveshFlow does '
          + 'not monitor, endorse, guarantee or assume responsibility for the accuracy, security '
          + 'or practices of those external websites. Following any third-party link is done at '
          + 'your own discretion.' },
      ],
    },
    {
      id: 'liability',
      heading: '8. Limitation of Liability',
      blocks: [
        { type: 'p', text:
            'The service is provided **“as is” and “as available”** without '
          + 'warranty of any kind. We do not warrant that the content is accurate, complete, '
          + 'timely or uninterrupted, and market data and news are obtained from third parties.' },
        { type: 'p', text:
            'Under no circumstances shall NiveshFlow, its founder(s), developers or affiliates be '
          + 'liable for any direct, indirect, incidental, consequential, special or exemplary '
          + 'damages — including financial losses, loss of profits, loss of data or trading '
          + 'losses — arising out of or in connection with your use of, or inability to use, '
          + 'the platform, or your reliance on any information provided through it. You use the '
          + 'platform entirely at your own risk.' },
        { type: 'p', text:
            'Nothing in these terms limits any liability that cannot be limited under applicable '
          + 'law. Subject to that, our total monetary liability in connection with the service is '
          + 'limited to the subscription fees you paid in the three months before the claim '
          + 'arose.' },
        { type: 'p', text:
            'See our [Disclaimer](/disclaimer) for the full position on data accuracy and '
          + 'investment risk.' },
      ],
    },
  ],
};

export default terms;
