package com.companynews.newsscheduler.model;
 
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
 
@Entity
@Table(name = "company_events_archive")
@Data
@NoArgsConstructor
public class CompanyEventsArchive {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
 
    // No UNIQUE — many archived rows per symbol is expected
    @Column(name = "company_symbol", nullable = false, length = 50)
    private String companySymbol;
 
    // Stores ONE archived event as JSON
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data", columnDefinition = "jsonb")
    private String eventData;
 
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
 
    @PrePersist
    protected void onCreate() { this.archivedAt = LocalDateTime.now(); }
}
