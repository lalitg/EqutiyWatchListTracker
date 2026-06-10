export function isWeekend(date) {
  const day = date.getDay();
  return day === 0 || day === 6;
}

export function getHolidayInfo(holidays, date) {
  if (!holidays) return null;
  const iso = toISO(date);
  return holidays.find(h => h.date === iso) || null;
}

/**
 * Returns { isOpen: boolean, reason: string|null }.
 * reason is null when open, or 'Weekend' / holiday name when closed.
 */
export function getMarketStatus(holidays, date = new Date()) {
  if (isWeekend(date)) {
    return { isOpen: false, reason: 'Weekend' };
  }
  const holiday = getHolidayInfo(holidays, date);
  if (holiday) {
    return { isOpen: false, reason: holiday.name };
  }
  return { isOpen: true, reason: null };
}

/** Returns next n upcoming holidays from fromDate (includes today if it's a holiday). */
export function getUpcomingHolidays(holidays, n = 5, fromDate = new Date()) {
  if (!holidays) return [];
  const today = toISO(fromDate);
  return holidays.filter(h => h.date >= today).slice(0, n);
}

function toISO(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}
