package com.companynews.newsscheduler.service;

import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies company news headlines as "important" corporate-action news.
 *
 * <p>An article is flagged {@code "important"} when its headline contains any of the phrases
 * listed in the {@code important-keywords.txt} classpath file — corporate-action terms such as
 * dividend, buyback, stock split, bonus issue, or merger. This drives the frontend's separate
 * "Important News" tab, which retains flagged articles for 90 days (vs. the 7-day Latest window).
 *
 * <p>WHY externalize the phrase list to a file (rather than hard-coding): these phrases WILL
 * need tuning against real fetched headlines — some terms ({@code result}, {@code target},
 * {@code bids}) are noise-prone and may need refining or removing after observing live data.
 * Keeping them in a file means the list can change without a code change or redeploy, exactly
 * like {@code keywords.txt} is used by {@link KeywordLoader}.
 *
 * <p>Matching is case-insensitive and word-boundary anchored, so {@code "results"} matches but
 * {@code "consultant"} does not falsely match on the substring {@code "result"}. All phrases are
 * compiled once at startup into a single alternation regex for efficient per-headline matching.
 */
@Service
public class NewsImportanceClassifier {

    private static final Logger log = LogManager.getLogger(NewsImportanceClassifier.class);

    /** Category value stored on a flagged {@link com.companynews.newsscheduler.dto.NewsItem}. */
    public static final String CATEGORY_IMPORTANT = "important";

    private static final String PHRASE_FILE = "important-keywords.txt";

    /**
     * Compiled alternation of all phrases, or {@code null} if the file was missing/empty —
     * in which case {@link #classify(String)} always returns {@code null} (nothing flagged).
     */
    private Pattern pattern;

    /**
     * Loads and compiles the phrase list on startup.
     *
     * <p>Each phrase is regex-quoted (so a phrase containing regex metacharacters is treated
     * literally) and wrapped with {@code \b} word boundaries. Phrases are joined with {@code |}
     * into a single case-insensitive {@link Pattern} matched against each headline.
     */
    @PostConstruct
    public void init() {
        Set<String> phrases = loadPhrases();
        if (phrases.isEmpty()) {
            log.warn("No phrases loaded from {} — importance classification disabled (nothing will be flagged)",
                PHRASE_FILE);
            this.pattern = null;
            return;
        }

        List<String> quoted = new ArrayList<>();
        for (String phrase : phrases) {
            quoted.add("\\b" + Pattern.quote(phrase) + "\\b");
        }
        this.pattern = Pattern.compile(String.join("|", quoted), Pattern.CASE_INSENSITIVE);
        log.info("Loaded {} importance phrases from {}", phrases.size(), PHRASE_FILE);
    }

    /**
     * Classifies a headline as important or not.
     *
     * @param headline the article headline/summary text (may be {@code null})
     * @return {@link #CATEGORY_IMPORTANT} if the headline matched any phrase, otherwise {@code null}
     */
    public String classify(String headline) {
        if (headline == null || headline.isBlank() || pattern == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(headline);
        return matcher.find() ? CATEGORY_IMPORTANT : null;
    }

    /**
     * Reads the phrase file from the classpath, skipping blank lines and {@code #} comments.
     * Mirrors {@link KeywordLoader#loadFileKeywords()} so the two behave identically.
     *
     * @return a deduplicated, order-preserving set of phrases; empty if the file is absent
     */
    private Set<String> loadPhrases() {
        Set<String> phrases = new LinkedHashSet<>();
        try {
            ClassPathResource resource = new ClassPathResource(PHRASE_FILE);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        phrases.add(line);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("{} not found or unreadable — skipping: {}", PHRASE_FILE, e.getMessage());
        }
        return phrases;
    }
}