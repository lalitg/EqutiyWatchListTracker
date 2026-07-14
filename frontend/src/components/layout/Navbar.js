import React, { useEffect, useState } from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import CompanySearchBar from './CompanySearchBar';
import './Navbar.css';

const Navbar = () => {
  const { isLoggedIn, user, logout } = useAuth();
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
            <NavLink to="/watchlist" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Watchlist
            </NavLink>
            <NavLink to="/market/global" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Global
            </NavLink>
            <NavLink to="/market/domestic" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Domestic
            </NavLink>
            <NavLink to="/upcoming-events" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Upcoming Events
            </NavLink>
            <NavLink to="/market/calendar" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Calendar
            </NavLink>
            {isLoggedIn ? (
              <div className="navbar-user">
                <span className="navbar-username">Hi, {user?.username}</span>
                <button className="navbar-logout-btn" onClick={handleLogout}>Logout</button>
              </div>
            ) : (
              <NavLink to="/login" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
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
