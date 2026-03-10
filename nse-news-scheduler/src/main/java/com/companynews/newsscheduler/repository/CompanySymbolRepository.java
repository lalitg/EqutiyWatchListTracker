package com.companynews.newsscheduler.repository;

import com.companynews.newsscheduler.model.CompanyMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CompanySymbolRepository extends JpaRepository<CompanyMaster, Long> {

    @Query(value = "SELECT symbol FROM company_master", nativeQuery = true)
    List<String> findAllSymbols();
}