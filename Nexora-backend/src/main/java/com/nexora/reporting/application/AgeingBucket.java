package com.nexora.reporting.application;

// How long a supplier amount is overdue (FR-RPT-05): not yet due, 1-7, 8-15, over 15 days past the due date.
public enum AgeingBucket {
    NOT_DUE, D1_7, D8_15, OVER_15;

    public static AgeingBucket of(int overdueDays) {
        if (overdueDays <= 0) {
            return NOT_DUE;
        }
        return overdueDays <= 7 ? D1_7 : overdueDays <= 15 ? D8_15 : OVER_15;
    }
}
