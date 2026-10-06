package com.companynews.newsscheduler.alert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Everything tunable about news alerts, in one place.
 *
 * <p>The defaults are the settled decisions, not placeholders: ±3.5 on the −5…+5 scale, a dispatch
 * every five minutes, quiet hours from 22:00 to 08:00 Indian time, and a daily ceiling of twelve
 * emails past which a user's remaining alerts are grouped hourly rather than dropped.
 */
@Component
public class AlertProperties {

    /** Indian market time — every schedule and quiet-hours decision is made in it. */
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * Master switch. False stops every send without a deploy, which is the lever to pull first when
     * anything about alerts looks wrong in production.
     */
    @Value("${alert.enabled:false}")
    private boolean enabled;

    /**
     * How extreme a score has to be. 3.5 on our scale is roughly the mentor document's ±0.75 on a
     * −1…+1 scale, and catches about 29% of scored articles.
     */
    @Value("${alert.threshold:3.5}")
    private double threshold;

    /**
     * Nothing older than this can ever trigger an alert, however recently it was scored.
     *
     * <p>This is the guard that makes a backfill safe: re-scoring thousands of historical headlines
     * must not mail anybody about months-old news.
     */
    @Value("${alert.max-article-age-hours:24}")
    private int maxArticleAgeHours;

    /** Start of the overnight hold. */
    @Value("${alert.quiet-hours.start:22:00}")
    private String quietStart;

    /** End of the overnight hold — held articles go out in one digest at this time. */
    @Value("${alert.quiet-hours.end:08:00}")
    private String quietEnd;

    /**
     * After this many emails in one day, a user's further alerts are batched hourly instead of
     * every cycle. Nothing is ever dropped — only paced.
     */
    @Value("${alert.daily-email-cap:12}")
    private int dailyEmailCap;

    /** Safety valve on one cycle's work, so a pathological run cannot monopolise the server. */
    @Value("${alert.max-companies-per-cycle:200}")
    private int maxCompaniesPerCycle;

    /** Sender address. Must be on the SES-verified domain. */
    @Value("${app.email.from:noreply@niveshflow.com}")
    private String fromAddress;

    @Value("${app.email.from-name:NiveshFlow}")
    private String fromName;

    /** Site root, used to build article, unsubscribe and manage links. */
    @Value("${app.base-url:https://niveshflow.com}")
    private String baseUrl;

    public boolean isEnabled()            { return enabled; }
    public double getThreshold()          { return threshold; }
    public int getMaxArticleAgeHours()    { return maxArticleAgeHours; }
    public int getDailyEmailCap()         { return dailyEmailCap; }
    public int getMaxCompaniesPerCycle()  { return maxCompaniesPerCycle; }
    public String getFromAddress()        { return fromAddress; }
    public String getFromName()           { return fromName; }

    public String getBaseUrl() {
        return baseUrl != null && baseUrl.endsWith("/")
             ? baseUrl.substring(0, baseUrl.length() - 1)
             : baseUrl;
    }

    public LocalTime getQuietStart() { return LocalTime.parse(quietStart); }
    public LocalTime getQuietEnd()   { return LocalTime.parse(quietEnd); }

    /**
     * Whether sending is held at this moment.
     *
     * <p>Written to handle a window that crosses midnight, which the obvious comparison gets wrong:
     * 22:00–08:00 means "after 22:00 <em>or</em> before 08:00", not "between" them.
     */
    public boolean isQuietAt(LocalTime now) {
        LocalTime start = getQuietStart();
        LocalTime end   = getQuietEnd();
        if (start.equals(end)) return false;                       // no quiet period configured
        return start.isBefore(end)
             ? !now.isBefore(start) && now.isBefore(end)           // same-day window
             : !now.isBefore(start) || now.isBefore(end);          // crosses midnight
    }

    /** True at the moment the overnight hold lifts, when held articles are released. */
    public boolean isQuietEndingAt(LocalTime now, int withinMinutes) {
        LocalTime end = getQuietEnd();
        int minutesNow = now.getHour() * 60 + now.getMinute();
        int minutesEnd = end.getHour() * 60 + end.getMinute();
        int delta = minutesNow - minutesEnd;
        return delta >= 0 && delta < withinMinutes;
    }
}
