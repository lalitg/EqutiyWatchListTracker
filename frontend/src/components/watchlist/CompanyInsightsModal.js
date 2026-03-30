import React, { useState, useEffect } from 'react';
import Modal from '../shared/Modal';
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

const CompanyInsightsModal = ({ isOpen, onClose, entry }) => {
  const [activeTab, setActiveTab] = useState('news');
  const [news, setNews] = useState([]);
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);

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

  const companyName = entry?.companyName || entry?.companyCode || 'Company';

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={companyName} maxWidth={640}>
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
  );
};

export default CompanyInsightsModal;
