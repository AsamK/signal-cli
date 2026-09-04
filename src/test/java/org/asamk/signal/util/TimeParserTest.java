package org.asamk.signal.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeParserTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    // Thursday 2026-09-03 14:30:00 in Berlin (CEST, UTC+2)
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 9, 3, 14, 30, 0, 0, ZONE);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(), ZONE);

    private static Duration duration(String input) throws TimeParser.TimeParseException {
        return TimeParser.parseDuration(input);
    }

    private static ZonedDateTime timestamp(String input) throws TimeParser.TimeParseException {
        return Instant.ofEpochMilli(TimeParser.parseTimestamp(input, CLOCK)).atZone(ZONE);
    }

    @Test
    void parsesCompactDurations() throws Exception {
        assertEquals(Duration.ofMinutes(90), duration("90m"));
        assertEquals(Duration.ofMinutes(97), duration("1h37m"));
        assertEquals(Duration.ofMinutes(97), duration("1h 37m"));
        assertEquals(Duration.ofHours(2), duration("2h"));
        assertEquals(Duration.ofDays(1).plusHours(2).plusMinutes(30).plusSeconds(15), duration("1d 2h 30m 15s"));
        assertEquals(Duration.ofDays(14), duration("2w"));
        assertEquals(Duration.ofMillis(1500), duration("1500ms"));
    }

    @Test
    void parsesWordyDurations() throws Exception {
        assertEquals(Duration.ofHours(2), duration("2 hours"));
        assertEquals(Duration.ofMinutes(150), duration("2 hours 30 minutes"));
        assertEquals(Duration.ofMinutes(150), duration("2 hours, 30 minutes"));
        assertEquals(Duration.ofMinutes(150), duration("2 hours and 30 minutes"));
        assertEquals(Duration.ofHours(1), duration("an hour"));
        assertEquals(Duration.ofMinutes(1), duration("a minute"));
        assertEquals(Duration.ofHours(3), duration("for 3 hrs"));
        assertEquals(Duration.ofMinutes(45), duration("  45   MINUTES "));
    }

    @Test
    void parsesFractionalDurations() throws Exception {
        assertEquals(Duration.ofMinutes(90), duration("1.5h"));
        assertEquals(Duration.ofMinutes(90), duration("1,5 hours"));
        assertEquals(Duration.ofSeconds(30), duration("0.5m"));
    }

    @Test
    void parsesColonAndIsoDurations() throws Exception {
        assertEquals(Duration.ofMinutes(90), duration("1:30"));
        assertEquals(Duration.ofHours(1).plusMinutes(30).plusSeconds(10), duration("1:30:10"));
        assertEquals(Duration.ofMinutes(150), duration("PT2H30M"));
        assertEquals(Duration.ofMinutes(150), duration("pt2h30m"));
        assertEquals(Duration.ofDays(1).plusHours(12), duration("P1DT12H"));
    }

    @Test
    void rejectsInvalidDurations() {
        assertThrows(TimeParser.TimeParseException.class, () -> duration("90"));
        assertThrows(TimeParser.TimeParseException.class, () -> duration(""));
        assertThrows(TimeParser.TimeParseException.class, () -> duration("0m"));
        assertThrows(TimeParser.TimeParseException.class, () -> duration("2 fortnights"));
        assertThrows(TimeParser.TimeParseException.class, () -> duration("1:75"));
        assertThrows(TimeParser.TimeParseException.class, () -> duration("18:00pm"));
        assertThrows(TimeParser.TimeParseException.class, () -> duration("PT"));
        assertThrows(TimeParser.TimeParseException.class, () -> duration("soon"));
    }

    @Test
    void parsesUnixTimestamps() throws Exception {
        assertEquals(1_780_000_000_000L, TimeParser.parseTimestamp("1780000000", CLOCK));
        assertEquals(1_780_000_000_123L, TimeParser.parseTimestamp("1780000000123", CLOCK));
    }

    @Test
    void parsesIsoTimestamps() throws Exception {
        assertEquals(Instant.parse("2026-09-03T18:00:00Z").toEpochMilli(),
                TimeParser.parseTimestamp("2026-09-03T18:00:00Z", CLOCK));
        assertEquals(Instant.parse("2026-09-03T18:00:00.500Z").toEpochMilli(),
                TimeParser.parseTimestamp("2026-09-03T18:00:00.500Z", CLOCK));
        assertEquals(Instant.parse("2026-09-03T16:00:00Z").toEpochMilli(),
                TimeParser.parseTimestamp("2026-09-03T18:00:00+02:00", CLOCK));
        assertEquals(Instant.parse("2026-09-03T16:00:00Z").toEpochMilli(),
                TimeParser.parseTimestamp("2026-09-03T18:00:00+02:00[Europe/Berlin]", CLOCK));
    }

    @Test
    void parsesLocalDateTimes() throws Exception {
        assertEquals(NOW.withHour(18).withMinute(0), timestamp("2026-09-03T18:00"));
        assertEquals(NOW.withHour(18).withMinute(0), timestamp("2026-09-03 18:00"));
        assertEquals(NOW.withHour(18).withMinute(0).withSecond(30), timestamp("2026-09-03 18:00:30"));
        assertEquals(NOW.withHour(9).withMinute(5), timestamp("2026-09-03 9:05"));
        assertEquals(NOW.plusDays(2).withHour(0).withMinute(0), timestamp("2026-09-05"));
    }

    @Test
    void parsesTimesOfDayAsNextOccurrence() throws Exception {
        // later today
        assertEquals(NOW.withHour(18).withMinute(0), timestamp("18:00"));
        assertEquals(NOW.withHour(18).withMinute(0), timestamp("6pm"));
        assertEquals(NOW.withHour(18).withMinute(30), timestamp("6:30pm"));
        assertEquals(NOW.withHour(18).withMinute(30), timestamp("6:30 PM"));
        assertEquals(NOW.withHour(18).withMinute(30), timestamp("6:30 p.m."));
        assertEquals(NOW.withHour(14).withMinute(45).withSecond(10), timestamp("14:45:10"));
        assertEquals(NOW.withHour(18).withMinute(0), timestamp("until 18:00"));
        assertEquals(NOW.withHour(18).withMinute(0), timestamp("at 18:00"));
        // already passed today, so tomorrow
        assertEquals(NOW.plusDays(1).withHour(9).withMinute(0), timestamp("9:00"));
        assertEquals(NOW.plusDays(1).withHour(9).withMinute(0), timestamp("9am"));
        assertEquals(NOW.plusDays(1).withHour(12).withMinute(0), timestamp("noon"));
        assertEquals(NOW.plusDays(1).withHour(0).withMinute(0), timestamp("midnight"));
        assertEquals(NOW.plusDays(1).withHour(0).withMinute(0), timestamp("12am"));
        assertEquals(NOW.plusDays(1).withHour(12).withMinute(0), timestamp("12pm"));
        // exactly now counts as passed
        assertEquals(NOW.plusDays(1), timestamp("14:30"));
    }

    @Test
    void parsesRelativeDays() throws Exception {
        assertEquals(NOW.plusDays(1).withHour(0).withMinute(0), timestamp("tomorrow"));
        assertEquals(NOW.plusDays(1).withHour(9).withMinute(0), timestamp("tomorrow 9:00"));
        assertEquals(NOW.plusDays(1).withHour(9).withMinute(0), timestamp("tomorrow at 9am"));
        assertEquals(NOW.plusDays(1).withHour(12).withMinute(0), timestamp("tomorrow noon"));
        // "today" is explicit and doesn't roll over
        assertEquals(NOW.withHour(9).withMinute(0), timestamp("today 9:00"));
    }

    @Test
    void rejectsInvalidTimestamps() {
        assertThrows(TimeParser.TimeParseException.class, () -> timestamp(""));
        assertThrows(TimeParser.TimeParseException.class, () -> timestamp("6"));
        assertThrows(TimeParser.TimeParseException.class, () -> timestamp("25:00"));
        assertThrows(TimeParser.TimeParseException.class, () -> timestamp("13pm"));
        assertThrows(TimeParser.TimeParseException.class, () -> timestamp("2026-13-01"));
        assertThrows(TimeParser.TimeParseException.class, () -> timestamp("90m"));
        assertThrows(TimeParser.TimeParseException.class, () -> timestamp("later"));
    }

    @Test
    void detectsDurations() {
        assertTrue(TimeParser.looksLikeDuration("90m"));
        assertTrue(TimeParser.looksLikeDuration("2 hours"));
        assertTrue(TimeParser.looksLikeDuration("2 hours and 30 minutes"));
        assertFalse(TimeParser.looksLikeDuration("tomorrow"));
        assertFalse(TimeParser.looksLikeDuration("6pm"));
        assertFalse(TimeParser.looksLikeDuration("1780000000"));
    }

    @Test
    void formatsDurations() {
        assertEquals("2h 30m", TimeParser.formatDuration(Duration.ofMinutes(150)));
        assertEquals("1d 2h 30m 15s", TimeParser.formatDuration(Duration.ofDays(1).plusHours(2).plusMinutes(30).plusSeconds(15)));
        assertEquals("0s", TimeParser.formatDuration(Duration.ZERO));
        assertEquals("45s", TimeParser.formatDuration(Duration.ofMillis(45_999)));
        assertEquals("-5m", TimeParser.formatDuration(Duration.ofMinutes(-5)));
    }
}
