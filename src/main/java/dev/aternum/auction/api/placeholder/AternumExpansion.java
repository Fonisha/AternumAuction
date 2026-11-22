package dev.aternum.auction.api.placeholder;

import dev.aternum.auction.AternumAuctionPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI expansion.
 *
 * %aternumauction_sell_limit%      maximum lots for the player
 * %aternumauction_sell_count%      active lots of the player
 * %aternumauction_expired_count%   waiting claims and payouts
 * %aternumauction_active_count%    active lots on this server
 * %aternumauction_total_active%    active lots visible to the player
 * %aternumauction_history_count%   history entries
 * %aternumauction_currency%        selected currency
 * %aternumauction_server%          server id
 * %aternumauction_version%         plugin version
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class AternumExpansion extends PlaceholderExpansion {
    private final AternumAuctionPlugin plugin;

    public AternumExpansion(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "aternumauction";
    }

    @Override
    public String getAuthor() {
        return "Fonisha";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        String key = params.toLowerCase();
        if ("sell_limit".equals(key) || "limit".equals(key)) {
            if (player.getPlayer() == null) return String.valueOf(plugin.getConfigManager().integer("settings.limits.default", 10));
            int limit = plugin.getConfigManager().limit(player.getPlayer(), plugin.getStorageManager().limitBonus(player.getUniqueId()));
            return limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit);
        }
        if ("sell_count".equals(key) || "active_player".equals(key)) {
            return String.valueOf(plugin.getAuctionManager().countVisible(player.getUniqueId()));
        }
        if ("expired_count".equals(key) || "pending".equals(key)) {
            return String.valueOf(plugin.getAuctionManager().pendingCount(player.getUniqueId()));
        }
        if ("active_count".equals(key)) {
            return String.valueOf(plugin.getAuctionManager().activeCount());
        }
        if ("total_active".equals(key)) {
            return String.valueOf(plugin.getAuctionManager().active().size());
        }
        if ("history_count".equals(key)) {
            return String.valueOf(plugin.getAuctionManager().historyCount(player.getUniqueId()));
        }
        if ("currency".equals(key)) {
            return plugin.getCurrencyRegistry().displayName(
                    plugin.getPlayerSettings().currency(player.getUniqueId(), plugin.getCurrencyRegistry().defaultId()));
        }
        if ("server".equals(key)) {
            return plugin.getConfigManager().serverId();
        }
        if ("version".equals(key)) {
            return plugin.getDescription().getVersion();
        }
        return null;
    }
}
