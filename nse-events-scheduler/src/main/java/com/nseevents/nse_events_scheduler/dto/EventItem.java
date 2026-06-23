package com.nseevents.nse_events_scheduler.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;

/**
 * DTO representing one single NSE calendar event.
 * Maps directly to what NSE returns and what gets stored in JSONB.
 *
 * Fields stored in DB JSONB: date, event, purpose
 * Fields NOT stored (JsonIgnore): symbol — keyword column already identifies company
 *
 * equals/hashCode are based on (date, event, purpose) so that List.stream().distinct()
 * removes cross-source duplicates before the events are persisted.
 */
public class EventItem {

    private String date;
    private String event;
    private String purpose;

    @JsonIgnore
    private String symbol;

    // Which NSE API this event came from: "event-calendar", "board-meetings", "corp-actions"
    // Used by EventWorkerService to run per-source early-exit independently.
    // Never stored in JSONB — internal routing only.
    @JsonIgnore
    private String source;

    public EventItem() {}

    public EventItem(String date, String event, String purpose) {
        this.date = date;
        this.event = event;
        this.purpose = purpose;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    @JsonIgnore
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    @JsonIgnore
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventItem)) return false;
        EventItem that = (EventItem) o;
        return Objects.equals(date, that.date)
            && Objects.equals(event, that.event)
            && Objects.equals(purpose, that.purpose);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, event, purpose);
    }
}

