import React from 'react';
import { NavLink } from 'react-router-dom';
import './Navbar.css';

const Navbar = () => {
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
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
