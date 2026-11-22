package dev.aternum.auction.gui;

import dev.aternum.auction.util.Heads;
import dev.aternum.auction.util.Text;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Small item builder used by every menu.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;
    private boolean glow;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material == null ? Material.STONE : material);
        this.meta = item.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public ItemBuilder name(String name) {
        if (meta != null) meta.setDisplayName(Text.color(name));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            for (String line : lines) lore.add(Text.color(line == null ? "" : line));
            meta.setLore(lore);
        }
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        this.glow = glow;
        return this;
    }

    public ItemBuilder modelData(int data) {
        if (meta == null || data == 0) return this;
        try {
            Method method = meta.getClass().getMethod("setCustomModelData", Integer.class);
            method.invoke(meta, data);
        } catch (Throwable ignored) {
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            if (glow) addGlow(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack head(String texture, String owner, String name, List<String> lore) {
        return head(texture, owner, name, lore, false);
    }

    public static ItemStack head(String texture, String owner, String name, List<String> lore, boolean glow) {
        ItemStack item = Heads.create(texture, owner);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) colored.add(Text.color(line == null ? "" : line));
                meta.setLore(colored);
            }
            if (glow) addGlow(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void addGlow(ItemMeta meta) {
        try {
            Enchantment enchantment;
            try {
                enchantment = (Enchantment) Enchantment.class.getField("UNBREAKING").get(null);
            } catch (Throwable ignored) {
                enchantment = (Enchantment) Enchantment.class.getField("DURABILITY").get(null);
            }
            meta.addEnchant(enchantment, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } catch (Throwable ignored) {
        }
    }

    public static List<String> lines(String... values) {
        return Arrays.asList(values);
    }
}
