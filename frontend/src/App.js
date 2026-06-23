import React from 'react';
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import Navbar from './components/layout/Navbar';
import WatchlistPage from './pages/WatchlistPage';
import GlobalMarketPage from './pages/GlobalMarketPage';
import DomesticMarketPage from './pages/DomesticMarketPage';
import IndexCompaniesPage from './pages/IndexCompaniesPage';
import SectorCompaniesPage from './pages/SectorCompaniesPage';
import CompanyDetailPage from './pages/CompanyDetailPage';
import LoginPage from './pages/LoginPage';
import NseCalendarPage from './pages/NseCalendarPage';
import { AuthProvider, useAuth } from './context/AuthContext';
import { WatchlistProvider } from './context/WatchlistContext';
import { MarketProvider } from './context/MarketContext';
import { CompanyListProvider } from './context/CompanyListContext';
import { useIdleTimer } from './hooks/useIdleTimer';
import './App.css';

const IDLE_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

function ProtectedRoute({ children }) {
  const { isLoggedIn } = useAuth();
  return isLoggedIn ? children : <Navigate to="/login" replace />;
}

function AppContent() {
  const { isLoggedIn, logout } = useAuth();
  const navigate = useNavigate();

  useIdleTimer({
    timeout: IDLE_TIMEOUT_MS,
    enabled: isLoggedIn,
    onIdle: async () => {
      await logout();
      navigate('/login', { state: { sessionExpired: true } });
    },
  });

  return (
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
          <Route path="/market/domestic/index/:indexKey" element={<IndexCompaniesPage />} />
          <Route path="/market/domestic/sector/:sectorKey" element={<SectorCompaniesPage />} />
          <Route path="/company/:symbol" element={<CompanyDetailPage />} />
          <Route path="/market/calendar" element={<NseCalendarPage />} />
        </Routes>
      </main>
    </div>
  );
}

function App() {
  return (
    <AuthProvider>
      <CompanyListProvider>
        <WatchlistProvider>
          <MarketProvider>
            <AppContent />
          </MarketProvider>
        </WatchlistProvider>
      </CompanyListProvider>
    </AuthProvider>
  );
}

export default App;
