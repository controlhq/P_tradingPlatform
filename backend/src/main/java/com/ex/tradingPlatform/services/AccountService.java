package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.dto.AccountResponse;
import com.ex.tradingPlatform.entities.Account;
import com.ex.tradingPlatform.entities.Transaction;
import com.ex.tradingPlatform.entities.TransactionType;
import com.ex.tradingPlatform.exceptions.BusinessException;
import com.ex.tradingPlatform.repositories.AccountRepository;
import com.ex.tradingPlatform.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AccountService {

    static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    private static final int MONEY_SCALE = 4;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AccountResponse getForUser(Long userId) {
        return AccountResponse.from(accountRepository.findByUserId(userId).orElseThrow(AccountService::accountNotFound));
    }

    @Transactional
    public AccountResponse deposit(Long userId, BigDecimal amount) {
        BigDecimal value = amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        Account account = lockAccount(userId);
        Instant now = clock.instant();

        account.deposit(value, now);
        record(account, TransactionType.DEPOSIT, value, now);
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse withdraw(Long userId, BigDecimal amount) {
        BigDecimal value = amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        Account account = lockAccount(userId);
        if (value.compareTo(account.getAvailableBalance()) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, INSUFFICIENT_FUNDS,
                    "Amount exceeds available funds.");
        }
        Instant now = clock.instant();

        account.withdraw(value, now);
        record(account, TransactionType.WITHDRAW, value, now);
        return AccountResponse.from(account);
    }

    private Account lockAccount(Long userId) {
        return accountRepository.findByUserIdForUpdate(userId).orElseThrow(AccountService::accountNotFound);
    }

    private void record(Account account, TransactionType type, BigDecimal amount, Instant now) {
        transactionRepository.save(new Transaction(account.getUser(), type, amount, account.getBalance(), now));
    }

    private static BusinessException accountNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, ACCOUNT_NOT_FOUND, "Account not found.");
    }
}
