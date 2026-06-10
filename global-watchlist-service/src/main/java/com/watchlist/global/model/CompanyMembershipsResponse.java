package com.watchlist.global.model;

import java.util.List;

public class CompanyMembershipsResponse {

    private String symbol;
    private String companyName;
    private List<IndexLabel> memberships;

    public CompanyMembershipsResponse(String symbol, String companyName, List<IndexLabel> memberships) {
        this.symbol      = symbol;
        this.companyName = companyName;
        this.memberships = memberships;
    }

    public String getSymbol()               { return symbol; }
    public String getCompanyName()          { return companyName; }
    public List<IndexLabel> getMemberships(){ return memberships; }

    public static class IndexLabel {
        private String nseKey;
        private String displayName;
        private String type; // "SECTOR" or "DOMESTIC"

        public IndexLabel(String nseKey, String displayName, String type) {
            this.nseKey      = nseKey;
            this.displayName = displayName;
            this.type        = type;
        }

        public String getNseKey()     { return nseKey; }
        public String getDisplayName(){ return displayName; }
        public String getType()       { return type; }
    }
}
