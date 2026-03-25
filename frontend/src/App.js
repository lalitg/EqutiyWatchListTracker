import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Navbar from './components/layout/Navbar';
import WatchlistPage from './pages/WatchlistPage';
import GlobalMarketPage from './pages/GlobalMarketPage';
import DomesticMarketPage from './pages/DomesticMarketPage';
import FastMoversPage from './pages/FastMoversPage';
import Nifty50Page from './pages/Nifty50Page';
import { WatchlistProvider } from './context/WatchlistContext';
import { MarketProvider } from './context/MarketContext';
import { CompanyListProvider } from './context/CompanyListContext';
import './App.css';

function App() {
  return (
    <CompanyListProvider>
    <WatchlistProvider>
      <MarketProvider>
        <div className="App">
          <Navbar />
          <main className="app-main">
            <Routes>
              <Route path="/" element={<Navigate to="/watchlist" replace />} />
              <Route path="/watchlist" element={<WatchlistPage />} />
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
  );
}

export default App;
