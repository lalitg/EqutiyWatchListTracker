package com.companynews.newsscheduler.repository;
 
import com.companynews.newsscheduler.model.CompanyEventsArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
 
@Repository
public interface CompanyEventsArchiveRepository
        extends JpaRepository<CompanyEventsArchive, Long> {
}
