import React, { useEffect, useState } from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useEntitlements, FEATURES } from '../../context/EntitlementsContext';
import NotificationBell from '../shared/NotificationBell';
import CompanySearchBar from './CompanySearchBar';
import { NAV_DESCRIPTIONS } from '../../constants/marketDescriptions';
import './Navbar.css';

const Navbar = () => {
  const { isLoggedIn, user, logout } = useAuth();
  const { can, tier } = useEntitlements();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  /**
   * A padlock beside a link the reader cannot use yet.
   *
   * The link still works. Blocking it would leave someone who clicked an advertised feature with
   * no way to find out what it is or what it costs; letting them through lands them on the page
   * itself, blurred, with the plan that unlocks it one click away.
   */
  const lock = (feature) => (
    can(feature) ? null : <span className="nf-nav-lock" aria-label="paid feature" title="Included in a paid plan">&#128274;</span>
  );

  return (
    <nav className="navbar">
      <div className="navbar-inner">
        <NavLink to="/watchlist" className="navbar-logo">
          Niveshflow
        </NavLink>
        <button
          type="button"
          className="navbar-toggle"
          onClick={() => setMenuOpen(o => !o)}
          aria-label="Toggle menu"
          aria-expanded={menuOpen}
        >
          {menuOpen ? '✕' : '☰'}
        </button>
        <div className={`navbar-collapse ${menuOpen ? 'navbar-collapse--open' : ''}`}>
          <CompanySearchBar />
          <div className="navbar-links">
            <NavLink to="/watchlist" data-tooltip={NAV_DESCRIPTIONS['Watchlist']} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Watchlist
            </NavLink>
            <NavLink to="/market/global" data-tooltip={NAV_DESCRIPTIONS['Global']} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Global
            </NavLink>
            <NavLink to="/market/domestic" data-tooltip={NAV_DESCRIPTIONS['Domestic']} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Domestic
            </NavLink>
            <NavLink to="/extremes" data-tooltip={NAV_DESCRIPTIONS['Extremes']} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Extremes{lock(FEATURES.EXTREMES)}
            </NavLink>
            <NavLink to="/mutual-funds" data-tooltip={NAV_DESCRIPTIONS['Mutual Funds']} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Mutual Funds{lock(FEATURES.MUTUAL_FUNDS)}
            </NavLink>
            <NavLink to="/upcoming-events" data-tooltip={NAV_DESCRIPTIONS['Upcoming Events']} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Upcoming Events{lock(FEATURES.EVENTS)}
            </NavLink>
            <NavLink to="/market/calendar" data-tooltip={NAV_DESCRIPTIONS['Calendar']} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Calendar{lock(FEATURES.CALENDAR)}
            </NavLink>
            {isLoggedIn ? (
              <div className="navbar-user">
                {/* The bell sits with the account controls, where every other global control is,
                    and so it is visible on every page rather than only on the watchlist. */}
                <NotificationBell isLoggedIn={isLoggedIn} />
                {/* The tier, where an account badge belongs, and the only route to the pricing
                    page from the header - a separate "Plans" link cost more width than it earned.
                    On a paid plan the chip is a statement; on the free one it is an invitation. */}
                <NavLink
                  to="/subscribe"
                  className={`navbar-tier navbar-tier--${(tier || 'free').toLowerCase()}`}
                  title={tier === 'FREE'
                    ? 'You are on the free plan - see what the paid plans include'
                    : `You are on the ${tier} plan - see all plans`}
                >
                  {tier === 'FREE' ? 'Upgrade' : tier === 'BASIC' ? 'Basic' : 'Advanced'}
                </NavLink>
                <span className="navbar-username">Hi, {user?.username}</span>
                <button className="navbar-logout-btn" data-tooltip={NAV_DESCRIPTIONS['Logout']} onClick={handleLogout}>Logout</button>
              </div>
            ) : (
              <NavLink to="/login" data-tooltip={NAV_DESCRIPTIONS['Login']} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
                Login
              </NavLink>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
