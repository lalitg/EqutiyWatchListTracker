package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.ExtremeEntryDto;
import com.companynews.newsscheduler.dto.ExtremesDto;
import com.companynews.newsscheduler.dto.SentimentWindow;
import com.companynews.newsscheduler.repository.CompanyDailySentimentRepository;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import com.companynews.newsscheduler.repository.RankedSentimentProjection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Assembles the Top Positives and Top Negatives boards for the Extremes page.
 *
 * <h2>Two sources, one shape</h2>
 * Single-day boards rank the {@code company_daily_sentiment} rollup; cumulative boards rank the
 * denormalised window columns on {@code company_news}. Both arrive as
 * {@link RankedSentimentProjection}, so everything below this point — naming, banding, ranking —
 * is written once.
 *
 * <p>The split is not arbitrary. A day is a calendar bucket, while a cumulative range is a rolling
 * window measured back from now; summing seven daily rows would answer "the last seven calendar
 * days", not "the last 168 hours". Since each row links through to that company's Sentiments tab,
 * where the same span is a rolling window, deriving one from the other would show two different
 * numbers for the same label one click apart.
 *
 * <h2>No minimum article count</h2>
 * Deliberate, and it shapes what these boards mean. A company whose entire day is one dramatic
 * headline competes directly against one carrying twenty articles, and because single-article
 * scores sit at the extremes of the -5..+5 scale, the lone headline usually wins. Two things keep
 * that honest rather than misleading: every row carries its article count, and ties break on that
 * count so an equally scored twenty-article consensus outranks a single headline.
 */
@Service
public class ExtremesService {

    private static final Logger log = LogManager.getLogger(ExtremesService.class);

    private final CompanyDailySentimentRepository dailyRepository;
    private final CompanyNewsRepository newsRepository;
    private final CompanyNameLookup nameLookup;
    private final SentimentScorer scorer;

    /** How many rows each board carries. */
    private final int boardSize;

    /** How many single-day tabs the page offers, counting today. */
    private final int dayCount;

    public ExtremesService(CompanyDailySentimentRepository dailyRepository,
                           CompanyNewsRepository newsRepository,
                           CompanyNameLookup nameLookup,
                           SentimentScorer scorer,
                           @Value("${sentiment.extremes.board-size:10}") int boardSize,
                           @Value("${sentiment.extremes.day-count:7}") int dayCount) {
        this.dailyRepository = dailyRepository;
        this.newsRepository  = newsRepository;
        this.nameLookup      = nameLookup;
        this.scorer          = scorer;
        this.boardSize       = boardSize;
        this.dayCount        = dayCount;
    }

    /**
     * The days the Single Day tab offers, newest first, starting from today.
     *
     * <p>Generated from the calendar rather than from whichever days happen to hold rows. A day with
     * no news anywhere is a real answer — quiet weekends produce roughly a quarter of a weekday's
     * article volume — and hiding it would silently renumber the tabs, so "3 days ago" would mean
     * different dates on different visits.
     *
     * @return {@code dayCount} consecutive dates ending today, in Indian market time
     */
    public List<LocalDate> selectableDays() {
        LocalDate today = DailySentimentService.today();
        List<LocalDate> days = new ArrayList<>(dayCount);
        for (int i = 0; i < dayCount; i++) days.add(today.minusDays(i));
        return days;
    }

    /**
     * Builds the board for one calendar day.
     *
     * @param day the day to rank within
     * @return the board; empty lists when nothing was published that day
     */
    @Transactional(readOnly = true)
    public ExtremesDto forDay(LocalDate day) {
        Pageable limit = PageRequest.of(0, boardSize);

        List<RankedSentimentProjection> top    = dailyRepository.rankTopForDay(day, limit);
        List<RankedSentimentProjection> bottom = dailyRepository.rankBottomForDay(day, limit);

        log.debug("Extremes day={} top={} bottom={}", day, top.size(), bottom.size());
        return assemble(day.toString(), dayLabel(day), day, top, bottom);
    }

