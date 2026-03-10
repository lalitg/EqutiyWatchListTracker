package com.companynews.newsscheduler.model;
 
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
 
@Entity
@Table(name = "company_news_archive")
@Data
@NoArgsConstructor
public class CompanyNewsArchive {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
 
    @Column(name = "company_symbol", nullable = false, length = 50)
    private String companySymbol;
 
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "news_data", columnDefinition = "jsonb")
    private String newsData;
 
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
 
    @PrePersist
    protected void onCreate() { this.archivedAt = LocalDateTime.now(); }
}
