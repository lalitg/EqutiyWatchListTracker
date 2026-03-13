
package com.watchlist.marketdata.repository;

import com.watchlist.marketdata.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CompanyRepository extends JpaRepository<Company,Long>{
    List<Company> findByActiveTrue();
}
