package com.watchlist.global.repository;

import com.watchlist.global.entity.GlobalWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlobalWatchlistRepository extends JpaRepository<GlobalWatchlist, Long> {

    boolean existsBySymbol(String symbol);
}
