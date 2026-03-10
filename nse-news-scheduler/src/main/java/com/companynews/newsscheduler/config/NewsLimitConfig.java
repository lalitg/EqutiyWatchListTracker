package com.companynews.newsscheduler.config;
 
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
 
@Component
public class NewsLimitConfig {
 
    @Value("${news.limit.default:5}")
    private int defaultLimit;
 
    @Value("${news.limit.big:15}")
    private int bigLimit;
 
    // Reads 'RELIANCE,TCS,INFY,...' from properties and splits into a Set
    @Value("${news.limit.big-companies:}")
    private String bigCompaniesRaw;
 
    private Set<String> bigCompanies;
 
    // @PostConstruct runs once after Spring injects all @Value fields.
    // We use it to parse the comma-separated string into a Set.
    @jakarta.annotation.PostConstruct
    public void init() {
        bigCompanies = new HashSet<>();
        if (bigCompaniesRaw != null && !bigCompaniesRaw.isBlank()) {
            Arrays.stream(bigCompaniesRaw.split(","))
                  .map(String::trim)
                  .forEach(bigCompanies::add);
        }
    }
 
    // Call this to get the correct limit for any symbol.
    // Returns 15 for big companies, 5 for everyone else.
    public int getLimitFor(String symbol) {
        if (symbol == null) return defaultLimit;
        return bigCompanies.contains(symbol.toUpperCase())
               ? bigLimit : defaultLimit;
    }
}
