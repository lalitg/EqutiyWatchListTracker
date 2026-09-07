package com.companynews.newsscheduler.repository;

import com.companynews.newsscheduler.model.CompanyNews;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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

    /**
     * Looks up many keywords in a single query.
     *
     * <p>Spring generates: {@code SELECT * FROM company_news WHERE keyword IN (?, ?, ...)}
     *
     * <p>Exists specifically for the batch sentiment endpoint. The watchlist and the Nifty
     * index / sector company tables each render dozens of companies at once; issuing one
     * {@link #findByKeyword(String)} per row would mean fifty round trips to serve a single
     * page. Keywords with no row are simply absent from the result — the caller treats those
     * as "no data" rather than an error.
     *
     * @param keywords the keywords to look up
     * @return matching records, in unspecified order and possibly fewer than requested
     */
    List<CompanyNews> findByKeywordIn(Collection<String> keywords);

    /**
     * Reads the denormalised sentiment columns for many keywords without touching the JSONB.
     *
     * <p>This is the query behind {@code GET /api/news/sentiment}. {@link #findByKeywordIn} would
     * answer the same question, but it selects {@code news} too — so serving one watchlist means
     * transferring and deserialising every stored article of every company on it, to produce a
     * handful of numbers that are already sitting in columns. Naming the scalar columns explicitly
     * keeps that page load proportional to the number of companies rather than to the volume of
     * news behind them, which is what makes quarter-long retention affordable.
     *
     * <p>Keywords with no row are simply absent from the result; the caller seeds those as
     * {@code NO_DATA}.
     *
     * @param keywords the keywords to look up
     * @return one projection per matching row, in unspecified order
     */
    @Query("""
           select c.keyword         as keyword,
                  c.latestScore     as latestScore,
                  c.latestLabel     as latestLabel,
                  c.newestArticleAt as newestArticleAt,
                  c.quarterScore    as quarterScore,
                  c.quarterLabel    as quarterLabel,
                  c.quarterCount    as quarterCount
           from CompanyNews c
           where c.keyword in :keywords
           """)
    List<SentimentProjection> findSentimentsByKeywordIn(@Param("keywords") Collection<String> keywords);
}
