import React, { useState, useEffect } from 'react';
import Modal from '../shared/Modal';
import TabBar from '../shared/TabBar';
import TrendPanel from '../market/TrendPanel';
import NewsList from '../market/NewsList';
import EventsList from '../market/EventsList';
import { fetchCompanyInsights } from '../../services/companyService';
import './CompanyInsightsModal.css';

const TABS = [
  { key: 'news', label: 'News' },
  { key: 'events', label: 'Events' },
  { key: 'trend', label: 'Trend' },
];

const CompanyInsightsModal = ({ isOpen, onClose, entry }) => {
  const [activeTab, setActiveTab] = useState('news');
  const [insights, setInsights] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!isOpen || !entry) return;
    setActiveTab('news');
    setLoading(true);
    fetchCompanyInsights(entry.companyCode)
      .then(setInsights)
      .finally(() => setLoading(false));
  }, [isOpen, entry]);

  const companyName = entry?.companyName || entry?.companyCode || 'Company';

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={companyName} maxWidth={640}>
      {loading ? (
        <div className="ci-loading">Loading insights...</div>
      ) : insights ? (
        <>
          <TabBar tabs={TABS} activeTab={activeTab} onTabChange={setActiveTab} />
          <div className="ci-tab-content">
            {activeTab === 'news' && <NewsList news={insights.news} />}
            {activeTab === 'events' && <EventsList events={insights.events} />}
            {activeTab === 'trend' && <TrendPanel trend={insights.trend} />}
          </div>
        </>
      ) : (
        <p className="ci-empty">No insights available.</p>
      )}
    </Modal>
  );
};

export default CompanyInsightsModal;
