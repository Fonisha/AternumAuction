package dev.aternum.auction.config;

import dev.aternum.auction.AternumAuctionPlugin;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Typed access to config.yml.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class ConfigManager {
    private final AternumAuctionPlugin plugin;

    public ConfigManager(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public String storageType() {
        return plugin.getConfig().getString("storage.type", "yaml").toLowerCase();
    }

    public String string(String path, String fallback) {
        return plugin.getConfig().getString(path, fallback);
    }

    public int integer(String path, int fallback) {
        return plugin.getConfig().getInt(path, fallback);
    }

    public double decimal(String path, double fallback) {
        return plugin.getConfig().getDouble(path, fallback);
    }

    public boolean bool(String path, boolean fallback) {
        return plugin.getConfig().getBoolean(path, fallback);
    }

    public List<String> strings(String path) {
        return plugin.getConfig().getStringList(path);
    }

    public String serverId() {
        String id = string("server.id", "");
        if (id == null || id.isEmpty()) id = "server-1";
        return id;
    }

    public boolean multiServer() {
        return bool("server.multi-server.enabled", false);
    }

    public boolean showAllServers() {
        return bool("server.multi-server.show-all-servers", false);
    }

    public int limit(Player player, int bonus) {
        if (player.hasPermission("aternumauction.bypass.limit")) return Integer.MAX_VALUE;
        int limit = integer("settings.limits.default", 10);
        for (int value : plugin.getConfig().getIntegerList("settings.limits.values")) {
            if (player.hasPermission("aternumauction.limit." + value) && value > limit) limit = value;
        }
        return limit + Math.max(0, bonus);
    }

    public long durationMillis(int hours) {
        int maximum = integer("settings.max-duration-hours", 168);
        int safeHours = Math.max(1, Math.min(maximum, hours));
        return safeHours * 3600000L;
    }

    public int defaultDurationHours() {
        return integer("settings.default-duration-hours", 48);
    }

    public String currencyId() {
        return string("currencies.default", "vault");
    }
}
