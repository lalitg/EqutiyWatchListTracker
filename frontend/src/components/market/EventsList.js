import React from 'react';
import './EventsList.css';

const MONTH_MAP = { Jan:0, Feb:1, Mar:2, Apr:3, May:4, Jun:5, Jul:6, Aug:7, Sep:8, Oct:9, Nov:10, Dec:11 };

function parseEventDate(dateStr) {
  if (!dateStr) return null;
  const parts = dateStr.split('-');
  if (parts.length !== 3) return null;
  const m = MONTH_MAP[parts[1]];
  if (m === undefined) return null;
  return new Date(Number(parts[2]), m, Number(parts[0]));
}

function todayMidnight() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

const EventItem = ({ item, past }) => (
  <div className="event-item">
    <div className={`event-date-badge${past ? ' event-date-badge--past' : ''}`}>
      {item.date}
    </div>
    <div className="event-details">
      <div className="event-title">{item.event}</div>
      {item.purpose && <span className="event-type">{item.purpose}</span>}
    </div>
  </div>
);

const EventsList = ({ events }) => {
  const today = todayMidnight();

  const upcoming = [];
  const past = [];

  (events || []).forEach(item => {
    const d = parseEventDate(item.date);
    if (d && d < today) {
      past.push({ ...item, _parsed: d });
    } else {
      upcoming.push({ ...item, _parsed: d });
    }
  });

  upcoming.sort((a, b) => (a._parsed || 0) - (b._parsed || 0));
  past.sort((a, b) => (b._parsed || 0) - (a._parsed || 0));

  return (
    <div className="events-list">
      <div className="events-section">
        <h3 className="events-section-title">Upcoming Events</h3>
        {upcoming.length > 0 ? (
          <div className="events-items">
            {upcoming.map((item, idx) => <EventItem key={idx} item={item} past={false} />)}
          </div>
        ) : (
          <p className="events-empty">No upcoming events.</p>
        )}
      </div>

      <div className="events-section">
        <h3 className="events-section-title events-section-title--past">Past Events</h3>
        {past.length > 0 ? (
          <div className="events-items">
            {past.map((item, idx) => <EventItem key={idx} item={item} past={true} />)}
          </div>
        ) : (
          <p className="events-empty">No past events.</p>
        )}
      </div>
    </div>
  );
};

export default EventsList;
