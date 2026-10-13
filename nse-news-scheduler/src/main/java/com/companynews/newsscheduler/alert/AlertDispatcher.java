package com.companynews.newsscheduler.alert;

import com.companynews.newsscheduler.service.NewsWorker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
 * Tells subscribers about news on the companies they follow.
 *
 * <h2>Two channels, one at a time</h2>
 * {@code alert.channel} selects where alerts go: {@code notifications} (the default — a row in the
 * user's notification panel) or {@code email}. Deliberately exclusive rather than two booleans,
 * because both channels share the article stamp: with both on, whichever ran first would stamp the
 * article and the other would find nothing. One setting makes that impossible to get wrong.
 *
 * <p>Email is the dormant channel. AWS refused SES production access on 12 October, so it cannot
 * reach real users; the code stays, tested and switched off, and one property value brings it back.
 *
 * <h2>The shape of a cycle</h2>
 * <ol>
 *   <li>Ask which companies have unalerted, recent, threshold-crossing news.</li>
 *   <li>Ask who subscribes to those companies.</li>
 *   <li>Raise a notification per subscriber per article — or, on the email channel, group the
 *       articles into one digest per person.</li>
 *   <li>Stamp each article that was actually delivered, so it can never be raised again.</li>
 * </ol>
 *
 * <h2>Why marking happens after delivery</h2>
 * If delivery fails, the articles stay unstamped and the next cycle tries again. The opposite order
 * loses alerts silently on every transient failure. The cost is that a crash in the instant between
 * delivering and stamping could repeat one item — and for notifications even that is covered, by
 * the unique index on (user, article).
 *
 * <h2>What applies to which channel</h2>
 * Quiet hours and the daily cap are email concerns and are skipped for notifications: a badge at
 * 3am disturbs nobody, and a list does not flood the way an inbox does.
 */
@Service
public class AlertDispatcher {

    private static final Logger log = LogManager.getLogger(AlertDispatcher.class);

    private final HotNewsCollector collector;
    private final SubscriberLookup subscriberLookup;
    private final AlertEmailService emailService;
    private final NotificationWriter notificationWriter;
    private final NewsWorker newsWorker;
    private final AlertProperties properties;
    private final UnsubscribeTokenSigner tokenSigner;

    /** Stops two cycles overlapping if one runs long. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Emails sent per user today, for the email pacing valve. Reset on the first cycle of a day. */
    private final Map<Long, Integer> sentToday = new HashMap<>();
    private final Map<Long, Instant> lastSendAt = new HashMap<>();
    private LocalDate countingDay = LocalDate.now(AlertProperties.IST);

    public AlertDispatcher(HotNewsCollector collector,
                           SubscriberLookup subscriberLookup,
                           AlertEmailService emailService,
                           NotificationWriter notificationWriter,
                           NewsWorker newsWorker,
                           AlertProperties properties,
                           UnsubscribeTokenSigner tokenSigner) {
        this.collector          = collector;
        this.subscriberLookup   = subscriberLookup;
        this.emailService       = emailService;
        this.notificationWriter = notificationWriter;
        this.newsWorker         = newsWorker;
        this.properties         = properties;
        this.tokenSigner        = tokenSigner;
    }

    /**
     * Runs on its own scheduler, never the shared one.
     *
     * <p>The other scheduled jobs in this service share a single thread — the hourly cleanup and the
     * 15-minute RSS fetch among them. Delivery work there would put news fetching behind whatever
     * latency the mail server or database happened to have.
     */
    @Scheduled(cron = "${alert.dispatch.cron:0 */5 * * * *}",
               scheduler = "alertTaskScheduler",
               zone = "${scheduler.timezone:Asia/Kolkata}")
    public void dispatch() {
        if (!properties.isEnabled()) return;

        if (properties.isEmailChannel() && !canSendEmail()) return;

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

    /**
     * Both email switches have to agree before a cycle runs on that channel.
     *
     * <p>Running with alerts on and sending off would find the same articles every cycle, log a
     * digest nobody receives and never stamp anything — an infinite repeat that looks like work.
     * And mailing someone a dead unsubscribe link invites a spam complaint, which costs the sending
     * domain far more than a missed digest.
     */
    private boolean canSendEmail() {
        if (!emailService.isEnabled()) {
            log.warn("Email channel selected but sending is off (app.email.enabled=false) - "
                   + "skipping cycle");
            return false;
        }
        if (!tokenSigner.isConfigured()) {
            log.warn("Email channel selected but alert.unsubscribe.secret is not set - "
                   + "skipping cycle");
            return false;
        }
        return true;
    }

    /** Exposed for tests and for a future admin trigger; carries no scheduling of its own. */
    public int runCycle() {
        ZonedDateTime now = ZonedDateTime.now(AlertProperties.IST);
        rollDayIfNeeded(now.toLocalDate());

        // Quiet hours hold an inbox, not a badge.
        if (properties.isEmailChannel() && properties.isQuietAt(now.toLocalTime())) {
            log.debug("Quiet hours - holding alerts until {}", properties.getQuietEnd());
            return 0;
        }

        long cutoff = Instant.now()
                             .minus(Duration.ofHours(properties.getMaxArticleAgeHours()))
                             .toEpochMilli();

        List<String> candidates = collector.candidateKeywords(cutoff);
        if (candidates.isEmpty()) return 0;

        // Notifications need no address; email needs a verified one.
        Map<String, List<SubscriberLookup.Subscriber>> subscribers =
            subscriberLookup.subscribersFor(candidates, properties.isEmailChannel());

        if (subscribers.isEmpty()) {
            log.debug("{} company(ies) have alertable news, but nobody subscribes to them",
                      candidates.size());
            return 0;
        }

        return properties.isEmailChannel()
             ? emailCycle(candidates, subscribers, cutoff, now)
             : notificationCycle(candidates, subscribers, cutoff);
    }

    // ─── Notification channel ────────────────────────────────────────────────

    /**
     * One row per subscriber per article.
     *
     * <p>No grouping, no pacing: a panel is read at a glance and holds as many rows as it holds.
     * An article is stamped once at least one notification for it exists, so the next cycle moves
     * on; recipients whose individual write failed are covered by the retry that leaves the article
     * unstamped when nothing at all was written.
     */
    private int notificationCycle(List<String> candidates,
                                  Map<String, List<SubscriberLookup.Subscriber>> subscribers,
                                  long cutoff) {
        int raised = 0;
        Map<String, Set<String>> delivered = new HashMap<>();

        for (String keyword : candidates) {
            List<SubscriberLookup.Subscriber> forCompany = subscribers.get(keyword);
            if (forCompany == null || forCompany.isEmpty()) continue;   // nobody follows it

            for (HotArticle article : collector.hotArticlesFor(keyword, cutoff)) {
                int created = notificationWriter.raise(article, forCompany);
                raised += created;

                // Stamped even when every recipient already had it: that means the work is done,
                // and leaving it unstamped would make every later cycle redo the same no-op.
                delivered.computeIfAbsent(keyword, k -> new HashSet<>()).add(article.link());
            }
        }

        int marked = markDelivered(delivered);
        if (raised > 0 || marked > 0) {
            log.info("Alert cycle complete - {} notification(s) raised, {} article(s) marked",
                     raised, marked);
        }
        return raised;
    }

    // ─── Email channel (dormant) ─────────────────────────────────────────────

    /** One digest per person, however many of their companies are in the news this cycle. */
    private int emailCycle(List<String> candidates,
                           Map<String, List<SubscriberLookup.Subscriber>> subscribers,
                           long cutoff,
                           ZonedDateTime now) {

        Map<Long, SubscriberLookup.Subscriber> people = new LinkedHashMap<>();
        Map<Long, List<HotArticle>> digests = new LinkedHashMap<>();

        for (String keyword : candidates) {
            List<SubscriberLookup.Subscriber> forCompany = subscribers.get(keyword);
            if (forCompany == null || forCompany.isEmpty()) continue;

            List<HotArticle> hot = collector.hotArticlesFor(keyword, cutoff);
            if (hot.isEmpty()) continue;                                // changed since the scan

            for (SubscriberLookup.Subscriber person : forCompany) {
                people.putIfAbsent(person.userId(), person);
                digests.computeIfAbsent(person.userId(), k -> new ArrayList<>()).addAll(hot);
            }
        }

        int sent = 0;
        Map<String, Set<String>> delivered = new HashMap<>();

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
                for (HotArticle a : articles) {
                    delivered.computeIfAbsent(a.keyword(), k -> new HashSet<>()).add(a.link());
                }
            }
        }

        int marked = markDelivered(delivered);
        if (sent > 0 || marked > 0) {
            log.info("Alert cycle complete - {} digest(s) sent, {} article(s) marked", sent, marked);
        }
        return sent;
    }

    // ─── pacing (email only) ─────────────────────────────────────────────────

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
     * Stamps delivered articles so they are never raised again.
     *
     * <p>Grouped by company because the write goes through {@link NewsWorker}, which holds a
     * per-company lock while it rewrites that company's news — the same lock the fetcher takes.
     * Writing without it would let a fetch landing at the same moment overwrite the stamp, or lose
     * the articles the fetch had just added.
     */
    private int markDelivered(Map<String, Set<String>> linksByKeyword) {
        if (linksByKeyword.isEmpty()) return 0;

        int marked = 0;
        for (Map.Entry<String, Set<String>> entry : linksByKeyword.entrySet()) {
            try {
                marked += newsWorker.markAlerted(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                // Delivery already happened. Failing to mark risks a repeat next cycle, which is
                // worth logging loudly but is not worth failing the run for.
                log.error("Could not mark {} article(s) for {} as alerted - a repeat is possible: {}",
                          entry.getValue().size(), entry.getKey(), e.getMessage());
            }
        }
        return marked;
    }
}
