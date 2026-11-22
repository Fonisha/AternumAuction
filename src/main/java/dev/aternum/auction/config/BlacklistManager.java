package dev.aternum.auction.config;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.util.ContainerUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Blacklist for materials, names, lore, worlds and container contents.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class BlacklistManager {
    private final AternumAuctionPlugin plugin;

    public BlacklistManager(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public String check(Player player, ItemStack item) {
        if (item == null || player == null) return "no-item";
        if (player.hasPermission("aternumauction.bypass.blacklist")) return null;
        if (plugin.getConfig().getStringList("settings.blacklist.worlds").contains(player.getWorld().getName())) {
            return "blacklisted";
        }
        if (isBlocked(item)) return "blacklisted";

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                String name = meta.getDisplayName().toLowerCase();
                for (String blocked : plugin.getConfig().getStringList("settings.blacklist.names")) {
                    if (!blocked.isEmpty() && name.contains(blocked.toLowerCase())) return "blacklisted";
                }
            }
            if (meta.hasLore()) {
                List<String> lore = new ArrayList<>(meta.getLore());
                if (lore.size() > plugin.getConfig().getInt("settings.item.max-lore-lines", 40)) return "blacklisted";
                for (String line : lore) {
                    String lower = line == null ? "" : line.toLowerCase();
                    for (String blocked : plugin.getConfig().getStringList("settings.blacklist.lore")) {
                        if (!blocked.isEmpty() && lower.contains(blocked.toLowerCase())) return "blacklisted";
                    }
                }
            }
        }

        if (!plugin.getConfig().getBoolean("settings.item.allow-shulkers", true)
                && item.getType().name().contains("SHULKER_BOX")) return "blacklisted";

        for (ItemStack content : ContainerUtil.contents(item)) {
            if (isBlocked(content)) return "blacklisted";
        }
        return null;
    }

    private boolean isBlocked(ItemStack item) {
        if (item == null) return false;
        return plugin.getConfig().getStringList("settings.blacklist.materials").contains(item.getType().name());
    }
}
