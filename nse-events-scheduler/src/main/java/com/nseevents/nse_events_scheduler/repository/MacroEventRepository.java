package com.nseevents.nse_events_scheduler.repository;

import com.nseevents.nse_events_scheduler.model.MacroEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for macro_events table.
 * All queries return results ordered by event_date ascending (soonest first).
 */
@Repository
public interface MacroEventRepository extends JpaRepository<MacroEvent, Long> {

    List<MacroEvent> findAllByOrderByEventDateAsc();

    List<MacroEvent> findByCategoryOrderByEventDateAsc(String category);

    List<MacroEvent> findByCountryOrderByEventDateAsc(String country);

    List<MacroEvent> findByEventDateGreaterThanEqualOrderByEventDateAsc(LocalDate from);

    List<MacroEvent> findByCategoryAndEventDateGreaterThanEqualOrderByEventDateAsc(String category, LocalDate from);

    List<MacroEvent> findByCountryAndEventDateGreaterThanEqualOrderByEventDateAsc(String country, LocalDate from);
}
