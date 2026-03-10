package com.companynews.newsscheduler.model;
 
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
 
@Entity
@Table(name = "company_upcoming_events")
@Data
@NoArgsConstructor
public class CompanyUpcomingEvents {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
 
    // UNIQUE: one row per company/sector/region
    @Column(name = "company_symbol", unique = true, nullable = false, length = 50)
    private String companySymbol;
 
    // Stores the full JSON array as a string in a JSONB column.
    // Example value: '[{"date":"2026-03-05","summary":"AGM"}]'
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "news", columnDefinition = "jsonb")
    private String news;
 
    @Column(name = "last_updated")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastUpdated;
 
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = LocalDateTime.now();
    }
}
