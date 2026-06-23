package com.nseevents.nse_events_scheduler.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for the macro_events table.
 * Stores hardcoded/pre-populated macroeconomic event dates:
 *   BUDGET, ELECTION, RBI_MPC, US_CPI, US_NFP, MUHURAT_TRADING, FOMC
 *
 * Table is created automatically by Hibernate (ddl-auto=update).
 * Indexes on category and event_date are created via @Table(indexes=...).
 * Data is seeded once on first startup by MacroEventDataSeeder.
 */
@Entity
@Table(name = "macro_events", indexes = {
    @Index(name = "idx_macro_events_category", columnList = "category"),
    @Index(name = "idx_macro_events_date",     columnList = "event_date")
})
public class MacroEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "event_name", nullable = false, length = 500)
    private String eventName;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_time", length = 50)
    private String eventTime;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "country", length = 10)
    private String country;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    private void touch() {
        this.lastUpdated = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public String getEventTime() { return eventTime; }
    public void setEventTime(String eventTime) { this.eventTime = eventTime; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
}
