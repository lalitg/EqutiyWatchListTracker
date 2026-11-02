/**
 * The Razorpay checkout script, loaded when somebody actually wants to pay.
 *
 * Not in index.html: every reader would then fetch a third-party script on every page load,
 * including the ones who never reach the pricing page. It is injected on first use and the same
 * promise is handed to every later caller, so a double click cannot start two loads.
 */

const SRC = 'https://checkout.razorpay.com/v1/checkout.js';

let loader = null;

/**
 * @returns {Promise<void>} resolves once window.Razorpay exists
 * @throws if the script cannot be reached — an ad blocker, or no network
 */
export function loadRazorpayCheckout() {
  if (window.Razorpay) return Promise.resolve();
  if (loader) return loader;

  loader = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = SRC;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      // Cleared so a later attempt can try again rather than reusing a rejected promise for the
      // rest of the session - the reader may simply have been offline for a moment.
      loader = null;
      reject(new Error('Could not load the payment window. Check your connection and try again.'));
    };
    document.body.appendChild(script);
  });

  return loader;
}
