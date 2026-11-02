package com.equity.payment.dto;

import com.equity.payment.enums.PlanTier;

/**
 * Response for GET /api/v1/plans.
 * razorpayPlanId is intentionally excluded — internal detail, not for frontend.
 */
public class PlanResponse {

    private Long   id;
    private String name;
    private String displayName;
    private String description;
    private int    pricePaise;
    private String priceDisplay;
    private String billingCycle;
    private int    durationDays;
    private String tier;

    public PlanResponse(Long id, String name, String displayName, String description,
                         int pricePaise, String billingCycle, int durationDays, String tier) {
        this.id           = id;
        this.name         = name;
        this.displayName  = displayName;
        this.description  = description;
        this.pricePaise   = pricePaise;
        this.priceDisplay = "Rs." + (pricePaise / 100);
        this.billingCycle = billingCycle;
        this.durationDays = durationDays;
        // Normalised, so the pricing page never has to cope with "advanced", " Basic" or a null.
        this.tier         = PlanTier.fromName(tier).name();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getPricePaise() { return pricePaise; }
    public String getPriceDisplay() { return priceDisplay; }
    public String getBillingCycle() { return billingCycle; }
    public int getDurationDays() { return durationDays; }
    public String getTier() { return tier; }
}
