import React, { useEffect, useRef } from 'react';
import './NotificationPanel.css';

/** "4 min ago" / "3 h ago" / "2 days ago" — relative is what a feed wants. */
function timeAgo(iso) {
  if (!iso) return '';
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return '';

  const minutes = Math.floor((Date.now() - then) / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h ago`;

  const days = Math.floor(hours / 24);
  return days === 1 ? 'yesterday' : `${days} days ago`;
}

const scoreText = (score) =>
  typeof score === 'number' ? `${score > 0 ? '+' : ''}${score.toFixed(1)}` : '';

/**
 * The side panel behind the bell.
 *
 * <h3>Two targets per row, on purpose</h3>
 * The row opens the company; the × dismisses without opening. The × stops the click from reaching
 * the row — without that, dismissing would also navigate away, which is the opposite of what
 * someone clearing a list wants.
 *
 * <h3>Closing</h3>
 * Escape and a click outside both close it, because a panel that can only be closed by its own
 * button traps anyone who opened it by accident.
 */
const NotificationPanel = ({ open, items, loading, error, onClose, onOpenItem, onDismiss, onReadAll }) => {
  const panelRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;

    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    const onClickOutside = (e) => {
      // The bell itself is excluded: it toggles, and closing here first would make a click on it
      // reopen the panel immediately.
      if (panelRef.current
          && !panelRef.current.contains(e.target)
          && !e.target.closest?.('.nf-bell')) {
        onClose();
      }
    };

    document.addEventListener('keydown', onKey);
    document.addEventListener('mousedown', onClickOutside);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('mousedown', onClickOutside);
    };
  }, [open, onClose]);

  if (!open) return null;

  const unreadCount = items.filter(n => n.unread).length;

  return (
    <div className="nf-panel" ref={panelRef} role="dialog" aria-label="Notifications">
      <div className="nf-panel-head">
        <span className="nf-panel-title">Notifications</span>
        {unreadCount > 0 && (
          <button className="nf-panel-readall" type="button" onClick={onReadAll}>
            Mark all read
          </button>
        )}
        <button className="nf-panel-close" type="button" onClick={onClose} aria-label="Close">×</button>
      </div>

      <div className="nf-panel-body">
        {loading && <div className="nf-panel-empty">Loading…</div>}

        {!loading && error && <div className="nf-panel-error">{error}</div>}

        {!loading && !error && items.length === 0 && (
          // Said plainly rather than as "no data": an empty panel is the normal state, and the
          // reader may not know alerts have to be switched on per company.
          <div className="nf-panel-empty">
            Nothing yet. Open a company and choose <strong>Alert me</strong> to hear about its news.
          </div>
        )}

        {!loading && !error && items.map(item => (
          <div
            key={item.id}
            className={`nf-item ${item.unread ? 'nf-item--unread' : ''}`}
            onClick={() => onOpenItem(item)}
            onKeyDown={(e) => { if (e.key === 'Enter') onOpenItem(item); }}
            role="button"
            tabIndex={0}
            title={`Open ${item.companyCode}`}
          >
            <div className="nf-item-main">
              <div className="nf-item-top">
                <span className="nf-item-sym">{item.companyCode}</span>
                <span className={`nf-item-score ${item.score > 0 ? 'up' : 'down'}`}>
                  {scoreText(item.score)}
                </span>
              </div>
              <div className="nf-item-headline">{item.headline}</div>
              <div className="nf-item-when">{timeAgo(item.createdAt)}</div>
            </div>
            <button
              className="nf-item-x"
              type="button"
              aria-label={`Dismiss ${item.companyCode}`}
              title="Dismiss"
              onClick={(e) => { e.stopPropagation(); onDismiss(item.id); }}
            >
              ×
            </button>
          </div>
        ))}
      </div>

      <div className="nf-panel-foot">
        Sentiment is generated automatically by an AI model reading headlines. It is for information
        only and is not investment advice — open the article before acting on it.
      </div>
    </div>
  );
};

export default NotificationPanel;
