package com.companycode.nse.repository;

import com.companycode.nse.entity.CompanyMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyMasterRepository extends JpaRepository<CompanyMaster, Long> {
    Optional<CompanyMaster> findBySymbol(String symbol);
}
