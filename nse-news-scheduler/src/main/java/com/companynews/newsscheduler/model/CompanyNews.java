package com.companynews.newsscheduler.model;
 
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.annotation.JsonFormat;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
 
@Entity
@Table(name = "company_news")
@Data
@NoArgsConstructor
public class CompanyNews {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
 
    @Column(name = "company_symbol", unique = true, nullable = false, length = 50)
    private String companySymbol;
 
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
