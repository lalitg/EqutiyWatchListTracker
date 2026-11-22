package com.companycode.nse.dto;

/** Represents one Domestic Market page sector tab. */
public class SectorTabDto {

    private String displayName;
    private String newsKeyword;

    public SectorTabDto(String displayName, String newsKeyword) {
        this.displayName = displayName;
        this.newsKeyword = newsKeyword;
    }

    public String getDisplayName() { return displayName; }
    public String getNewsKeyword() { return newsKeyword; }
}
