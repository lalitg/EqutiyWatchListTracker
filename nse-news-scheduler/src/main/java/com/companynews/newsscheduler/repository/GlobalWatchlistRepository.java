package com.companynews.newsscheduler.repository;

import com.companynews.newsscheduler.model.GlobalWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GlobalWatchlistRepository extends JpaRepository<GlobalWatchlist, Long> {

    // Fetch only the symbol strings — we don't need the full entity objects
    // This runs: SELECT symbol FROM global_watchlist
    @Query("SELECT g.symbol FROM GlobalWatchlist g")
    List<String> findAllSymbols();
}
