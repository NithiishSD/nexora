package com.nexora.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// One customer's position, derived on read: outstanding over owed obligations (assumption Q2), the advance kept from
// payments, how many orders still have something to pay, and the date of the oldest unpaid obligation (null if none).
public record CustomerBalance(UUID customerId, BigDecimal outstanding, BigDecimal advance, long unpaidOrders,
                              LocalDate oldestUnpaidDate) {
}
