package com.companynews.newsscheduler.repository;
 
import com.companynews.newsscheduler.model.CompanyNews;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
 
@Repository
public interface CompanyNewsRepository
        extends JpaRepository<CompanyNews, Long> {
 
    Optional<CompanyNews> findByCompanySymbol(String companySymbol);
}
