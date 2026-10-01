import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useSearchParams } from 'react-router-dom';
import { sendVerificationEmail, verifyEmail } from '../services/userService';
import './LoginPage.css';

/**
 * Where the emailed verification link lands:
 *   https://niveshflow.com/verify-email?token=<one-time token>
 *
 * The page verifies on arrival rather than asking the reader to press anything. They already acted
 * by clicking the link in their inbox; a second button would only be a chance to abandon the flow.
 *
 * Four states, because a failed verification has two very different causes and the reader can only
 * act on one of them: an expired link is fixed by asking for another, while a missing token means
 * the link was mangled in transit and a fresh one is the only way forward.
 */
const VerifyEmailPage = () => {
  const [params] = useSearchParams();
  const token = params.get('token') || '';

  const [status, setStatus] = useState(token ? 'verifying' : 'no-token');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [resendState, setResendState] = useState('idle');   // idle | sending | sent | failed
  const [resendMessage, setResendMessage] = useState('');

  // React runs effects twice in development's strict mode. Without this guard the token would be
  // redeemed by the first run and then reported invalid by the second — the page would show a
  // failure for a link that actually worked.
  const attempted = useRef(false);

  useEffect(() => {
    if (!token || attempted.current) return;
    attempted.current = true;

    verifyEmail(token)
      .then(result => {
        setEmail(result.email || '');
        setStatus('verified');
      })
      .catch(err => {
        setError(err.message);
        setStatus('failed');
      });
  }, [token]);

  const resend = useCallback(async () => {
    setResendState('sending');
    try {
      const result = await sendVerificationEmail();
      setResendMessage(result.message || 'Verification email sent.');
      setResendState(result.sent ? 'sent' : 'failed');
    } catch (err) {
      setResendMessage(err.message);
      setResendState('failed');
    }
  }, []);

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">Nivesh Flow</div>

        {status === 'verifying' && (
          <p className="login-hint" style={{ marginBottom: 16 }}>
            Confirming your email address…
          </p>
        )}

        {status === 'verified' && (
          <>
            <h2 style={{ fontSize: 18, margin: '0 0 8px', color: '#15803d' }}>
              Email confirmed
            </h2>
            <p className="login-hint" style={{ marginBottom: 16 }}>
              {email ? `${email} is verified.` : 'Your address is verified.'} You can now receive
              news alerts for the companies you follow.
            </p>
            <Link className="login-btn" to="/watchlist" style={{ display: 'block', textAlign: 'center' }}>
              Go to my watchlist
            </Link>
          </>
        )}

        {status === 'no-token' && (
          <div className="login-error">
            This link is missing its token. Mail clients sometimes cut long links in half — open the
            email again and use the full link, or ask for a new one below.
          </div>
        )}

        {status === 'failed' && <div className="login-error">{error}</div>}

        {(status === 'failed' || status === 'no-token') && (
          <>
            <p className="login-hint" style={{ margin: '14px 0 8px' }}>
              Signed in? Ask for a fresh link — it stays valid for 24 hours.
            </p>
            <button
              className="login-btn"
              type="button"
              onClick={resend}
              disabled={resendState === 'sending'}
            >
              {resendState === 'sending' ? 'Sending…' : 'Send me a new link'}
            </button>
            {resendMessage && (
              <p
                className={resendState === 'sent' ? 'login-hint' : 'login-error'}
                style={{ marginTop: 10 }}
              >
                {resendMessage}
              </p>
            )}
            <p className="login-hint" style={{ marginTop: 14 }}>
              <Link to="/login">Back to sign in</Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
};

export default VerifyEmailPage;
