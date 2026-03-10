package com.companynews.newsscheduler.dto;
 
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
 
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnnouncementItem {
 
    @JsonProperty("symbol")
    private String symbol;
 
    // sm_name = Security/Company name
    @JsonProperty("sm_name")
    private String smName;
 
    // attchmntText = the main text of the announcement
    @JsonProperty("attchmntText")
    private String attchmntText;
 
    // attchmntFile = link to the PDF attachment
    @JsonProperty("attchmntFile")
    private String attchmntFile;
 
    // an_dt = announcement date
    @JsonProperty("an_dt")
    private String anDt;
}
