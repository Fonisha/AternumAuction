package dev.aternum.auction.config;

import dev.aternum.auction.AternumAuctionPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player preferences: last used currency and sorting mode.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class PlayerSettings {
    private final AternumAuctionPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, String> currencies = new HashMap<>();
    private final Map<UUID, String> sorts = new HashMap<>();

    public PlayerSettings(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-settings.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        if (data.isConfigurationSection("players")) {
            for (String key : data.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String currency = data.getString("players." + key + ".currency");
                    String sort = data.getString("players." + key + ".sort");
                    if (currency != null && !currency.isEmpty()) currencies.put(uuid, currency);
                    if (sort != null && !sort.isEmpty()) sorts.put(uuid, sort);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public String currency(UUID player, String fallback) {
        if (!plugin.getConfig().getBoolean("currencies.remember-selection", true)) return fallback;
        return currencies.getOrDefault(player, fallback);
    }

    public void setCurrency(UUID player, String currency) {
        if (currency == null || currency.isEmpty()) return;
        currencies.put(player, currency);
        save();
    }

    public String sort(UUID player, String fallback) {
        return sorts.getOrDefault(player, fallback);
    }

    public void setSort(UUID player, String sort) {
        if (sort == null || sort.isEmpty()) return;
        sorts.put(player, sort);
        save();
    }

    private void save() {
        for (Map.Entry<UUID, String> entry : currencies.entrySet()) {
            data.set("players." + entry.getKey() + ".currency", entry.getValue());
        }
        for (Map.Entry<UUID, String> entry : sorts.entrySet()) {
            data.set("players." + entry.getKey() + ".sort", entry.getValue());
        }
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save player-settings.yml: " + exception.getMessage());
        }
    }
}
