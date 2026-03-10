package com.companynews.newsscheduler.dto;
 
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
 
// @JsonIgnoreProperties(ignoreUnknown = true) is CRITICAL:
// The NSE API returns many more fields than we need.
// This annotation tells Jackson: ignore any fields not listed here.
// Without it, the app would crash if NSE adds a new field.
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventCalendarItem {
 
    // Maps the JSON key 'symbol' to this Java field
    @JsonProperty("symbol")
    private String symbol;
 
    // Maps the JSON key 'company' to this Java field
    @JsonProperty("company")
    private String company;
 
    // 'bm_desc' = Board Meeting Description - this is our news text
    @JsonProperty("bm_desc")
    private String bmDesc;
 
    // The date of the event
    @JsonProperty("date")
    private String date;
}
