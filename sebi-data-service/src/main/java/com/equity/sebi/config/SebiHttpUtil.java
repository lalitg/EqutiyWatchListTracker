package com.equity.sebi.config;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Utility to create Jsoup connections that bypass SSL certificate validation.
 *
 * SEBI, NSE, and other Indian government sites commonly use certificate chains
 * not included in Java's default cacerts truststore. Fine to bypass for a POC
 * scraper that only reads public data.
 */
public final class SebiHttpUtil {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final SSLSocketFactory TRUST_ALL_FACTORY = buildTrustAllFactory();

    private SebiHttpUtil() {}

    /** Returns a pre-configured Jsoup Connection for the given URL. */
    public static Connection connect(String url) {
        return Jsoup.connect(url)
                .sslSocketFactory(TRUST_ALL_FACTORY)
                .userAgent(USER_AGENT)
                .header("Referer", "https://www.sebi.gov.in/")
                .ignoreContentType(true)
                .ignoreHttpErrors(false)
                .timeout(25_000);
    }

    private static SSLSocketFactory buildTrustAllFactory() {
        try {
            TrustManager[] trustAll = {
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return ctx.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build trust-all SSL factory", e);
        }
    }
}
