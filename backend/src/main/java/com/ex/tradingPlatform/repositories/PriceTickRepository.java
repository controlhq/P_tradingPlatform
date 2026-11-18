package com.ex.tradingPlatform.repositories;

import com.ex.tradingPlatform.entities.PriceTick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceTickRepository extends JpaRepository<PriceTick, Long> {

    /** Latest tick of the instrument; ticks with the same timestamp are ordered by id. */
    Optional<PriceTick> findFirstByInstrumentIdOrderByTsDescIdDesc(Long instrumentId);

    /** Latest tick of each given instrument in one query (instruments without ticks are absent). */
    @Query(value = """
            SELECT DISTINCT ON (instrument_id) *
            FROM price_ticks
            WHERE instrument_id IN (:instrumentIds)
            ORDER BY instrument_id, ts DESC, id DESC""", nativeQuery = true)
    List<PriceTick> findLatestByInstrumentIds(@Param("instrumentIds") Collection<Long> instrumentIds);
}
