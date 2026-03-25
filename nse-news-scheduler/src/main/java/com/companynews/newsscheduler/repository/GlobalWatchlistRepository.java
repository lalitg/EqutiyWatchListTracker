package com.companynews.newsscheduler.repository;

import com.companynews.newsscheduler.model.GlobalWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@code global_watchlist} table.
 *
 * <p>Provides access to the list of company symbols that all users have added to their
 * watchlists. This list drives the Google RSS keyword loading — every symbol in the
 * global watchlist is fetched for news during each scheduled run.
 */
@Repository
public interface GlobalWatchlistRepository extends JpaRepository<GlobalWatchlist, Long> {

    /**
     * Returns all company symbols stored in the global watchlist.
     *
     * <p>Uses JPQL with the Java field name {@code companyCode} (not the DB column name
     * {@code company_code}) because JPQL operates on the entity model, not the physical schema.
     * The {@code @Column(name = "company_code")} annotation on the entity handles the column mapping.
     *
     * <p>Generated SQL equivalent: {@code SELECT company_code FROM global_watchlist}
     *
     * @return a list of company symbol strings (e.g., {@code ["INFY", "TCS", "RELIANCE"]});
     *         returns an empty list if the table is empty
     */
    @Query("SELECT g.companyCode FROM GlobalWatchlist g")
    List<String> findAllSymbols();
}
