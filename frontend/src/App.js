import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Navbar from './components/layout/Navbar';
import WatchlistPage from './pages/WatchlistPage';
import GlobalMarketPage from './pages/GlobalMarketPage';
import DomesticMarketPage from './pages/DomesticMarketPage';
import FastMoversPage from './pages/FastMoversPage';
import Nifty50Page from './pages/Nifty50Page';
import LoginPage from './pages/LoginPage';
import { AuthProvider, useAuth } from './context/AuthContext';
import { WatchlistProvider } from './context/WatchlistContext';
import { MarketProvider } from './context/MarketContext';
import { CompanyListProvider } from './context/CompanyListContext';
import './App.css';

function ProtectedRoute({ children }) {
  const { isLoggedIn } = useAuth();
  return isLoggedIn ? children : <Navigate to="/login" replace />;
}

function App() {
  return (
    <AuthProvider>
      <CompanyListProvider>
        <WatchlistProvider>
          <MarketProvider>
            <div className="App">
              <Navbar />
              <main className="app-main">
                <Routes>
                  <Route path="/login" element={<LoginPage />} />
                  <Route path="/" element={<Navigate to="/watchlist" replace />} />
                  <Route path="/watchlist" element={
                    <ProtectedRoute><WatchlistPage /></ProtectedRoute>
                  } />
                  <Route path="/market/global" element={<GlobalMarketPage />} />
                  <Route path="/market/domestic" element={<DomesticMarketPage />} />
                  <Route path="/market/fast-movers" element={<FastMoversPage />} />
                  <Route path="/market/nifty50" element={<Nifty50Page />} />
                </Routes>
              </main>
            </div>
          </MarketProvider>
        </WatchlistProvider>
      </CompanyListProvider>
    </AuthProvider>
  );
}

export default App;
