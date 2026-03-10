package com.companynews.newsscheduler.repository;
 
import com.companynews.newsscheduler.model.CompanyNewsArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
 
@Repository
public interface CompanyNewsArchiveRepository
        extends JpaRepository<CompanyNewsArchive, Long> {
}
