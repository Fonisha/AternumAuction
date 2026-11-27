package dev.aternum.auction.script;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Variables exposed to admin scripts.
 *
 * Every value can be used as %name% in expressions and in message/command text.
 * Unknown variables resolve to an empty string.
 *
 * @author Fonisha
 */
public final class ScriptContext {
    private final Map<String, Object> values = new HashMap<String, Object>();
    private final Map<String, String> strings = new HashMap<String, String>();
    private Player recipient;

    public ScriptContext put(String key, Object value) {
        String name = key.toLowerCase(Locale.ENGLISH);
        values.put(name, value);
        strings.put(name, printable(value));
        return this;
    }

    public ScriptContext recipient(Player player) {
        this.recipient = player;
        return this;
    }

    public Player recipient() {
        return recipient;
    }

    public Map<String, Object> variables() {
        return Collections.unmodifiableMap(values);
    }

    public Object get(String key) {
        return values.get(key.toLowerCase(Locale.ENGLISH));
    }

    public String string(String key) {
        String value = strings.get(key.toLowerCase(Locale.ENGLISH));
        return value == null ? "" : value;
    }

    /** Replaces every %variable% in the supplied text with its printable value. */
    public String resolve(String text) {
        if (text == null) return "";
        String result = text;
        for (Map.Entry<String, String> entry : strings.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    /** Adds date and time variables, calculated at the moment the event fires. */
    public ScriptContext time() {
        Calendar calendar = Calendar.getInstance();
        Date now = calendar.getTime();
        put("timestamp", calendar.getTimeInMillis());
        put("hour", calendar.get(Calendar.HOUR_OF_DAY));
        put("minute", calendar.get(Calendar.MINUTE));
        put("day", calendar.get(Calendar.DAY_OF_MONTH));
        put("month", calendar.get(Calendar.MONTH) + 1);
        put("year", calendar.get(Calendar.YEAR));
        int weekday = calendar.get(Calendar.DAY_OF_WEEK);
        put("weekday", weekday - 1);
        put("weekday_name", calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH));
        put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(now));
        put("time", new SimpleDateFormat("HH:mm", Locale.ENGLISH).format(now));
        return this;
    }

    /** Extracts material and custom model data from an item, if available. */
    public ScriptContext item(ItemStack item) {
        if (item == null) {
            put("item", "");
            put("material", "");
            put("model", -1);
            return this;
        }
        put("item", ChatColor.stripColor(displayName(item)));
        put("material", item.getType() == null ? "" : item.getType().name());
        put("amount", item.getAmount());
        int model = -1;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                java.lang.reflect.Method has = meta.getClass().getMethod("hasCustomModelData");
                if (Boolean.TRUE.equals(has.invoke(meta))) {
                    java.lang.reflect.Method get = meta.getClass().getMethod("getCustomModelData");
                    model = ((Number) get.invoke(meta)).intValue();
                }
            }
        } catch (Throwable ignored) {
            // Older server versions do not have custom model data.
        }
        put("model", model);
        return this;
    }

    private static String displayName(ItemStack item) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName() != null) return meta.getDisplayName();
        } catch (Throwable ignored) {
        }
        return item.getType() == null ? "" : item.getType().name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
    }

    private static String printable(Object value) {
        if (value == null) return "";
        if (value instanceof UUID) return value.toString();
        if (value instanceof Number) return Expression.text(value);
        if (value instanceof Boolean) return Expression.text(value);
        if (value instanceof ItemStack) return ChatColor.stripColor(displayName((ItemStack) value));
        if (value instanceof Player) {
            Player player = (Player) value;
            return player.getName() == null ? player.getUniqueId().toString() : player.getName();
        }
        if (value instanceof org.bukkit.OfflinePlayer) {
            org.bukkit.OfflinePlayer player = (org.bukkit.OfflinePlayer) value;
            String name = player.getName();
            return name == null ? player.getUniqueId().toString() : name;
        }
        return String.valueOf(value);
    }
}
