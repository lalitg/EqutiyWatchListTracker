/**
 * Refund, Return & Cancellation Policy.
 *
 * This page has to agree word for word with the cancellation dialog in the app, which already
 * tells a user three things before they confirm: no further charge, no refund of the remaining
 * period, and card details needed again to resubscribe. A divergence between the two is the first
 * thing a payment dispute would turn on.
 *
 * The payment-error carve-out is deliberately narrow. It covers money taken for something not
 * delivered -- a double charge, or a charge where access never followed -- and nothing else. A
 * blanket refusal would not survive a chargeback anyway, and our own renewal handler can log a
 * charge that lands after a cancellation, so the policy has to allow for putting that right.
 */
const refundPolicy = {
  title: 'Refund, Return & Cancellation Policy',
  intro: 'How cancellation works, what happens to the time you have already paid for, and the '
       + 'narrow circumstances in which money is returned.',
  versioned: true,
  sections: [
    {
      id: 'cancellation',
      heading: '1. Cancellation Terms',
      blocks: [
        // Verbatim from the policy brief. Do not reword.
        { type: 'quote', text:
            'Users may cancel their subscription at any time via account settings. Cancellation '
          + 'stops future billing cycles.' },
        { type: 'p', text:
            'In NiveshFlow this is done from the [Plans](/subscribe) page in your account. '
          + 'Cancelling takes effect immediately in the sense that no further payment is taken.' },
      ],
    },
    {
      id: 'access',
      heading: '2. Access After Cancelling',
      blocks: [
        { type: 'p', text:
            '**You keep everything your plan includes until the end of the period you have already '
          + 'paid for.** On that date the account returns to the free plan. Cancelling does not '
          + 'cut access short, and it does not delete your watchlists.' },
        { type: 'p', text:
            'Because a cancelled mandate cannot be restarted, resubscribing later is a fresh '
          + 'purchase and will ask for your payment details again.' },
      ],
    },
    {
      id: 'no-refund',
      heading: '3. Refund Eligibility',
      blocks: [
        { type: 'h3', text: 'No refund policy' },
        // Verbatim from the policy brief. Do not reword.
        { type: 'quote', text:
            'Subscription fees are non-refundable once content access is granted.' },
        { type: 'p', text:
            'The unused remainder of a billing period is not refunded when you cancel, and no '
          + 'partial or pro-rata refund is given when you move to a cheaper plan.' },

        { type: 'h3', text: 'Payment errors' },
        { type: 'p', text:
            'This is the one exception. Where a payment has been taken in error — the same '
          + 'subscription charged more than once, or a charge taken without access being granted '
          + '— the amount taken in error is **refunded in full** to the original payment '
          + 'method, normally within 5–7 working days of the error being confirmed.' },
        { type: 'p', text:
            'If you think you have been charged in error, contact us with the payment reference '
          + 'and we will investigate. See [Contact Us](/contact).' },
      ],
    },
    {
      id: 'interruptions',
      heading: '4. Service Interruptions',
      blocks: [
        { type: 'p', text:
            'The service may occasionally be unavailable because of server downtime, scheduled '
          + 'maintenance, or problems at the third parties who supply our market data. Temporary '
          + 'interruptions of this kind do not automatically warrant a full refund.' },
        { type: 'p', text:
            'Where an outage is prolonged and affects a significant part of a billing period, we '
          + 'will consider the circumstances on a case-by-case basis.' },
      ],
    },
  ],
};

export default refundPolicy;
