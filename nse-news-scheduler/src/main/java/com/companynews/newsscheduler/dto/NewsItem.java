package com.companynews.newsscheduler.dto;

public class NewsItem {

    private String date;      // an_dt from NSE
    private String summary;   // attchmntText from NSE
    private String link;      // attchmntFile from NSE
    private String symbol;    // symbol from NSE — used internally, not stored in DB

    // Default constructor — required for Jackson deserialization
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

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
}
