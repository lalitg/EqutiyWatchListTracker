import React from 'react';
import './EventsList.css';

const EventsList = ({ events }) => {
  if (!events || events.length === 0) {
    return <p className="events-empty">No upcoming events.</p>;
  }

  return (
    <div className="events-list">
      <h3 className="events-list-title">Upcoming Events</h3>
      <div className="events-items">
        {events.map((item) => (
          <div key={item.id} className="event-item">
            <div className="event-date-badge">
              {item.date}
            </div>
            <div className="event-details">
              <div className="event-title">{item.title}</div>
              <span className="event-type">{item.type}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default EventsList;
