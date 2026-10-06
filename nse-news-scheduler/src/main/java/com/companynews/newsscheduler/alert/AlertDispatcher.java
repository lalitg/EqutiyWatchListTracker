package com.companynews.newsscheduler.alert;

import com.companynews.newsscheduler.service.NewsWorker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Turns alertable articles into one email per person, every few minutes.
 *
 * <h2>The shape of a cycle</h2>
 * <ol>
 *   <li>Ask which companies have unalerted, recent, threshold-crossing news.</li>
 *   <li>Ask who subscribes to those companies — verified addresses only.</li>
 *   <li>Group the articles by recipient, so someone following five companies gets one email and
 *       not five.</li>
 *   <li>Send, then stamp each article as alerted so it can never be sent again.</li>
 * </ol>
 *
 * <h2>Quiet hours hold, they do not drop</h2>
 * Between 22:00 and 08:00 nothing goes out. Because "has this been alerted?" is a property of the
 * article rather than a queue in memory, the overnight articles are simply still unalerted at 08:00
 * and go out in that cycle — the morning digest needs no separate mechanism, and a restart at 3am
 * changes nothing.
 *
 * <h2>The daily cap paces, it does not drop either</h2>
 * Past the cap, a recipient's further alerts are held and sent hourly instead of every cycle.
 * Nothing is discarded: a reader who hits the ceiling on a dramatic day still receives every
 * headline, in fewer emails.
 *
 * <h2>Why marking happens after sending</h2>
 * If the mail server fails, the articles stay unmarked and the next cycle tries again. The opposite
 * order would lose alerts silently on every transient SMTP failure. The cost is that a crash in the
 * instant between sending and marking could repeat one digest — a duplicate is a far better outcome
 * than silence.
 */
@Service
public class AlertDispatcher {

    private static final Logger log = LogManager.getLogger(AlertDispatcher.class);

    private final HotNewsCollector collector;
    private final SubscriberLookup subscriberLookup;
    private final AlertEmailService emailService;
    private final NewsWorker newsWorker;
    private final AlertProperties properties;
    private final UnsubscribeTokenSigner tokenSigner;

    /** Stops two cycles overlapping if one runs long. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Emails sent per user today, for the pacing valve. Reset on the first cycle of a new day. */
    private final Map<Long, Integer> sentToday = new HashMap<>();
    private final Map<Long, Instant> lastSendAt = new HashMap<>();
    private LocalDate countingDay = LocalDate.now(AlertProperties.IST);

    public AlertDispatcher(HotNewsCollector collector,
                           SubscriberLookup subscriberLookup,
                           AlertEmailService emailService,
                           NewsWorker newsWorker,
                           AlertProperties properties,
                           UnsubscribeTokenSigner tokenSigner) {
        this.collector        = collector;
        this.subscriberLookup = subscriberLookup;
        this.emailService     = emailService;
        this.newsWorker       = newsWorker;
        this.properties       = properties;
        this.tokenSigner      = tokenSigner;
    }

    /**
     * Runs on its own scheduler, never the shared one.
     *
     * <p>The other scheduled jobs in this service share a single thread — the hourly cleanup and the
     * 15-minute RSS fetch among them. Sending email there would put news fetching behind whatever
     * latency the mail server happens to have.
     */
    @Scheduled(cron = "${alert.dispatch.cron:0 */5 * * * *}",
               scheduler = "alertTaskScheduler",
               zone = "${scheduler.timezone:Asia/Kolkata}")
    public void dispatch() {
        if (!properties.isEnabled()) return;

        if (!tokenSigner.isConfigured()) {
            // Mailing someone with a dead unsubscribe link invites a spam complaint, which costs
            // the sending domain far more than a missed digest.
            log.warn("Alerts are enabled but alert.unsubscribe.secret is not set - skipping cycle");
            return;
        }

        if (!emailService.isEnabled()) {
            // Both switches have to agree. Running with alerts on and sending off would find the
            // same articles every cycle, log a digest nobody receives, and never stamp anything -
            // an infinite repeat that looks like work being done.
            log.warn("Alerts are enabled but email sending is off (app.email.enabled=false) - "
                   + "skipping cycle");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.warn("Previous alert cycle still running - skipping this one");
            return;
        }

        try {
            runCycle();
        } catch (Exception e) {
            log.error("Alert cycle failed", e);
        } finally {
            running.set(false);
        }
    }

