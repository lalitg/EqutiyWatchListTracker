package com.nseevents.nse_events_scheduler.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nseevents.nse_events_scheduler.dto.EventItem;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA entity mapping to company_events table.
 * One row per company symbol.
 * All events for that company stored as JSONB array.
 */
@Entity
@Table(name = "company_events")
public class CompanyEvents {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One row per company — UNIQUE ensures no duplicates
    @Column(name = "keyword", unique = true, nullable = false)
    private String keyword;

    // JSONB column — stores List<EventItem> as JSON array
    // Hibernate + Jackson handle serialization automatically
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "events", columnDefinition = "jsonb")
    private List<EventItem> events;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public List<EventItem> getEvents() { return events; }
    public void setEvents(List<EventItem> events) { this.events = events; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}

