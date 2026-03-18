package com.nseevents.nse_events_scheduler.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * DTO representing one single NSE calendar event.
 * Maps directly to what NSE returns and what gets stored in JSONB.
 *
 * Fields stored in DB JSONB: date, event, purpose
 * Fields NOT stored (JsonIgnore): symbol — keyword column already identifies company
 */
public class EventItem {

    // The event date — e.g. "23-Apr-2026"
    private String date;

    // bm_desc from NSE — the full event description
    // Renamed to "event" as your mentor requested
    private String event;

    // purpose from NSE — category e.g. "Financial Results", "Dividend"
    private String purpose;

    // symbol from NSE — used internally to group events by company
    // @JsonIgnore — never stored in JSONB, keyword column already has this
    @JsonIgnore
    private String symbol;

    // Default constructor — required by Jackson for JSONB deserialization
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
}

