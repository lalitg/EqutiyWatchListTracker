package com.equity.fastmovers.repository;

import com.equity.fastmovers.entity.CompanyMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyMasterRepository extends JpaRepository<CompanyMaster, Long> {

    List<CompanyMaster> findByActiveTrueOrderBySymbolAsc();

    Optional<CompanyMaster> findBySymbol(String symbol);
}
