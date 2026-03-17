package com.companynews.newsscheduler.repository;

import com.companynews.newsscheduler.model.GlobalWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GlobalWatchlistRepository extends JpaRepository<GlobalWatchlist, Long> {

    // Changed g.symbol to g.companyCode to match the renamed Java field
    // JPQL uses Java field names, not DB column names
    @Query("SELECT g.companyCode FROM GlobalWatchlist g")
    List<String> findAllSymbols();
}
