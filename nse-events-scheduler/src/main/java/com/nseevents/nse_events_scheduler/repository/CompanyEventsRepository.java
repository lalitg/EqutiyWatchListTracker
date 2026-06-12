package com.nseevents.nse_events_scheduler.repository;

import com.nseevents.nse_events_scheduler.model.CompanyEvents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for company_events table.
 * Spring auto-generates all implementations — no SQL written manually.
 */
@Repository
public interface CompanyEventsRepository extends JpaRepository<CompanyEvents, Long> {

    // Spring auto-generates: SELECT * FROM company_events WHERE keyword = ?
    Optional<CompanyEvents> findByKeyword(String keyword);
}

