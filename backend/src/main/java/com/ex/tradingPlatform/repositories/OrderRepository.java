package com.ex.tradingPlatform.repositories;

import com.ex.tradingPlatform.entities.Order;
import com.ex.tradingPlatform.entities.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Read queries fetch the instrument too, because every response includes its symbol. */
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "instrument")
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "instrument")
    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "instrument")
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndStatus(Long userId, OrderStatus status);

    /** Pending orders of an instrument in the order they were placed; the user is needed to lock the account. */
    @EntityGraph(attributePaths = "user")
    List<Order> findByInstrumentIdAndStatusOrderByCreatedAtAscIdAsc(Long instrumentId, OrderStatus status);
}
