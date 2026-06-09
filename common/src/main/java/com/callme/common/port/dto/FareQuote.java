package com.callme.common.port.dto;

import com.callme.common.shared.Money;

public record FareQuote(Money amount, double distanceKm, FareBreakdown breakdown) {
}
