package com.companynews.newsscheduler.alert;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes and sends one digest.
 *
 * <h2>What the email leads with</h2>
 * The headline and a link to the article, with the score beside the ticker as context. Our model is
 * right about three times in four, so the reader needs the actual article to judge — an email that
 * led with "HDFCBANK −3.9" and buried the story would be asking them to trust a number that is
 * wrong a quarter of the time.
 *
 * <h2>Good news and bad news are separated</h2>
 * The mentor's design merges the two hot lists immediately after building them. Keeping them apart
 * costs nothing and is far easier to read at a glance than one list where the sign of a number is
 * the only thing distinguishing a deal win from a regulatory probe.
 *
 * <h2>Unsubscribe is in every message</h2>
 * Per company and for everything, in the body and in the {@code List-Unsubscribe} headers that let
 * Gmail and Outlook offer their own one-click unsubscribe. A reader who cannot easily stop the mail
 * reaches for "spam" instead, which costs the sending domain far more than losing one subscriber.
 */
@Service
public class AlertEmailService {

    private static final Logger log = LogManager.getLogger(AlertEmailService.class);

    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(AlertProperties.IST);

    private static final String DISCLAIMER =
        "Scores are generated automatically by an AI model reading news headlines, on a -5 to +5 "
      + "scale. They are for information only and are not investment advice or a recommendation to "
      + "buy, sell or hold any security. The model can be wrong and headlines can mislead - read "
      + "the article.";

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AlertProperties properties;
    private final UnsubscribeTokenSigner tokenSigner;
    private final boolean emailEnabled;

    public AlertEmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                             AlertProperties properties,
                             UnsubscribeTokenSigner tokenSigner,
                             @Value("${app.email.enabled:false}") boolean emailEnabled) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties         = properties;
        this.tokenSigner        = tokenSigner;
        this.emailEnabled       = emailEnabled;
    }

    /** @return whether this environment can actually deliver mail */
    public boolean isEnabled() {
        return emailEnabled;
    }

    /**
     * Sends one person their digest.
     *
     * @return true if the message reached the mail server; false when email is switched off in this
     *         environment (the digest is logged instead) or the send failed
     */
    public boolean send(SubscriberLookup.Subscriber subscriber, List<HotArticle> articles) {
        if (articles.isEmpty()) return false;

        String subject = subject(articles);
        String body    = body(subscriber, articles);

        if (!emailEnabled) {
            // The local path: no SMTP credentials anywhere, and the whole digest visible in the log
            // so the flow can still be walked end to end.
            log.info("Email disabled - would send to={} subject=[{}]\n{}",
                     subscriber.email(), subject, body);
            return false;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.error("Alerts are enabled but no mail sender is configured - set spring.mail.host. "
                    + "Digest for {} was NOT sent", subscriber.email());
            return false;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(subscriber.email());
            helper.setSubject(subject);
            helper.setText(body, false);
            helper.setFrom(new InternetAddress(properties.getFromAddress(), properties.getFromName()));

            // Lets a mail client offer its own unsubscribe button, which readers reach for before
            // they reach for "report spam".
            String unsubscribeUrl = unsubscribeLink(subscriber.userId(), UnsubscribeTokenSigner.ALL_SCOPE);
            message.setHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
            message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");

            sender.send(message);
            log.info("Alert digest sent to userId={} ({} article(s))",
                     subscriber.userId(), articles.size());
            return true;
        } catch (Exception e) {
            log.error("Alert digest to userId={} failed: {}", subscriber.userId(), e.getMessage(), e);
            return false;
        }
    }

    // ─── composition ─────────────────────────────────────────────────────────

    /**
     * Names the companies rather than counting them: an inbox list shows the subject only, and
     * "2 alerts: INFY, HDFCBANK" is actionable where "You have new alerts" is not.
     */
    String subject(List<HotArticle> articles) {
        List<String> symbols = new ArrayList<>();
        for (HotArticle a : articles) {
            if (!symbols.contains(a.keyword())) symbols.add(a.keyword());
        }

        String named = String.join(", ", symbols.size() > 3 ? symbols.subList(0, 3) : symbols);
        if (symbols.size() > 3) named += " and " + (symbols.size() - 3) + " more";

        return articles.size() + (articles.size() == 1 ? " alert: " : " alerts: ") + named;
    }

    String body(SubscriberLookup.Subscriber subscriber, List<HotArticle> articles) {
        List<HotArticle> good = articles.stream().filter(HotArticle::isPositive).toList();
        List<HotArticle> bad  = articles.stream().filter(a -> !a.isPositive()).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("Hi ").append(subscriber.name() == null ? "there" : subscriber.name()).append(",\n\n");
        sb.append("News on the companies you follow:\n");

        appendSection(sb, "GOOD NEWS", good);
        appendSection(sb, "BAD NEWS", bad);

        sb.append("\n").append(DISCLAIMER).append("\n\n");
        sb.append("Manage your alerts: ").append(properties.getBaseUrl()).append("/watchlist\n");

        for (String symbol : articles.stream().map(HotArticle::keyword).distinct().toList()) {
            sb.append("Stop alerts for ").append(symbol).append(": ")
              .append(unsubscribeLink(subscriber.userId(), symbol)).append("\n");
        }
        sb.append("Stop all alerts: ")
          .append(unsubscribeLink(subscriber.userId(), UnsubscribeTokenSigner.ALL_SCOPE)).append("\n");

        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String title, List<HotArticle> articles) {
        if (articles.isEmpty()) return;
        sb.append("\n").append(title).append("\n");
        for (HotArticle a : articles) {
            sb.append("\n  ").append(a.keyword()).append("  ")
              .append(a.score() > 0 ? "+" : "").append(String.format("%.1f", a.score())).append("\n");
            sb.append("  ").append(a.headline() == null ? "(no headline)" : a.headline()).append("\n");
            sb.append("  ").append(TIME.format(Instant.ofEpochMilli(a.publishedAt())))
              .append(" IST - ").append(a.link()).append("\n");
        }
    }

    private String unsubscribeLink(Long userId, String scope) {
        String token = tokenSigner.sign(userId, scope);
        return properties.getBaseUrl() + "/unsubscribe?token="
             + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