    /** Exposed for tests and for a future admin trigger; carries no scheduling of its own. */
    public int runCycle() {
        ZonedDateTime now = ZonedDateTime.now(AlertProperties.IST);
        rollDayIfNeeded(now.toLocalDate());

        if (properties.isQuietAt(now.toLocalTime())) {
            log.debug("Quiet hours - holding alerts until {}", properties.getQuietEnd());
            return 0;
        }

        long cutoff = Instant.now()
                             .minus(Duration.ofHours(properties.getMaxArticleAgeHours()))
                             .toEpochMilli();

        List<String> candidates = collector.candidateKeywords(cutoff);
        if (candidates.isEmpty()) return 0;

        Map<String, List<SubscriberLookup.Subscriber>> subscribers =
            subscriberLookup.subscribersFor(candidates);
        if (subscribers.isEmpty()) {
            log.debug("{} company(ies) have alertable news, but nobody subscribes to them",
                      candidates.size());
            return 0;
        }

        // Articles gathered per recipient, so one person receives one email however many of their
        // companies are in the news this cycle.
        Map<Long, SubscriberLookup.Subscriber> people = new LinkedHashMap<>();
        Map<Long, List<HotArticle>> digests = new LinkedHashMap<>();

        for (String keyword : candidates) {
            List<SubscriberLookup.Subscriber> forCompany = subscribers.get(keyword);
            if (forCompany == null || forCompany.isEmpty()) continue;   // nobody follows it

            List<HotArticle> hot = collector.hotArticlesFor(keyword, cutoff);
            if (hot.isEmpty()) continue;                                // changed since the scan

            for (SubscriberLookup.Subscriber person : forCompany) {
                people.putIfAbsent(person.userId(), person);
                digests.computeIfAbsent(person.userId(), k -> new ArrayList<>()).addAll(hot);
            }
        }

        int sent = 0;
        Set<String> toMark = new HashSet<>();      // "keyword\0link" pairs actually delivered

        for (Map.Entry<Long, List<HotArticle>> entry : digests.entrySet()) {
            Long userId = entry.getKey();
            List<HotArticle> articles = entry.getValue();

            if (isPaced(userId, now.toInstant())) {
                log.info("userId={} is over the daily cap - holding {} article(s) for the next "
                       + "hourly batch", userId, articles.size());
                continue;                            // left unmarked, so the next cycle picks it up
            }

            if (emailService.send(people.get(userId), articles)) {
                sent++;
                recordSend(userId, now.toInstant());
                for (HotArticle a : articles) toMark.add(a.keyword() + " " + a.link());
            }
        }

        int marked = markDelivered(toMark);
        if (sent > 0 || marked > 0) {
            log.info("Alert cycle complete - {} digest(s) sent, {} article(s) marked", sent, marked);
        }
        return sent;
    }

    // ─── pacing ──────────────────────────────────────────────────────────────

    /**
     * Whether this recipient should wait.
     *
     * <p>Only applies past the daily cap, and even then it delays rather than drops: an hour after
     * their last email, the held articles go out together.
     */
    private boolean isPaced(Long userId, Instant now) {
        int already = sentToday.getOrDefault(userId, 0);
        if (already < properties.getDailyEmailCap()) return false;

        Instant last = lastSendAt.get(userId);
        return last != null && Duration.between(last, now).toMinutes() < 60;
    }

    private void recordSend(Long userId, Instant when) {
        sentToday.merge(userId, 1, Integer::sum);
        lastSendAt.put(userId, when);
    }

    /**
     * The counters are per-day and in memory on purpose.
     *
     * <p>A restart forgets them, which at worst lets a heavy day's recipient receive a few more
     * emails than the cap intended. Persisting them would mean a table to serve a comfort setting.
     */
    private void rollDayIfNeeded(LocalDate today) {
        if (!today.equals(countingDay)) {
            countingDay = today;
            sentToday.clear();
            lastSendAt.clear();
        }
    }

    // ─── marking ─────────────────────────────────────────────────────────────

    /**
     * Stamps delivered articles so they are never sent again.
     *
     * <p>Grouped by company because the write goes through {@link NewsWorker}, which holds a
     * per-company lock while it rewrites that company's news — the same lock the fetcher takes.
     * Writing without it would let a fetch landing at the same moment overwrite the stamp, or lose
     * the articles the fetch had just added.
     */
    private int markDelivered(Set<String> keywordAndLink) {
        if (keywordAndLink.isEmpty()) return 0;

        Map<String, Set<String>> linksByKeyword = new HashMap<>();
        for (String pair : keywordAndLink) {
            int sep = pair.indexOf(' ');
            linksByKeyword.computeIfAbsent(pair.substring(0, sep), k -> new HashSet<>())
                          .add(pair.substring(sep + 1));
        }

        int marked = 0;
        for (Map.Entry<String, Set<String>> entry : linksByKeyword.entrySet()) {
            try {
                marked += newsWorker.markAlerted(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                // The mail is already gone. Failing to mark means a possible duplicate next cycle,
                // which is worth logging loudly but is not worth failing the run for.
                log.error("Could not mark {} article(s) for {} as alerted - a duplicate is possible: {}",
                          entry.getValue().size(), entry.getKey(), e.getMessage());
            }
        }
        return marked;
    }

    /** Lets the quiet-hours rule be exercised without waiting for 22:00. */
    boolean isQuietNow() {
        return properties.isQuietAt(LocalTime.now(AlertProperties.IST));
    }
}
