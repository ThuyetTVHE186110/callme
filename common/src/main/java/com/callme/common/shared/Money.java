package com.callme.common.shared;

import java.math.BigDecimal;
import java.util.Currency;

public record Money(BigDecimal amount, Currency currency) {

    public static Money vnd(BigDecimal amount) {
        return new Money(amount, Currency.getInstance("VND"));
    }
}
