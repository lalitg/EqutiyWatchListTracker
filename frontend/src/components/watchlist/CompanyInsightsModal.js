import React, { useState, useEffect, useCallback } from 'react';
import Modal from '../shared/Modal';
import Toast from '../shared/Toast';
import TabBar from '../shared/TabBar';
import NewsList from '../market/NewsList';
import EventsList from '../market/EventsList';
import { fetchNews } from '../../services/newsService';
import { fetchEvents } from '../../services/eventsService';
import './CompanyInsightsModal.css';

const TABS = [
  { key: 'news', label: 'News' },
  { key: 'events', label: 'Events' },
];

const CompanyInsightsModal = ({ isOpen, onClose, entry, onAddToWatchlist, isInWatchlist, isAdding }) => {
  const [activeTab, setActiveTab] = useState('news');
  const [news, setNews] = useState([]);
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [toastVisible, setToastVisible] = useState(false);

  useEffect(() => {
    if (!isOpen || !entry) return;
    setActiveTab('news');
    setNews([]);
    setEvents([]);
    setLoading(true);

    Promise.all([
      fetchNews(entry.companyCode).catch(() => null),
      fetchEvents(entry.companyCode).catch(() => null),
    ]).then(([newsData, eventsData]) => {
      setNews(newsData?.news ?? []);
      setEvents(eventsData?.events ?? []);
    }).finally(() => setLoading(false));
  }, [isOpen, entry]);

  const handleAddClick = useCallback(() => {
    if (isInWatchlist) {
      setToastVisible(true);
      return;
    }
    if (onAddToWatchlist) onAddToWatchlist(entry.companyCode);
  }, [isInWatchlist, onAddToWatchlist, entry]);

  const companyName = entry?.companyName || entry?.companyCode || 'Company';

  return (
    <>
      <Modal isOpen={isOpen} onClose={onClose} title={companyName} maxWidth={640}>
        {onAddToWatchlist && (
          <div className="ci-watchlist-action">
            <button
              className={`ci-btn-watchlist ${isInWatchlist ? 'ci-btn-in-watchlist' : ''}`}
              onClick={handleAddClick}
              disabled={isAdding}
            >
              {isInWatchlist ? '✓ In Watchlist' : isAdding ? 'Adding...' : '+ Add to Watchlist'}
            </button>
          </div>
        )}
        {loading ? (
          <div className="ci-loading">Loading insights...</div>
        ) : (
          <>
            <TabBar tabs={TABS} activeTab={activeTab} onTabChange={setActiveTab} />
            <div className="ci-tab-content">
              {activeTab === 'news' && <NewsList news={news} />}
              {activeTab === 'events' && <EventsList events={events} />}
            </div>
          </>
        )}
      </Modal>
      <Toast
        message="Already in your watchlist"
        visible={toastVisible}
        onHide={() => setToastVisible(false)}
      />
    </>
  );
};

export default CompanyInsightsModal;
