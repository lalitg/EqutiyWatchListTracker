package com.equity.payment.dto;

/**
 * Response from POST /api/v1/subscriptions/create.
 * Frontend uses razorpaySubscriptionId to open the Razorpay checkout popup.
 * Frontend shows firstPaymentAmountPaise and renewalAmountPaise to the user
 * e.g. "Pay Rs.299 today, then Rs.499/month"
 */
public class CreateSubscriptionResponse {

    private String razorpaySubscriptionId;
    private String razorpayKeyId;
    private int    originalAmountPaise;
    private int    discountPaise;
    private int    firstPaymentAmountPaise;
    private int    renewalAmountPaise;
    private String currency = "INR";

    public CreateSubscriptionResponse(String razorpaySubscriptionId, String razorpayKeyId,
                                       int originalAmountPaise, int discountPaise,
                                       int firstPaymentAmountPaise, int renewalAmountPaise) {
        this.razorpaySubscriptionId = razorpaySubscriptionId;
        this.razorpayKeyId          = razorpayKeyId;
        this.originalAmountPaise    = originalAmountPaise;
        this.discountPaise          = discountPaise;
        this.firstPaymentAmountPaise = firstPaymentAmountPaise;
        this.renewalAmountPaise     = renewalAmountPaise;
    }

    public String getRazorpaySubscriptionId() { return razorpaySubscriptionId; }
    public String getRazorpayKeyId() { return razorpayKeyId; }
    public int getOriginalAmountPaise() { return originalAmountPaise; }
    public int getDiscountPaise() { return discountPaise; }
    public int getFirstPaymentAmountPaise() { return firstPaymentAmountPaise; }
    public int getRenewalAmountPaise() { return renewalAmountPaise; }
    public String getCurrency() { return currency; }
}
