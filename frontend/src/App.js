import React from 'react';
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import Navbar from './components/layout/Navbar';
import Footer from './components/layout/Footer';
import WatchlistPage from './pages/WatchlistPage';
import GlobalMarketPage from './pages/GlobalMarketPage';
import DomesticMarketPage from './pages/DomesticMarketPage';
import IndexCompaniesPage from './pages/IndexCompaniesPage';
import SectorCompaniesPage from './pages/SectorCompaniesPage';
import ExtremesPage from './pages/ExtremesPage';
import CompanyDetailPage from './pages/CompanyDetailPage';
import LoginPage from './pages/LoginPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import VerifyEmailPage from './pages/VerifyEmailPage';
import UnsubscribePage from './pages/UnsubscribePage';
import NseCalendarPage from './pages/NseCalendarPage';
import UpcomingEventsPage from './pages/UpcomingEventsPage';
import MutualFundsPage from './pages/MutualFundsPage';
import SubscribePage from './pages/SubscribePage';
import { AuthProvider, useAuth } from './context/AuthContext';
import { WatchlistProvider } from './context/WatchlistContext';
import { MarketProvider } from './context/MarketContext';
import { CompanyListProvider } from './context/CompanyListContext';
import { EntitlementsProvider } from './context/EntitlementsContext';
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
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          {/* Public by necessity: opened from a mail client with no session. */}
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          {/* Also public: an unsubscribe link must work from a mail client, and asking someone to
              sign in before they may stop email is how a spam complaint happens instead. */}
          <Route path="/unsubscribe" element={<UnsubscribePage />} />
          <Route path="/" element={<Navigate to="/watchlist" replace />} />
          <Route path="/watchlist" element={
            <ProtectedRoute><WatchlistPage /></ProtectedRoute>
          } />
          <Route path="/market/global" element={<GlobalMarketPage />} />
          <Route path="/market/domestic" element={<DomesticMarketPage />} />
          <Route path="/market/domestic/index/:indexKey" element={<IndexCompaniesPage />} />
          <Route path="/market/domestic/sector/:sectorKey" element={<SectorCompaniesPage />} />
          <Route path="/company/:symbol" element={<CompanyDetailPage />} />
          <Route path="/extremes" element={<ExtremesPage />} />
          <Route path="/mutual-funds" element={<MutualFundsPage />} />
          <Route path="/upcoming-events" element={<UpcomingEventsPage />} />
          <Route path="/market/calendar" element={<NseCalendarPage />} />
          {/* Public: a visitor has to be able to read the prices before they have an account. */}
          <Route path="/subscribe" element={<SubscribePage />} />
        </Routes>
      </main>
      {/* Outside <Routes> on purpose: one instance, on every page, surviving navigation. A
          disclaimer that each page had to remember to render is a disclaimer that will be missing
          from the next page somebody adds. */}
      <Footer />
    </div>
  );
}

function App() {
  return (
    <AuthProvider>
      {/* Inside AuthProvider, because what a reader may see depends on who they are; outside
          everything else, because almost every page asks. */}
      <EntitlementsProvider>
        <CompanyListProvider>
          <WatchlistProvider>
            <MarketProvider>
              <AppContent />
            </MarketProvider>
          </WatchlistProvider>
        </CompanyListProvider>
      </EntitlementsProvider>
    </AuthProvider>
  );
}

export default App;
