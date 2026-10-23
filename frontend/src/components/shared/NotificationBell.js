import React, { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useNotifications } from '../../hooks/useNotifications';
import { useEntitlements, FEATURES } from '../../context/EntitlementsContext';
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
  const { can } = useEntitlements();

  // Alerts are an Advanced feature, so a reader without them has no notifications to count. The
  // hook is told there is no session rather than being skipped, which is what stops the minute
  // polling running for every free reader in the product - a locked bell would cost the same
  // requests as a working one and never show a number.
  const allowed = can(FEATURES.ALERTS);

  const {
    unreadCount, items, loading, error, refreshList, open: markOpened, dismiss, readAll,
  } = useNotifications(isLoggedIn && allowed);

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

  // Hidden rather than locked: a bell is a container for things that have happened, and there is
  // nothing behind this one to show through a blur. The feature is advertised where it can be
  // explained - on the company page's "Alert me" button and on the pricing page.
  if (!isLoggedIn || !allowed) return null;

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
        {/* Always the plain bell. The slashed one means notifications are switched off,
            not that there is nothing unread - an empty panel is not a disabled one. */}
        <span aria-hidden="true">🔔</span>
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