    /**
     * Builds the board for one cumulative range.
     *
     * @param window one of the rolling periods; single-article and calendar rows are not valid here
     * @return the board
     */
    @Transactional(readOnly = true)
    public ExtremesDto forWindow(SentimentWindow window) {
        Pageable limit = PageRequest.of(0, boardSize);

        List<RankedSentimentProjection> top;
        List<RankedSentimentProjection> bottom;

        switch (window) {
            case WEEK_1    -> { top = newsRepository.rankTopByWeek(limit);
                                bottom = newsRepository.rankBottomByWeek(limit); }
            case WEEK_2    -> { top = newsRepository.rankTopByTwoWeeks(limit);
                                bottom = newsRepository.rankBottomByTwoWeeks(limit); }
            case MONTH_1   -> { top = newsRepository.rankTopByMonth(limit);
                                bottom = newsRepository.rankBottomByMonth(limit); }
            case QUARTER_1 -> { top = newsRepository.rankTopByQuarter(limit);
                                bottom = newsRepository.rankBottomByQuarter(limit); }
            default -> throw new IllegalArgumentException(
                "Not a cumulative range: " + window + ". Valid: WEEK_1, WEEK_2, MONTH_1, QUARTER_1");
        }

        log.debug("Extremes window={} top={} bottom={}", window, top.size(), bottom.size());
        return assemble(window.name(), window.label(), null, top, bottom);
    }

    // ── internals ──────────────────────────────────────────────────────────────

    /**
     * Turns two ranked projections into a response, resolving display names in one query.
     *
     * <p>Names for both boards are fetched together rather than per row: twenty rows would otherwise
     * mean twenty round trips to render one page.
     */
    private ExtremesDto assemble(String range,
                                 String label,
                                 LocalDate day,
                                 List<RankedSentimentProjection> top,
                                 List<RankedSentimentProjection> bottom) {

        Set<String> symbols = new HashSet<>();
        top.forEach(r -> symbols.add(r.getKeyword()));
        bottom.forEach(r -> symbols.add(r.getKeyword()));

        // A display name is cosmetic: the lookup swallows its own failures and returns an empty
        // map, so losing it costs a nicer label rather than the board.
        Map<String, String> names = nameLookup.namesFor(symbols);

        return new ExtremesDto(range, label, day,
                               toEntries(top, names),
                               toEntries(bottom, names),
                               LocalDateTime.now());
    }

    private List<ExtremeEntryDto> toEntries(List<RankedSentimentProjection> rows,
                                            Map<String, String> names) {
        List<ExtremeEntryDto> entries = new ArrayList<>(rows.size());
        int rank = 1;
        for (RankedSentimentProjection row : rows) {
            double score = round2(row.getScore());
            entries.add(new ExtremeEntryDto(
                rank++,
                row.getKeyword(),
                names.get(row.getKeyword()),
                score,
                scorer.toLabel(score),
                row.getArticleCount() == null ? 0 : row.getArticleCount()));
        }
        return entries;
    }

    /**
     * Human label for a day tab.
     *
     * <p>Today and Yesterday are named; everything older gets its date. Weekday names alone were
     * considered and dropped: weekends carry roughly a quarter of a weekday's article volume, so two
     * of the seven tabs are visibly thin, and a reader seeing a bare "Sat" wonders what broke rather
     * than recognising a quiet weekend. A date says so plainly and stays unambiguous if the page
     * ever offers more than seven days, where weekday names would start repeating.
     */
    public String forDayLabel(LocalDate day) {
        return dayLabel(day);
    }

    private String dayLabel(LocalDate day) {
        LocalDate today = DailySentimentService.today();
        if (day.equals(today))               return "Today";
        if (day.equals(today.minusDays(1)))  return "Yesterday";
        return day.getDayOfMonth() + " " + day.getMonth()
                    .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
    }

    private static double round2(Double value) {
        if (value == null) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }

    /** Exposed so the controller can validate a requested range without duplicating the mapping. */
    public static final Function<String, SentimentWindow> CUMULATIVE_RANGES = key -> {
        SentimentWindow window = SentimentWindow.valueOf(key);
        if (!window.isRolling()) {
            throw new IllegalArgumentException("Not a cumulative range: " + key);
        }
        return window;
    };
}
