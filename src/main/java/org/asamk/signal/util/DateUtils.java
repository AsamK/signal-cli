package org.asamk.signal.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateUtils {

    private static final TimeZone tzUTC = TimeZone.getTimeZone("UTC");

    private DateUtils() {
    }

    public static String formatTimestamp(long timestamp) {
        var date = new Date(timestamp);
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tzUTC);
        return timestamp + " (" + df.format(date) + ")";
    }
}
