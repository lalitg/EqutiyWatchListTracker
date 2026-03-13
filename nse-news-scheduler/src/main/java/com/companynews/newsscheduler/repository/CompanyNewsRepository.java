package com.companynews.newsscheduler.repository;

import com.companynews.newsscheduler.model.CompanyNews;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyNewsRepository extends JpaRepository<CompanyNews, Long> {

    // Spring auto-implements this: SELECT * FROM company_news WHERE keyword = ?
    // Returns Optional because the row may or may not exist
    Optional<CompanyNews> findByKeyword(String keyword);
}
