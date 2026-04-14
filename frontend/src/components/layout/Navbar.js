import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import './Navbar.css';

const Navbar = () => {
  const { isLoggedIn, user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <nav className="navbar">
      <div className="navbar-inner">
        <NavLink to="/watchlist" className="navbar-logo">
          Equity Watchlist Tracker
        </NavLink>
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
          <NavLink to="/market/fast-movers" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            Fast Movers
          </NavLink>
          <NavLink to="/market/nifty50" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            Nifty 50
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
    </nav>
  );
};

export default Navbar;
