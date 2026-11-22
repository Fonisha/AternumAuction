package dev.aternum.auction.config;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Language and message resolver.
 * Messages live in lang/ru.yml and lang/en.yml, everything else falls back to English.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class Messages {
    private final AternumAuctionPlugin plugin;
    private YamlConfiguration language;
    private YamlConfiguration fallback;
    private YamlConfiguration bundledLanguage;
    private YamlConfiguration bundledFallback;
    private String code = "ru";

    public Messages(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        code = plugin.getConfig().getString("settings.language", "ru").toLowerCase(Locale.ENGLISH);
        language = load(code);
        fallback = "en".equals(code) ? language : load("en");
        bundledLanguage = loadBundled(code);
        bundledFallback = "en".equals(code) ? bundledLanguage : loadBundled("en");
    }

    private YamlConfiguration load(String id) {
        File folder = new File(plugin.getDataFolder(), "lang");
        if (!folder.exists() && !folder.mkdirs()) plugin.getLogger().warning("Could not create lang folder.");
        File file = new File(folder, id + ".yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("lang/" + id + ".yml", false);
            } catch (Throwable ignored) {
            }
        }
        YamlConfiguration result = new YamlConfiguration();
        try {
            result.load(file);
            return result;
        } catch (Exception exception) {
            File backup = new File(folder, id + ".broken.yml");
            try {
                Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().warning("Invalid " + id + ".yml moved to " + backup.getName()
                        + "; restoring the bundled language file.");
                plugin.saveResource("lang/" + id + ".yml", false);
                result.load(new File(folder, id + ".yml"));
                return result;
            } catch (Exception recoveryException) {
                plugin.getLogger().warning("Could not recover language " + id + ".yml: "
                        + recoveryException.getMessage());
                return result;
            }
        }
    }

    private YamlConfiguration loadBundled(String id) {
        YamlConfiguration result = new YamlConfiguration();
        InputStream stream = plugin.getResource("lang/" + id + ".yml");
        if (stream == null) return result;
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            result.load(reader);
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load bundled language " + id + ": " + exception.getMessage());
        }
        return result;
    }

    public String languageCode() {
        return code;
    }

    public String raw(String path) {
        String value = language.getString(path);
        if (value == null && fallback != null) value = fallback.getString(path);
        if (value == null && bundledLanguage != null) value = bundledLanguage.getString(path);
        if (value == null && bundledFallback != null) value = bundledFallback.getString(path);
        return value == null ? path : value;
    }

    /** Resolves a value supplied by a layout, including language keys in old layouts. */
    public String resolveRaw(String value, Map<String, String> placeholders) {
        return resolve(value, placeholders);
    }

    public List<String> rawList(String path) {
        List<String> value = language.getStringList(path);
        if ((value == null || value.isEmpty()) && language.isString(path)) value = splitLines(language.getString(path));
        if ((value == null || value.isEmpty()) && fallback != null) value = fallback.getStringList(path);
        if ((value == null || value.isEmpty()) && fallback != null && fallback.isString(path)) value = splitLines(fallback.getString(path));
        if ((value == null || value.isEmpty()) && bundledLanguage != null) value = bundledLanguage.getStringList(path);
        if ((value == null || value.isEmpty()) && bundledLanguage != null && bundledLanguage.isString(path)) value = splitLines(bundledLanguage.getString(path));
        if ((value == null || value.isEmpty()) && bundledFallback != null) value = bundledFallback.getStringList(path);
        if ((value == null || value.isEmpty()) && bundledFallback != null && bundledFallback.isString(path)) value = splitLines(bundledFallback.getString(path));
        return value == null ? Collections.<String>emptyList() : value;
    }

    private List<String> splitLines(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        for (String line : value.split("\\|\\|", -1)) result.add(line);
        return result;
    }

    /** Resolves a layout value. Supports the "lang:" prefix. */
    public String resolve(String value, Map<String, String> placeholders) {
        if (value == null) return "";
        String result = value;
        if (result.startsWith("lang:")) result = raw(result.substring(5));
        return Text.replace(result, placeholders);
    }

    public List<String> resolveList(List<String> values, Map<String, String> placeholders) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (String value : values) {
            if (value == null) continue;
            if (value.trim().startsWith("lang:")) {
                List<String> expanded = rawList(value.trim().substring(5));
                if (!expanded.isEmpty()) {
                    for (String line : expanded) result.add(Text.replace(line, placeholders));
                    continue;
                }
            }
            result.add(resolve(value, placeholders));
        }
        return result;
    }

    /**
     * Resolves layout lore. A "lang:" entry that points at a list is expanded
     * into several lines, so a layout can reuse a whole localized block.
     */
    public List<String> resolveLines(List<String> values, Map<String, String> placeholders) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (String value : values) {
            if (value == null) continue;
            String trimmed = value.trim();
            if (trimmed.startsWith("lang:")) {
                List<String> expanded = rawList(trimmed.substring(5));
                if (!expanded.isEmpty()) {
                    for (String line : expanded) {
                        for (String part : line.split("\\|\\|", -1)) result.add(Text.replace(part, placeholders));
                    }
                    continue;
                }
            }
            String resolved = resolve(value, placeholders);
            for (String part : resolved.split("\\|\\|", -1)) result.add(part);
        }
        return result;
    }

    public String prefix() {
        return raw("prefix");
    }

    public String get(String key, Map<String, String> placeholders) {
        return Text.replace(raw("messages." + key), placeholders);
    }

    public String get(String key) {
        return get(key, Collections.<String, String>emptyMap());
    }

    public List<String> list(String key) {
        List<String> values = rawList("messages." + key);
        if (values.isEmpty()) values = rawList(key);
        return values;
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender == null || key == null) return;
        String message = get(key, placeholders);
        if (message == null || message.isEmpty()) return;
        String prefix = prefix();
        if (prefix.isEmpty()) sender.sendMessage(Text.color(message));
        else sender.sendMessage(Text.color(prefix + message));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.<String, String>emptyMap());
    }

    public void sendRaw(CommandSender sender, String message) {
        if (sender != null && message != null) sender.sendMessage(Text.color(message));
    }

    public void broadcast(String key, Map<String, String> placeholders) {
        String message = Text.color(prefix() + get(key, placeholders));
        plugin.getServer().broadcastMessage(message);
    }
}
