package com.ex.tradingPlatform.repositories;

import com.ex.tradingPlatform.IntegrationTest;
import com.ex.tradingPlatform.entities.Transaction;
import com.ex.tradingPlatform.entities.TransactionType;
import com.ex.tradingPlatform.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionPersistenceTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    UserRepository userRepository;

    @Autowired
    TransactionRepository transactionRepository;

    private User user;

    @BeforeEach
    void createUser() {
        user = userRepository.save(new User("john@example.com", "hash", T0));
    }

    @Test
    void deposit_isPersistedWithBalanceAfter() {
        Transaction saved = transactionRepository.save(
                new Transaction(user, TransactionType.DEPOSIT, new BigDecimal("100.5000"), new BigDecimal("100.5000"), T0));

        Transaction loaded = transactionRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(loaded.getAmount()).isEqualByComparingTo("100.5");
        assertThat(loaded.getBalanceAfter()).isEqualByComparingTo("100.5");
        assertThat(loaded.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void BR27_nonPositiveAmount_isRejectedByDb() {
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(
                new Transaction(user, TransactionType.DEPOSIT, BigDecimal.ZERO, BigDecimal.ZERO, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void BR27_negativeBalanceAfter_isRejectedByDb() {
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(
                new Transaction(user, TransactionType.WITHDRAW, BigDecimal.ONE, new BigDecimal("-1"), T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void BR27_buyWithoutOrder_isRejectedByDb() {
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(
                new Transaction(user, TransactionType.BUY, BigDecimal.ONE, BigDecimal.ONE, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
