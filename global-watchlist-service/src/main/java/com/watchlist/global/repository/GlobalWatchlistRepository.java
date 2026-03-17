package com.watchlist.global.repository;

import com.watchlist.global.entity.GlobalWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GlobalWatchlistRepository extends JpaRepository<GlobalWatchlist, Long> {

    Optional<GlobalWatchlist> findByCompanyCode(String companyCode);

    boolean existsByCompanyCode(String companyCode);
}
