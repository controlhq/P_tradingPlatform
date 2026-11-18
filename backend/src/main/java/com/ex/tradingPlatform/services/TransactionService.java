package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.dto.PageResponse;
import com.ex.tradingPlatform.dto.TransactionResponse;
import com.ex.tradingPlatform.entities.Transaction;
import com.ex.tradingPlatform.entities.TransactionType;
import com.ex.tradingPlatform.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    // id breaks ties between transactions created at the same instant
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TransactionRepository transactionRepository;

    /** The user's own transactions, newest first; {@code type} is an optional filter. */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(Long userId, TransactionType type, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<Transaction> result = type == null
                ? transactionRepository.findByUserId(userId, pageable)
                : transactionRepository.findByUserIdAndType(userId, type, pageable);
        return PageResponse.from(result.map(TransactionResponse::from));
    }
}
