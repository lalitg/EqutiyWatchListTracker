package com.companynews.newsscheduler.repository;

import com.companynews.newsscheduler.model.CompanyDailySentiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Access to the per-company, per-day sentiment rollup behind the Extremes page.
 *
 * <p>The ranking queries return a projection rather than entities: a board shows ten rows, and
 * materialising every candidate as a managed entity to throw all but ten away is wasted work on a
 * path any visitor can trigger.
 */
@Repository
public interface CompanyDailySentimentRepository extends JpaRepository<CompanyDailySentiment, Long> {

    /** Looks up one company's row for one day, for the upsert path. */
    Optional<CompanyDailySentiment> findByKeywordAndDay(String keyword, LocalDate day);

    /** All of one company's rows, used when rebuilding its history from scratch. */
    List<CompanyDailySentiment> findByKeyword(String keyword);

    /**
     * Ranks companies by their mean sentiment on a single day, most positive first.
     *
     * <h2>The tie-break is doing real work here</h2>
     * There is deliberately no minimum article count, so a company whose whole day is one dramatic
     * headline competes directly with one carrying twenty articles — and single-article scores
     * cluster at the extremes of the -5..+5 scale, so exact ties are common rather than rare.
     * Ordering by {@code articleCount} next puts the better-evidenced company above an equally
     * scored single headline, and the final ordering by keyword makes the result stable so a board
     * does not reshuffle between refreshes.
     *
     * @param day   the calendar day to rank within
     * @param limit unused by the query itself; callers pass a {@link org.springframework.data.domain.Pageable}
     * @return rows for that day, best first
     */
    @Query("""
           select d.keyword                        as keyword,
                  (d.scoreSum / d.articleCount)    as score,
                  d.articleCount                   as articleCount
           from CompanyDailySentiment d
           where d.day = :day and d.articleCount > 0
           order by (d.scoreSum / d.articleCount) desc, d.articleCount desc, d.keyword asc
           """)
    List<RankedSentimentProjection> rankTopForDay(@Param("day") LocalDate day,
                                                  org.springframework.data.domain.Pageable limit);

    /** As {@link #rankTopForDay}, most negative first. */
    @Query("""
           select d.keyword                        as keyword,
                  (d.scoreSum / d.articleCount)    as score,
                  d.articleCount                   as articleCount
           from CompanyDailySentiment d
           where d.day = :day and d.articleCount > 0
           order by (d.scoreSum / d.articleCount) asc, d.articleCount desc, d.keyword asc
           """)
    List<RankedSentimentProjection> rankBottomForDay(@Param("day") LocalDate day,
                                                     org.springframework.data.domain.Pageable limit);

    /** Which days actually hold data, newest first — drives the day selector so it never offers an empty tab blindly. */
    @Query("select distinct d.day from CompanyDailySentiment d order by d.day desc")
    List<LocalDate> findDistinctDaysDesc(org.springframework.data.domain.Pageable limit);

    /**
     * Deletes rollup rows older than the cut-off.
     *
     * <p>Kept in step with article retention on purpose. These rows are derived data, and holding
     * them beyond the articles they summarise would leave history that nothing could rebuild or
     * verify.
     */
    @Modifying
    @Query("delete from CompanyDailySentiment d where d.day < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDate cutoff);

    /** Removes every row for one company, used before rebuilding its history. */
    @Modifying
    @Query("delete from CompanyDailySentiment d where d.keyword = :keyword")
    int deleteByKeyword(@Param("keyword") String keyword);
}
