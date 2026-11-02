import about from './about';
import contact from './contact';
import disclaimer from './disclaimer';
import privacy from './privacy';
import refundPolicy from './refundPolicy';
import terms from './terms';

/**
 * Every policy document, keyed by the route it is served at.
 *
 * <p>One map rather than six imports in the router means adding a page is one line here and one
 * route, and the page components themselves no longer exist to be forgotten.
 */
export const DOCUMENTS = {
  '/terms': terms,
  '/refund-policy': refundPolicy,
  '/privacy': privacy,
  '/disclaimer': disclaimer,
  '/about': about,
  '/contact': contact,
};

export { about, contact, disclaimer, privacy, refundPolicy, terms };
export default DOCUMENTS;
