import { useCallback, useEffect, useRef, useState } from 'react';
import {
  dismissNotification,
  getUnreadCount,
  listNotifications,
  markAllRead,
  markRead,
} from '../services/notificationService';

/** How often the badge re-checks while the tab is open. */
const POLL_MS = 60_000;

/**
 * Keeps the bell's count and the panel's list.
 *
 * <h3>Why polling</h3>
 * A websocket would deliver the count a minute sooner and cost a persistent connection per reader,
 * plus reconnect handling, on a server that has already fallen over once. The alert itself is up to
 * fifteen minutes old by the time it exists — news is only fetched every quarter of an hour — so a
 * minute of polling latency is noise against that.
 *
 * <h3>Why it also refreshes on focus</h3>
 * A tab left open overnight would otherwise show a stale number the moment someone returns to it,
 * which is exactly when they look. Browsers also throttle timers in background tabs, so the
 * interval alone cannot be trusted to have kept up.
 *
 * <h3>Optimistic updates</h3>
 * Dismissing and reading apply locally first and revert if the server disagrees. The alternative —
 * waiting for a round trip before the row disappears — makes the panel feel broken on a slow
 * connection, for an action that essentially never fails.
 *
 * @param {boolean} isLoggedIn there is nothing to show, or poll for, when signed out
 */
export function useNotifications(isLoggedIn) {
  const [unreadCount, setUnreadCount] = useState(0);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Avoids a dangling setState when the user signs out mid-request.
  const mounted = useRef(true);
  useEffect(() => () => { mounted.current = false; }, []);

  const refreshCount = useCallback(async () => {
    if (!isLoggedIn) return;
    try {
      const count = await getUnreadCount();
      if (mounted.current) setUnreadCount(count);
    } catch {
      // A failed poll is not worth showing anyone: the next one is a minute away, and a stale
      // badge is better than an error banner over the whole app.
    }
  }, [isLoggedIn]);

  const refreshList = useCallback(async () => {
    if (!isLoggedIn) return;
    setLoading(true);
    setError('');
    try {
      const list = await listNotifications(20);
      if (mounted.current) setItems(list || []);
    } catch (err) {
      if (mounted.current) setError(err.message);
    } finally {
      if (mounted.current) setLoading(false);
    }
  }, [isLoggedIn]);

  // Poll the count, and re-check whenever the reader comes back to the tab.
  useEffect(() => {
    if (!isLoggedIn) {
      setUnreadCount(0);
      setItems([]);
      return undefined;
    }

    refreshCount();
    const timer = setInterval(refreshCount, POLL_MS);

    const onFocus = () => {
      if (document.visibilityState === 'visible') refreshCount();
    };
    document.addEventListener('visibilitychange', onFocus);
    window.addEventListener('focus', onFocus);

    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onFocus);
      window.removeEventListener('focus', onFocus);
    };
  }, [isLoggedIn, refreshCount]);

  /** Opening an item: it stays in the list, but stops counting. */
  const open = useCallback(async (id) => {
    setItems(prev => prev.map(n => (n.id === id ? { ...n, unread: false } : n)));
    setUnreadCount(prev => Math.max(0, prev - 1));
    try {
      await markRead(id);
    } catch {
      refreshCount();          // put the truth back
    }
  }, [refreshCount]);

  /** The × on a row: gone from the list for good. */
  const dismiss = useCallback(async (id) => {
    const previous = items;
    const target = items.find(n => n.id === id);

    setItems(prev => prev.filter(n => n.id !== id));
    if (target?.unread) setUnreadCount(prev => Math.max(0, prev - 1));

    try {
      await dismissNotification(id);
    } catch {
      setItems(previous);      // the row comes back rather than vanishing silently
      refreshCount();
    }
  }, [items, refreshCount]);

  const readAll = useCallback(async () => {
    const previous = items;
    setItems(prev => prev.map(n => ({ ...n, unread: false })));
    setUnreadCount(0);
    try {
      await markAllRead();
    } catch {
      setItems(previous);
      refreshCount();
    }
  }, [items, refreshCount]);

  return { unreadCount, items, loading, error, refreshCount, refreshList, open, dismiss, readAll };
}

export default useNotifications;
