package com.nexora.finance.domain;

// A payment (expense, work record) counts while RECORDED; a reversal makes it REVERSED, never deletes it.
public enum RecordStatus {
    RECORDED,
    REVERSED,
    PENDING,
    BOUNCED
}
