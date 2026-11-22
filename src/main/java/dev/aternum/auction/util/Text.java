package dev.aternum.auction.util;

import org.bukkit.ChatColor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colour and placeholder helpers, including #RRGGBB hex support.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class Text {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Text() {
    }

    public static String color(String value) {
        if (value == null) return "";
        Matcher matcher = HEX.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("\u00a7x");
            for (char character : hex.toCharArray()) replacement.append('\u00a7').append(character);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static String strip(String value) {
        return ChatColor.stripColor(color(value));
    }

    public static String replace(String value, Map<String, String> placeholders) {
        if (value == null) return "";
        String result = value;
        if (placeholders != null) for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
