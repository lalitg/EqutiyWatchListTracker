import { useEffect, useRef, useCallback } from 'react';

const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'touchstart', 'scroll', 'click'];

export function useIdleTimer({ timeout, onIdle, enabled = true }) {
  const timerRef  = useRef(null);
  const onIdleRef = useRef(onIdle);

  useEffect(() => { onIdleRef.current = onIdle; }, [onIdle]);

  const resetTimer = useCallback(() => {
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => { onIdleRef.current?.(); }, timeout);
  }, [timeout]);

  useEffect(() => {
    if (!enabled) {
      clearTimeout(timerRef.current);
      return;
    }
    resetTimer();
    ACTIVITY_EVENTS.forEach(evt => window.addEventListener(evt, resetTimer, { passive: true }));
    return () => {
      clearTimeout(timerRef.current);
      ACTIVITY_EVENTS.forEach(evt => window.removeEventListener(evt, resetTimer));
    };
  }, [enabled, resetTimer]);
}
