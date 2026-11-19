package com.ex.tradingPlatform.repositories;

import com.ex.tradingPlatform.entities.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    boolean existsBySymbol(String symbol);

    List<Instrument> findAllByActiveTrueOrderBySymbolAsc();

    List<Instrument> findAllByOrderBySymbolAsc();
}
