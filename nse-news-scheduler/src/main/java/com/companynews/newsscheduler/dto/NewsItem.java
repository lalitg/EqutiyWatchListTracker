package com.companynews.newsscheduler.dto;
 
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
 
// This class represents ONE item inside the news JSON array.
// Example: one event like { date, summary }
//          one news  like { date, summary, link }
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsItem {
 
    private String date;      // e.g. "2026-03-05"
    private String summary;   // the news/event text
    private String link;      // URL to article (null for events)
}
