package dev.aternum.auction.util;

import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads the contents of shulker boxes without placing them.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class ContainerUtil {
    private ContainerUtil() {
    }

    public static boolean isContainer(ItemStack item) {
        return isShulker(item) && (!contents(item).isEmpty() || hasContainerMeta(item));
    }

    public static boolean hasContainerMeta(ItemStack item) {
        if (!isShulker(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) return false;
        try {
            BlockState state = ((BlockStateMeta) meta).getBlockState();
            return state instanceof Container;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static List<ItemStack> contents(ItemStack item) {
        if (!isShulker(item)) return Collections.emptyList();
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) return Collections.emptyList();
        try {
            BlockState state = ((BlockStateMeta) meta).getBlockState();
            if (!(state instanceof Container)) return Collections.emptyList();
            Inventory inventory = ((Container) state).getInventory();
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack content : inventory.getStorageContents()) {
                if (content != null && content.getType() != org.bukkit.Material.AIR) result.add(content.clone());
            }
            return result;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static boolean isShulker(ItemStack item) {
        return item != null && item.getType().name().endsWith("SHULKER_BOX");
    }
}
