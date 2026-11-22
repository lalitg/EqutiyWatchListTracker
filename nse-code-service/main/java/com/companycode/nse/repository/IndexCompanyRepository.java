package com.companycode.nse.repository;

import com.companycode.nse.entity.IndexCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface IndexCompanyRepository extends JpaRepository<IndexCompany, Long> {

    List<IndexCompany> findBySymbol(String symbol);

    List<IndexCompany> findByIndexKeyOrderByCompanyNameAsc(String indexKey);

    @Query("SELECT DISTINCT i.indexKey, i.displayName FROM IndexCompany i ORDER BY i.displayName")
    List<Object[]> findDistinctIndices();

    @Query("SELECT i.indexKey, COUNT(i) FROM IndexCompany i GROUP BY i.indexKey")
    List<Object[]> countByIndexKey();

    @Modifying
    @Query("DELETE FROM IndexCompany")
    void deleteAllBulk();
}
