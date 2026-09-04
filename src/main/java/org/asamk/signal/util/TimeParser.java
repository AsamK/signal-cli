package org.asamk.signal.util;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses user supplied durations and points in time.
 * <p>
 * Durations: {@code 90m}, {@code 1h37m}, {@code 2 hours 30 minutes}, {@code 1.5h}, {@code 1:30} (h:mm),
 * {@code 1:30:00} (h:mm:ss), ISO-8601 ({@code PT2H30M}, {@code P1DT12H}).
 * <p>
 * Points in time: unix timestamps in seconds or milliseconds, ISO-8601 timestamps with or without zone,
 * {@code YYYY-MM-DD [HH:MM[:SS]]}, times of day ({@code 18:00}, {@code 6pm}, {@code 6:30pm}, {@code noon},
 * {@code midnight}), optionally prefixed with {@code today} or {@code tomorrow}.
 * Times without a date refer to the next occurrence of that time.
 */
public final class TimeParser {

    private static final Pattern DURATION_TOKEN = Pattern.compile("(\\d+(?:[.,]\\d+)?|\\ban?\\b)\\s*([a-zA-Z]+)");
    private static final Pattern DURATION_ONLY = Pattern.compile("^(?:(?:\\d+(?:[.,]\\d+)?|an?)\\s*[a-zA-Z]+\\s*)+$");
    private static final Pattern DURATION_COLON = Pattern.compile("^(\\d+):(\\d{1,2})(?::(\\d{1,2}))?$");
    private static final Pattern UNIX_TIMESTAMP = Pattern.compile("^\\d{9,}$");
    private static final Pattern TIME_OF_DAY = Pattern.compile(
            "^(\\d{1,2})(?::(\\d{2}))?(?::(\\d{2}))?\\s*([ap]\\.?m\\.?)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_TIME = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})(?:[tT ](\\d{1,2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?))?$");

    private static final Map<String, Duration> UNITS = Map.ofEntries(Map.entry("ms", Duration.ofMillis(1)),
            Map.entry("msec", Duration.ofMillis(1)),
            Map.entry("msecs", Duration.ofMillis(1)),
            Map.entry("milli", Duration.ofMillis(1)),
            Map.entry("millis", Duration.ofMillis(1)),
            Map.entry("millisecond", Duration.ofMillis(1)),
            Map.entry("milliseconds", Duration.ofMillis(1)),
            Map.entry("s", Duration.ofSeconds(1)),
            Map.entry("sec", Duration.ofSeconds(1)),
            Map.entry("secs", Duration.ofSeconds(1)),
            Map.entry("second", Duration.ofSeconds(1)),
            Map.entry("seconds", Duration.ofSeconds(1)),
            Map.entry("m", Duration.ofMinutes(1)),
            Map.entry("min", Duration.ofMinutes(1)),
            Map.entry("mins", Duration.ofMinutes(1)),
            Map.entry("minute", Duration.ofMinutes(1)),
            Map.entry("minutes", Duration.ofMinutes(1)),
            Map.entry("h", Duration.ofHours(1)),
            Map.entry("hr", Duration.ofHours(1)),
            Map.entry("hrs", Duration.ofHours(1)),
            Map.entry("hour", Duration.ofHours(1)),
            Map.entry("hours", Duration.ofHours(1)),
            Map.entry("d", Duration.ofDays(1)),
            Map.entry("day", Duration.ofDays(1)),
            Map.entry("days", Duration.ofDays(1)),
            Map.entry("w", Duration.ofDays(7)),
            Map.entry("wk", Duration.ofDays(7)),
            Map.entry("wks", Duration.ofDays(7)),
            Map.entry("week", Duration.ofDays(7)),
            Map.entry("weeks", Duration.ofDays(7)));

    private TimeParser() {
    }

