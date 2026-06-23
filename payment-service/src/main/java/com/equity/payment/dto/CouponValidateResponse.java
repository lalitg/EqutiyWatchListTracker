package com.equity.payment.dto;

/**
 * Response from POST /api/v1/coupons/validate.
 * Shows the user their discounted price before they pay.
 */
public class CouponValidateResponse {

    private boolean valid;
    private String  couponCode;
    private String  discountType;
    private int     originalAmountPaise;
    private int     discountPaise;
    private int     finalAmountPaise;
    private String  originalDisplay;
    private String  discountDisplay;
    private String  finalDisplay;
    private String  message;

    public CouponValidateResponse(boolean valid, String couponCode, String discountType,
                                   int originalAmountPaise, int discountPaise, int finalAmountPaise) {
        this.valid               = valid;
        this.couponCode          = couponCode;
        this.discountType        = discountType;
        this.originalAmountPaise = originalAmountPaise;
        this.discountPaise       = discountPaise;
        this.finalAmountPaise    = finalAmountPaise;
        this.originalDisplay     = formatPaise(originalAmountPaise);
        this.discountDisplay     = formatPaise(discountPaise);
        this.finalDisplay        = formatPaise(finalAmountPaise);
        this.message             = "You save " + discountDisplay + " with code " + couponCode + "!";
    }

    private String formatPaise(int paise) {
        return "Rs." + (paise / 100);
    }

    public boolean isValid() { return valid; }
    public String getCouponCode() { return couponCode; }
    public String getDiscountType() { return discountType; }
    public int getOriginalAmountPaise() { return originalAmountPaise; }
    public int getDiscountPaise() { return discountPaise; }
    public int getFinalAmountPaise() { return finalAmountPaise; }
    public String getOriginalDisplay() { return originalDisplay; }
    public String getDiscountDisplay() { return discountDisplay; }
    public String getFinalDisplay() { return finalDisplay; }
    public String getMessage() { return message; }
}
