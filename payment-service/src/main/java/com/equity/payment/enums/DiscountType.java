package com.equity.payment.enums;

public enum DiscountType {
    /** Fixed amount off in paise. discount_value = amount to subtract. */
    FLAT,
    /** Percentage off. discount_value = 0-100. max_discount_paise caps it. */
    PERCENTAGE
}