    public static Duration parseDuration(final String input) throws TimeParseException {
        var text = normalize(input);
        text = stripPrefix(text, "for ");
        text = stripPrefix(text, "in ");
        if (text.isEmpty()) {
            throw new TimeParseException("Duration is empty");
        }

        if (text.startsWith("p")) {
            try {
                return checkPositive(Duration.parse(text.toUpperCase(Locale.ROOT)));
            } catch (DateTimeParseException e) {
                throw new TimeParseException("Invalid ISO-8601 duration: " + input);
            }
        }

        final var colonMatcher = DURATION_COLON.matcher(text);
        if (colonMatcher.matches()) {
            final var hours = Long.parseLong(colonMatcher.group(1));
            final var minutes = Long.parseLong(colonMatcher.group(2));
            final var seconds = colonMatcher.group(3) == null ? 0 : Long.parseLong(colonMatcher.group(3));
            if (minutes >= 60 || seconds >= 60) {
                throw new TimeParseException("Invalid duration: " + input);
            }
            return checkPositive(Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds));
        }

        if (text.chars().allMatch(Character::isDigit)) {
            throw new TimeParseException("Duration \""
                    + input
                    + "\" has no unit, use e.g. \""
                    + text
                    + "m\" for minutes or \""
                    + text
                    + "h\" for hours");
        }

