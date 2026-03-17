package com.companynews.newsscheduler.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class NewsItem {

    private String date;
    private String summary;
    private String link;

    // @JsonIgnore means Jackson will NOT include this field
    // when serializing to JSON for DB storage or API response.
    // symbol is only used internally in memory to group announcements
    // by company — it is never stored in DB or returned in API.
    // The keyword column in company_news already identifies the company.
    @JsonIgnore
    private String symbol;

    public NewsItem() {}

    public NewsItem(String date, String summary, String link) {
        this.date = date;
        this.summary = summary;
        this.link = link;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    @JsonIgnore
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
}
