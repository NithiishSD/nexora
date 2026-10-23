package com.nexora.parties.application;

import java.math.BigDecimal;

// A worker's default rates (FR-PARTY-14), copied into new work records; null when not set.
public record WorkerRates(BigDecimal rollingPerHour, BigDecimal warpingPerKg) {
}
