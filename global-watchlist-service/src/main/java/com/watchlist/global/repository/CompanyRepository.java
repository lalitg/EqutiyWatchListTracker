package com.watchlist.global.repository;

import com.watchlist.global.entity.CompanyMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<CompanyMaster, Long> {

    Optional<CompanyMaster> findBySymbol(String symbol);
}
