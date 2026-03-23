package com.companynews.newsscheduler.repository;

import com.companynews.newsscheduler.model.CompanyNews;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the {@code company_news} table.
 *
 * <p>Provides CRUD operations inherited from {@link JpaRepository} plus the custom
 * {@link #findByKeyword(String)} query method that is the primary read path for this service.
 *
 * <p>Spring auto-generates all implementations at startup — no manual SQL or implementation
 * class is required. Method names follow Spring Data naming conventions which are translated
 * into JPQL/SQL by the framework.
 */
@Repository
public interface CompanyNewsRepository extends JpaRepository<CompanyNews, Long> {

    /**
     * Looks up a news record by keyword.
     *
     * <p>Spring generates: {@code SELECT * FROM company_news WHERE keyword = ?}
     *
     * <p>Returns {@link Optional} because the row may or may not exist. Callers should
     * use {@link Optional#isPresent()} to distinguish a cache hit from a cache miss before
     * deciding whether to trigger an on-demand fetch.
     *
     * @param keyword the keyword to search for (company symbol, sector, or macro term)
     * @return an {@link Optional} containing the matching {@link CompanyNews} record,
     *         or {@link Optional#empty()} if no row exists for that keyword
     */
    Optional<CompanyNews> findByKeyword(String keyword);
}
