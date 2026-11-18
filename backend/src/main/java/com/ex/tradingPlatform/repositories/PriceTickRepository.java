package com.ex.tradingPlatform.repositories;

import com.ex.tradingPlatform.entities.PriceTick;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceTickRepository extends JpaRepository<PriceTick, Long> {

    /** Latest tick of the instrument; ticks with the same timestamp are ordered by id. */
    Optional<PriceTick> findFirstByInstrumentIdOrderByTsDescIdDesc(Long instrumentId);

    /** Newest ticks first, both bounds inclusive. */
    List<PriceTick> findByInstrumentIdAndTsBetweenOrderByTsDescIdDesc(
            Long instrumentId, Instant from, Instant to, Limit limit);

    /**
     * Deletes ticks older than {@code cutoff} except the latest tick of each instrument
     * (a tick is kept unless a newer one exists, using the BR18 ordering).
     */
    @Modifying
    @Query(value = """
            DELETE FROM price_ticks t
            WHERE t.ts < :cutoff
              AND EXISTS (SELECT 1 FROM price_ticks n
                          WHERE n.instrument_id = t.instrument_id
                            AND (n.ts > t.ts OR (n.ts = t.ts AND n.id > t.id)))""", nativeQuery = true)
    int deleteOlderThanExceptLatest(@Param("cutoff") Instant cutoff);

    /** Latest tick of each given instrument in one query (instruments without ticks are absent). */
    @Query(value = """
            SELECT DISTINCT ON (instrument_id) *
            FROM price_ticks
            WHERE instrument_id IN (:instrumentIds)
            ORDER BY instrument_id, ts DESC, id DESC""", nativeQuery = true)
    List<PriceTick> findLatestByInstrumentIds(@Param("instrumentIds") Collection<Long> instrumentIds);
}