        // Separators between the parts: "2 hours, 30 minutes" / "2 hours and 30 minutes"
        text = text.replaceAll("\\band\\b|(?<!\\d),|,(?!\\d)", " ").replaceAll("\\s+", " ").trim();
        if (!DURATION_ONLY.matcher(text).matches()) {
            throw new TimeParseException("Invalid duration: " + input);
        }
        final var matcher = DURATION_TOKEN.matcher(text);
        var result = Duration.ZERO;
        while (matcher.find()) {
            final var amountText = matcher.group(1);
            final var unitText = matcher.group(2).toLowerCase(Locale.ROOT);
            final var unit = UNITS.get(unitText);
            if (unit == null) {
                throw new TimeParseException("Unknown duration unit \"" + unitText + "\" in: " + input);
            }
            final double amount = amountText.equals("a") || amountText.equals("an")
                    ? 1
                    : Double.parseDouble(amountText.replace(',', '.'));
            result = result.plusMillis(Math.round(amount * unit.toMillis()));
        }
        return checkPositive(result);
    }

    /**
     * Parses a point in time, returning it as milliseconds since epoch.
     */
    public static long parseTimestamp(final String input, final Clock clock) throws TimeParseException {
        var text = normalize(input);
        text = stripPrefix(text, "until ");
        text = stripPrefix(text, "at ");
        text = stripPrefix(text, "@");
        if (text.isEmpty()) {
            throw new TimeParseException("Timestamp is empty");
        }
        final var zone = clock.getZone();
        final var now = ZonedDateTime.now(clock);

        if (UNIX_TIMESTAMP.matcher(text).matches()) {
            final long value;
            try {
                value = Long.parseLong(text);
            } catch (NumberFormatException e) {
                throw new TimeParseException("Invalid unix timestamp: " + input);
            }
            // Anything below 1e11 is in seconds (that's the year 5138), above it's milliseconds
            return value < 100_000_000_000L ? value * 1000 : value;
        }

        try {
            return Instant.parse(input.trim()).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(input.trim()).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(input.trim()).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        final var dateTimeMatcher = DATE_TIME.matcher(text);
        if (dateTimeMatcher.matches()) {
            try {
                final var date = LocalDate.parse(dateTimeMatcher.group(1));
                final var time = dateTimeMatcher.group(2) == null
                        ? LocalTime.MIDNIGHT
                        : LocalTime.parse(padHour(dateTimeMatcher.group(2)));
                return LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli();
            } catch (DateTimeException e) {
                throw new TimeParseException("Invalid date/time: " + input);
            }
        }

        var dayOffset = 0;
        var explicitDay = false;
        if (text.startsWith("tomorrow")) {
            dayOffset = 1;
            explicitDay = true;
            text = text.substring("tomorrow".length()).trim();
            text = stripPrefix(text, "at ");
        } else if (text.startsWith("today")) {
            explicitDay = true;
            text = text.substring("today".length()).trim();
            text = stripPrefix(text, "at ");
        }
        final var day = now.toLocalDate().plusDays(dayOffset);

        final LocalTime time;
        if (text.isEmpty()) {
            if (!explicitDay) {
                throw new TimeParseException("Invalid timestamp: " + input);
            }
            time = LocalTime.MIDNIGHT;
        } else {
            time = parseTimeOfDay(text, input);
        }

        var result = ZonedDateTime.of(day, time, zone);
        if (!explicitDay && !result.isAfter(now)) {
            // A bare time of day refers to the next occurrence
            result = result.plusDays(1);
        }
        return result.toInstant().toEpochMilli();
    }

    /**
     * Returns true if the input looks like a duration rather than a point in time.
     * Used to give a helpful error message when the user mixes the two up.
     */
    public static boolean looksLikeDuration(final String input) {
        try {
            parseDuration(input);
            return true;
        } catch (TimeParseException e) {
            return false;
        }
    }

    public static String formatDuration(final Duration duration) {
        if (duration.isNegative()) {
            return "-" + formatDuration(duration.negated());
        }
        final var parts = new ArrayList<String>();
        final var days = duration.toDays();
        final var hours = duration.toHoursPart();
        final var minutes = duration.toMinutesPart();
        final var seconds = duration.toSecondsPart();
        if (days > 0) {
            parts.add(days + "d");
        }
        if (hours > 0) {
            parts.add(hours + "h");
        }
        if (minutes > 0) {
            parts.add(minutes + "m");
        }
        if (seconds > 0 || parts.isEmpty()) {
            parts.add(seconds + "s");
        }
        return String.join(" ", parts);
    }

    public static String formatTimestamp(final long timestamp, final ZoneId zone) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(timestamp).atZone(zone));
    }

    private static LocalTime parseTimeOfDay(final String text, final String input) throws TimeParseException {
        switch (text) {
            case "noon", "midday" -> {
                return LocalTime.NOON;
            }
            case "midnight" -> {
                return LocalTime.MIDNIGHT;
            }
            default -> {
            }
        }
        final var matcher = TIME_OF_DAY.matcher(text);
        if (!matcher.matches()) {
            throw new TimeParseException("Invalid time: " + input);
        }
        var hour = Integer.parseInt(matcher.group(1));
        final var minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        final var second = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        final var meridiem = matcher.group(4);
        if (meridiem != null) {
            if (hour < 1 || hour > 12) {
                throw new TimeParseException("Invalid 12-hour time: " + input);
            }
            final var pm = meridiem.toLowerCase(Locale.ROOT).startsWith("p");
            if (hour == 12) {
                hour = pm ? 12 : 0;
            } else if (pm) {
                hour += 12;
            }
        } else if (matcher.group(2) == null) {
            // A bare number without minutes or am/pm is too ambiguous to be a time
            throw new TimeParseException("Invalid time: " + input);
        }
        try {
            return LocalTime.of(hour, minute, second);
        } catch (DateTimeException e) {
            throw new TimeParseException("Invalid time: " + input);
        }
    }

    private static String padHour(final String time) {
        return time.indexOf(':') == 1 ? "0" + time : time;
    }

    private static String normalize(final String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String stripPrefix(final String text, final String prefix) {
        return text.startsWith(prefix) ? text.substring(prefix.length()).trim() : text;
    }

    private static Duration checkPositive(final Duration duration) throws TimeParseException {
        if (duration.isZero() || duration.isNegative()) {
            throw new TimeParseException("Duration must be positive");
        }
        return duration;
    }

    public static final class TimeParseException extends Exception {

        public TimeParseException(final String message) {
            super(message);
        }
    }
}
