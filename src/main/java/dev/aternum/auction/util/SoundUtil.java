package dev.aternum.auction.util;

import dev.aternum.auction.AternumAuctionPlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Sound helper with a fallback chain so 1.8-1.21 configs keep working.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class SoundUtil {
    private SoundUtil() {
    }

    public static void play(AternumAuctionPlugin plugin, Player player, String key) {
        String name = plugin.getConfig().getString("sounds." + key);
        if (name == null || name.isEmpty()) return;
        Sound sound = find(name);
        if (sound == null) return;
        try {
            player.playSound(player.getLocation(), sound, 0.7f, 1.0f);
        } catch (Throwable ignored) {
        }
    }

    private static Sound find(String name) {
        Sound sound = resolve(name);
        if (sound != null) return sound;
        String upper = name.toUpperCase();
        if (upper.startsWith("UI_")) return resolve("CLICK");
        if ("BLOCK_ENDER_CHEST_OPEN".equals(upper)) return resolve("CHEST_OPEN");
        if ("ENTITY_PLAYER_LEVELUP".equals(upper)) return resolve("LEVEL_UP");
        if ("ENTITY_VILLAGER_NO".equals(upper)) return resolve("VILLAGER_NO");
        if ("ENTITY_VILLAGER_TRADE".equals(upper)) return resolve("VILLAGER_YES");
        if ("ITEM_BOOK_PAGE_TURN".equals(upper)) return resolve("BOOK_PAGE_TURN");
        if ("BLOCK_CHEST_OPEN".equals(upper)) return resolve("CHEST_OPEN");
        return resolve("ORB_PICKUP");
    }

    private static Sound resolve(String name) {
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
