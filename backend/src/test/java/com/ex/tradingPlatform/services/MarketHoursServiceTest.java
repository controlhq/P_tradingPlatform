package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.config.MarketProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketHoursServiceTest {

    // 2026-11-16 is a Monday; Warsaw is UTC+1 in November and UTC+2 in July
    @ParameterizedTest(name = "{0} -> open={1}")
    @CsvSource({
            "2026-11-18T08:00:00Z, true",   // Wed 09:00 local, opening is inclusive
            "2026-11-18T12:00:00Z, true",   // Wed 13:00
            "2026-11-18T15:59:59Z, true",   // Wed 16:59:59
            "2026-11-18T07:59:59Z, false",  // Wed 08:59:59
            "2026-11-18T16:00:00Z, false",  // Wed 17:00, closing is exclusive
            "2026-11-20T12:00:00Z, true",   // Fri
            "2026-11-21T12:00:00Z, false",  // Sat
            "2026-11-22T12:00:00Z, false",  // Sun
            "2026-07-01T07:00:00Z, true",   // Wed 09:00 CEST
            "2026-07-01T06:59:59Z, false",  // Wed 08:59:59 CEST
            "2026-07-01T15:00:00Z, false"   // Wed 17:00 CEST
    })
    void BR23_isOpen_dependsOnTradingDayAndHourInWarsaw(Instant now, boolean expected) {
        assertThat(service(now, false).isOpen()).isEqualTo(expected);
    }

    @Test
    void BR23_alwaysOpen_isOpenOnWeekendAndHasNoNextChange() {
        MarketHoursService service = service(Instant.parse("2026-11-22T03:00:00Z"), true);

        assertThat(service.isOpen()).isTrue();
        assertThat(service.nextChange()).isEmpty();
    }

    @Test
    void BR23_nextChange_whenOpen_isTodaysClose() {
        assertThat(service(Instant.parse("2026-11-18T10:00:00Z"), false).nextChange())
                .contains(Instant.parse("2026-11-18T16:00:00Z"));
    }

    @Test
    void BR23_nextChange_beforeOpening_isTodaysOpen() {
        assertThat(service(Instant.parse("2026-11-18T06:00:00Z"), false).nextChange())
                .contains(Instant.parse("2026-11-18T08:00:00Z"));
    }

    @Test
    void BR23_nextChange_afterClose_isNextDaysOpen() {
        assertThat(service(Instant.parse("2026-11-18T16:00:00Z"), false).nextChange())
                .contains(Instant.parse("2026-11-19T08:00:00Z"));
    }

    @Test
    void BR23_nextChange_onFridayEvening_isMondayOpen() {
        assertThat(service(Instant.parse("2026-11-20T18:00:00Z"), false).nextChange())
                .contains(Instant.parse("2026-11-23T08:00:00Z"));
    }

    @Test
    void BR23_nextChange_acrossDstSwitch_usesLocalOpeningTime() {
        // Sat 2026-03-28; clocks move to CEST on Sunday, so Monday 09:00 local is 07:00Z
        assertThat(service(Instant.parse("2026-03-28T12:00:00Z"), false).nextChange())
                .contains(Instant.parse("2026-03-30T07:00:00Z"));
    }

    @Test
    void properties_openAfterClose_areRejected() {
        assertThatThrownBy(() -> new MarketProperties(LocalTime.of(17, 0), LocalTime.of(9, 0),
                ZoneId.of("Europe/Warsaw"), EnumSet.of(DayOfWeek.MONDAY), false, 3000, 0.005))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MarketHoursService service(Instant now, boolean alwaysOpen) {
        MarketProperties properties = new MarketProperties(
                LocalTime.of(9, 0), LocalTime.of(17, 0), ZoneId.of("Europe/Warsaw"),
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), alwaysOpen, 3000, 0.005);
        return new MarketHoursService(properties, Clock.fixed(now, ZoneOffset.UTC));
    }
}
