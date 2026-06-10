package com.equity.fundamentals.repository;

import com.equity.fundamentals.entity.CompanyMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only repository for the company_master table.
 *
 * fundamentals-service only reads from this table.
 * nse-code-service owns and writes to it.
 *
 * findByActiveTrue() is the only method needed:
 *   Returns all companies where active = true.
 *   The scheduler iterates over this list to fetch data for each company.
 *   Spring Data JPA auto-generates: SELECT * FROM company_master WHERE active = true
 */
@Repository
public interface CompanyMasterRepository extends JpaRepository<CompanyMaster, Long> {

    List<CompanyMaster> findByActiveTrue();
}
