package com.ex.tradingPlatform.repositories;

import com.ex.tradingPlatform.entities.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByUserIdAndInstrumentId(Long userId, Long instrumentId);
}
