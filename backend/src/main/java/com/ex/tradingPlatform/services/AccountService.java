package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.dto.AccountResponse;
import com.ex.tradingPlatform.entities.Account;
import com.ex.tradingPlatform.exceptions.BusinessException;
import com.ex.tradingPlatform.repositories.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public AccountResponse getForUser(Long userId) {
        return AccountResponse.from(findOrThrow(userId));
    }

    private Account findOrThrow(Long userId) {
        return accountRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, ACCOUNT_NOT_FOUND, "Account not found."));
    }
}
