package com.ex.tradingPlatform.dto;

import com.ex.tradingPlatform.entities.Account;

import java.math.BigDecimal;

public record AccountResponse(BigDecimal balance, BigDecimal blockedBalance, BigDecimal available) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getBalance(), account.getBlockedBalance(), account.getAvailableBalance());
    }
}
