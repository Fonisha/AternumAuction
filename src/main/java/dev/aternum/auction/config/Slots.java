package dev.aternum.auction.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Parses slot definitions such as 12, "0-8", "10,12,14" or a YAML list.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class Slots {
    private Slots() {
    }

    public static List<Integer> parse(Object value) {
        List<Integer> result = new ArrayList<>();
        if (value == null) return result;
        if (value instanceof Number) {
            result.add(((Number) value).intValue());
            return result;
        }
        if (value instanceof List) {
            for (Object element : (List<?>) value) result.addAll(parse(element));
            return result;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return result;
        for (String part : text.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            int dash = token.indexOf('-');
            if (dash > 0) {
                try {
                    int from = Integer.parseInt(token.substring(0, dash).trim());
                    int to = Integer.parseInt(token.substring(dash + 1).trim());
                    if (from > to) {
                        int swap = from;
                        from = to;
                        to = swap;
                    }
                    for (int index = from; index <= to; index++) result.add(index);
                } catch (NumberFormatException ignored) {
                }
            } else {
                try {
                    result.add(Integer.parseInt(token));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    public static List<String> ranges(Collection<Integer> slots) {
        return Collections.emptyList();
    }
}
