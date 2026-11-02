/**
 * One source of truth for the policy pages.
 *
 * <p>{@link POLICY_VERSION} is the keystone. It is shown on every policy page, sent to the server
 * when somebody subscribes, and stored against their payment. Without it the business can prove
 * that a user consented but not <em>what</em> they consented to, which is worth very little in a
 * dispute. Bump it whenever any policy page changes materially — and bump the matching
 * {@code policy.current-version} in <em>both</em> payment-service and main-api-service in the same
 * change. main-api is the application that actually runs; payment-service is bundled into it as a
 * library and its own properties file is never loaded, so setting only one of them silently
 * disables the check.
 */

/** Bump together with policy.current-version in payment-service AND main-api-service. */
export const POLICY_VERSION = '2026-11-01';

/** Shown on each page so a reader can tell which revision they are looking at. */
export const EFFECTIVE_DATE = '1 November 2026';

export const CONTACT_EMAIL = 'lalitgera591985@gmail.com';

/**
 * Required on a consumer-facing site under the Consumer Protection (E-Commerce) Rules, 2020,
 * which have been in force since 23 July 2020.
 */
export const GRIEVANCE = {
  name: 'Lalit Gera',
  designation: 'Grievance Officer',
  email: CONTACT_EMAIL,
  acknowledgeHours: 48,
  resolveDays: 30,
};

/** Registered name and address, also required by those rules. */
export const BUSINESS = {
  legalName: 'Lalit Gera, trading as "NiveshFlow"',
  address: 'Gurugram, Haryana',
};

/**
 * The standing risk line, shown on every screen.
 *
 * <p>One sentence, kept here rather than in the banner component so the wording cannot drift from
 * the policy pages that say the same thing at greater length.
 */
export const RISK_LINE =
  'Equity investments are subject to market risks. Information provided by NiveshFlow is for '
  + 'educational purposes only and is not SEBI-registered investment advice.';
