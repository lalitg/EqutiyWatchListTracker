import React, { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { unsubscribeByToken } from '../services/alertService';
import './LoginPage.css';

/**
 * Where the unsubscribe link in an alert email lands:
 *   https://niveshflow.com/unsubscribe?token=<signed token>
 *
 * It acts immediately rather than asking for confirmation. Someone who clicked "stop these emails"
 * has already decided, and a confirmation step is one more thing standing between them and the
 * outcome they want — the alternative they reach for is the spam button, which costs far more than
 * one lost subscriber.
 *
 * No session is needed or asked for: the signed token names the user and the scope.
 */
const UnsubscribePage = () => {
  const [params] = useSearchParams();
  const token = params.get('token') || '';

  const [status, setStatus] = useState(token ? 'working' : 'no-token');
  const [message, setMessage] = useState('');
  const [scope, setScope] = useState('');

  // Strict mode runs effects twice in development. Unsubscribing is idempotent, so a second call
  // would be harmless — but it would briefly report "already off" over a successful result.
  const attempted = useRef(false);

  useEffect(() => {
    if (!token || attempted.current) return;
    attempted.current = true;

    unsubscribeByToken(token)
      .then(result => {
        setScope(result.scope === 'all' ? 'all' : result.scope);
        setMessage(result.message || 'You have been unsubscribed.');
        setStatus('done');
      })
      .catch(err => {
        setMessage(err.message);
        setStatus('failed');
      });
  }, [token]);

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">Nivesh Flow</div>

        {status === 'working' && (
          <p className="login-hint" style={{ marginBottom: 16 }}>Updating your alerts…</p>
        )}

        {status === 'done' && (
          <>
            <h2 style={{ fontSize: 18, margin: '0 0 8px', color: '#1a1a2e' }}>
              {scope === 'all' ? 'All alerts are off' : `Alerts off for ${scope}`}
            </h2>
            <p className="login-hint" style={{ marginBottom: 16 }}>{message}</p>
            <Link className="login-btn" to="/watchlist" style={{ display: 'block', textAlign: 'center' }}>
              Go to my watchlist
            </Link>
            {scope !== 'all' && (
              <p className="login-hint" style={{ marginTop: 14 }}>
                Still receiving alerts for other companies. Turn any of them off from that company’s
                page, or use the “Stop all alerts” link in any alert email.
              </p>
            )}
          </>
        )}

        {status === 'no-token' && (
          <div className="login-error">
            This link is missing its token. Mail clients sometimes cut long links in half — open the
            email again and use the full link, or turn alerts off from the company’s page.
          </div>
        )}

        {status === 'failed' && (
          <>
            <div className="login-error">{message}</div>
            <p className="login-hint" style={{ marginTop: 14 }}>
              You can also turn alerts off from any company’s page after signing in.
            </p>
            <Link className="login-btn" to="/login" style={{ display: 'block', textAlign: 'center' }}>
              Sign in
            </Link>
          </>
        )}
      </div>
    </div>
  );
};

export default UnsubscribePage;
