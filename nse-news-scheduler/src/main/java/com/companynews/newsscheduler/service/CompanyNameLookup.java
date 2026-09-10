package com.companynews.newsscheduler.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves company symbols to display names for the Extremes boards.
 *
 * <h2>Why plain JDBC rather than a JPA repository</h2>
 * {@code company_master} belongs to another module. Mapping it as an {@code @Entity} here would put
 * a second schema definition of the same table in the codebase — and with {@code ddl-auto=update},
 * a definition that disagreed even slightly could alter a table this service has no business
 * owning. A Spring Data repository is not an option either: it requires a managed entity type, so
 * there is no way to declare one without first mapping the table.
 *
 * <p>Two read-only columns behind one query is exactly the case plain JDBC is for. The retired
 * fast-movers service reached the same table the same way to label its leaderboard.
 *
 * <h2>Failure is cosmetic, and treated that way</h2>
 * A missing name costs a nicer label; the board still ranks, still links, and still shows the
 * ticker. So every failure path here returns an empty map rather than propagating — losing the
 * whole page because a decorative lookup failed would be a much worse outcome than showing
 * tickers.
 */
@Component
public class CompanyNameLookup {

    private static final Logger log = LogManager.getLogger(CompanyNameLookup.class);

    /**
     * Cap on symbols per lookup, guarding the generated {@code IN (...)} list.
     *
     * <p>Never approached in practice: a board is ten rows and a page shows two boards. It exists
     * so a future caller cannot turn this into an unbounded query by passing a larger collection.
     */
    private static final int MAX_SYMBOLS = 200;

    private final JdbcTemplate jdbcTemplate;

    public CompanyNameLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Looks up display names for the given symbols in a single query.
     *
     * <p>One query rather than one per row: twenty rows would otherwise mean twenty round trips to
     * render one page.
     *
     * @param symbols company symbols; may be empty
     * @return symbol to display name. Symbols with no row, or with a null name, are simply absent —
     *         the caller falls back to the ticker
     */
    public Map<String, String> namesFor(Collection<String> symbols) {
        Map<String, String> names = new HashMap<>();
        if (symbols == null || symbols.isEmpty()) return names;

        var distinct = symbols.stream().filter(s -> s != null && !s.isBlank())
                              .distinct().limit(MAX_SYMBOLS).toList();
        if (distinct.isEmpty()) return names;

        String placeholders = String.join(",", java.util.Collections.nCopies(distinct.size(), "?"));
        String sql = "select symbol, company_name from company_master where symbol in ("
                   + placeholders + ")";

        try {
            jdbcTemplate.query(sql, rs -> {
                String symbol = rs.getString("symbol");
                String name   = rs.getString("company_name");
                if (symbol != null && name != null && !name.isBlank()) {
                    names.put(symbol, name);
                }
            }, distinct.toArray());
        } catch (Exception e) {
            log.warn("Company name lookup failed, falling back to symbols: {}", e.getMessage());
            return Map.of();
        }
        return names;
    }
}
