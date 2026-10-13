import React, { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useNotifications } from '../../hooks/useNotifications';
import NotificationPanel from './NotificationPanel';
import './NotificationPanel.css';

/**
 * The bell in the navbar, with its unread count.
 *
 * <p>Hidden entirely when signed out: there is nothing to count, and a control that cannot do
 * anything teaches the reader nothing.
 *
 * <p>Clicking an item opens that company's page on its default Latest News tab — the same
 * destination the Extremes boards use, because the headline is what the reader wants and the score
 * is only context. Opening also marks the item read, which is what the badge counts.
 *
 * @param {boolean} isLoggedIn whether there is a session to fetch notifications for
 */
const NotificationBell = ({ isLoggedIn }) => {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  const {
    unreadCount, items, loading, error, refreshList, open: markOpened, dismiss, readAll,
  } = useNotifications(isLoggedIn);

  const toggle = useCallback(() => {
    setOpen(prev => {
      const next = !prev;
      // Fetched on opening rather than kept warm: the list is only ever looked at here, and the
      // badge already tells the reader whether it is worth opening.
      if (next) refreshList();
      return next;
    });
  }, [refreshList]);

  const openItem = useCallback((item) => {
    if (item.unread) markOpened(item.id);
    setOpen(false);
    navigate(`/company/${encodeURIComponent(item.companyCode)}`);
  }, [markOpened, navigate]);

  if (!isLoggedIn) return null;

  const label = unreadCount > 0
    ? `Notifications — ${unreadCount} unread`
    : 'Notifications';

  return (
    <div className="nf-bell-wrap">
      <button
        type="button"
        className={`nf-bell ${open ? 'nf-bell--active' : ''}`}
        onClick={toggle}
        aria-label={label}
        aria-expanded={open}
        title={label}
      >
        <span aria-hidden="true">{unreadCount > 0 ? '🔔' : '🔕'}</span>
        {unreadCount > 0 && (
          // Capped at 99+: past that the exact number tells the reader nothing they can act on.
          <span className="nf-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>
        )}
      </button>

      <NotificationPanel
        open={open}
        items={items}
        loading={loading}
        error={error}
        onClose={() => setOpen(false)}
        onOpenItem={openItem}
        onDismiss={dismiss}
        onReadAll={readAll}
      />
    </div>
  );
};

export default NotificationBell;
