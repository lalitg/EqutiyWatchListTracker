package com.companynews.newsscheduler.repository;
 
import com.companynews.newsscheduler.model.CompanyUpcomingEvents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
 
@Repository
public interface CompanyUpcomingEventsRepository
        extends JpaRepository<CompanyUpcomingEvents, Long> {
 
    Optional<CompanyUpcomingEvents> findByCompanySymbol(String companySymbol);
}
