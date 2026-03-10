package com.watchlist.global.repository;

import com.watchlist.global.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
}
