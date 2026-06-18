import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { forgotPassword, resetPassword } from '../services/authService';
import './LoginPage.css';

const LoginPage = () => {
  const [tab, setTab] = useState('login');
  const [successMsg, setSuccessMsg] = useState('');

  // Login state
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');

  // Signup state
  const [username, setUsername] = useState('');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [signupPassword, setSignupPassword] = useState('');
  // Forgot / reset password state
  const [forgotEmail, setForgotEmail] = useState('');
  const [resetToken, setResetToken] = useState('');
  const [newPassword, setNewPassword] = useState('');

  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const { login, signup } = useAuth();
  const navigate = useNavigate();

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(identifier, password);
      navigate('/watchlist', { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSignup = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await signup({
        username,
        name,
        email,
        phoneNumber: phone || undefined,
        password: signupPassword,
      });
      setSuccessMsg('Account created! Please log in.');
      setTab('login');
      setIdentifier(username);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleForgotPassword = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const data = await forgotPassword(forgotEmail);
      setSuccessMsg(data.message || 'If this email is registered, a reset token has been sent.');
      // In dev mode the token is returned in the response — pre-fill it for convenience
      if (data.resetToken) {
        setResetToken(data.resetToken);
        setSuccessMsg(`Token: ${data.resetToken} — use it below to reset your password.`);
      }
      setTab('reset');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await resetPassword(resetToken, newPassword);
      setSuccessMsg('Password reset successfully! Please log in.');
      setTab('login');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const switchTab = (t) => {
    setTab(t);
    setError('');
    setSuccessMsg('');
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">Nivesh Flow</div>

        <div className="login-tabs">
          <button
            className={`login-tab ${tab === 'login' ? 'active' : ''}`}
            onClick={() => switchTab('login')}
          >
            Login
          </button>
          <button
            className={`login-tab ${tab === 'signup' ? 'active' : ''}`}
            onClick={() => switchTab('signup')}
          >
            Sign Up
          </button>
        </div>

        {successMsg && <div className="login-success">{successMsg}</div>}
        {error && <div className="login-error">{error}</div>}

        {tab === 'login' ? (
          <form className="login-form" onSubmit={handleLogin}>
            <div className="login-field">
              <label>Username / Email / Phone</label>
              <input
                type="text"
                value={identifier}
                onChange={e => setIdentifier(e.target.value)}
                placeholder="Enter username, email or phone"
                required
                autoFocus
              />
            </div>
            <div className="login-field">
              <label>Password</label>
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="Enter password"
                required
              />
            </div>
            <button className="login-btn" type="submit" disabled={loading}>
              {loading ? 'Logging in...' : 'Login'}
            </button>
            <button
              type="button"
              className="login-forgot-link"
              onClick={() => switchTab('forgot')}
            >
              Forgot password?
            </button>
          </form>
        ) : tab === 'signup' ? (
          <form className="login-form" onSubmit={handleSignup}>
            <div className="login-field">
              <label>Username</label>
              <input
                type="text"
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder="Choose a username"
                required
                autoFocus
              />
            </div>
            <div className="login-field">
              <label>Full Name</label>
              <input
                type="text"
                value={name}
                onChange={e => setName(e.target.value)}
                placeholder="Your full name"
                required
              />
            </div>
            <div className="login-field">
              <label>Email</label>
              <input
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="Email address"
                required
              />
            </div>
            <div className="login-field">
              <label>Phone (optional)</label>
              <input
                type="tel"
                value={phone}
                onChange={e => setPhone(e.target.value)}
                placeholder="Phone number"
              />
            </div>
            <div className="login-field">
              <label>Password</label>
              <input
                type="password"
                value={signupPassword}
                onChange={e => setSignupPassword(e.target.value)}
                placeholder="Choose a password"
                required
              />
              <p className="login-hint">
                Min 8 characters · 1 uppercase · 1 number · 1 special character (@$!%*?&)
              </p>
            </div>
            <button className="login-btn" type="submit" disabled={loading}>
              {loading ? 'Creating account...' : 'Create Account'}
            </button>
          </form>
        ) : tab === 'forgot' ? (
          <form className="login-form" onSubmit={handleForgotPassword}>
            <p className="login-hint" style={{ marginBottom: 12 }}>
              Enter your registered email. We'll send you a reset token.
            </p>
            <div className="login-field">
              <label>Email</label>
              <input
                type="email"
                value={forgotEmail}
                onChange={e => setForgotEmail(e.target.value)}
                placeholder="your@email.com"
                required
                autoFocus
              />
            </div>
            <button className="login-btn" type="submit" disabled={loading}>
              {loading ? 'Sending...' : 'Send Reset Token'}
            </button>
            <button type="button" className="login-forgot-link" onClick={() => switchTab('login')}>
              Back to login
            </button>
          </form>
        ) : tab === 'reset' ? (
          <form className="login-form" onSubmit={handleResetPassword}>
            <p className="login-hint" style={{ marginBottom: 12 }}>
              Enter the reset token and your new password.
            </p>
            <div className="login-field">
              <label>Reset Token</label>
              <input
                type="text"
                value={resetToken}
                onChange={e => setResetToken(e.target.value)}
                placeholder="Paste your reset token"
                required
                autoFocus
              />
            </div>
            <div className="login-field">
              <label>New Password</label>
              <input
                type="password"
                value={newPassword}
                onChange={e => setNewPassword(e.target.value)}
                placeholder="New password"
                required
              />
              <p className="login-hint">
                Min 8 characters · 1 uppercase · 1 number · 1 special character (@$!%*?&)
              </p>
            </div>
            <button className="login-btn" type="submit" disabled={loading}>
              {loading ? 'Resetting...' : 'Reset Password'}
            </button>
            <button type="button" className="login-forgot-link" onClick={() => switchTab('forgot')}>
              Request a new token
            </button>
          </form>
        ) : null}
      </div>
    </div>
  );
};

export default LoginPage;
