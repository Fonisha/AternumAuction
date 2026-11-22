package dev.aternum.auction.gui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class MenuUtil {
    private MenuUtil() {
    }

    public static String time(long millis) {
        if (millis <= 0) return "0m";
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1, minutes) + "m";
    }

    public static String date(long millis) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(millis));
    }

    public static String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
    }
}
