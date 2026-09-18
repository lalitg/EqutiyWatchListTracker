import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { resetPassword } from '../services/authService';
import './LoginPage.css';

/**
 * Dedicated page for the password-reset link delivered by email:
 *   https://niveshflow.com/reset-password?token=<rawToken>
 *
 * The token is read from the URL query string (never typed by the user), so the
 * reset token never appears in the app UI. The user only enters a new password.
 */
const ResetPasswordPage = () => {
  const [params] = useSearchParams();
  const token = params.get('token') || '';
  const navigate = useNavigate();

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!token) {
      setError('This reset link is invalid or missing its token. Please request a new one.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    setLoading(true);
    try {
      await resetPassword(token, newPassword);
      // Success — send the user to login with a one-time success banner.
      navigate('/login', { replace: true, state: { resetSuccess: true } });
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">Nivesh Flow</div>

        <p className="login-hint" style={{ marginBottom: 16 }}>
          Set a new password for your account.
        </p>

        {!token && (
          <div className="login-error">
            This reset link is invalid or missing its token. Please request a new one from the login page.
          </div>
        )}
        {error && <div className="login-error">{error}</div>}

        <form className="login-form" onSubmit={handleSubmit}>
          <div className="login-field">
            <label>New Password</label>
            <input
              type="password"
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              placeholder="New password"
              required
              autoFocus
            />
            <p className="login-hint">
              Min 8 characters · 1 uppercase · 1 number · 1 special character (@$!%*?&)
            </p>
          </div>
          <div className="login-field">
            <label>Confirm New Password</label>
            <input
              type="password"
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              placeholder="Re-enter new password"
              required
            />
          </div>
          <button className="login-btn" type="submit" disabled={loading || !token}>
            {loading ? 'Resetting...' : 'Reset Password'}
          </button>
          <button type="button" className="login-forgot-link" onClick={() => navigate('/login')}>
            Back to login
          </button>
        </form>
      </div>
    </div>
  );
};

export default ResetPasswordPage;
