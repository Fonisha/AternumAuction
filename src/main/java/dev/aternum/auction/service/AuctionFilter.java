package dev.aternum.auction.service;

import dev.aternum.auction.config.ConfigManager;
import dev.aternum.auction.model.AuctionItem;
import dev.aternum.auction.model.AuctionType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Search / filter predicate used by every auction view.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class AuctionFilter {
    public enum Type {
        ALL, CATEGORY, SELLER, SEARCH, MINE, BIN, BID, BUY_REQUEST
    }

    private final Type type;
    private final String value;
    private final UUID player;
    private final ConfigManager config;
    private final AuctionType auctionType;
    private final String category;

    private AuctionFilter(Type type, String value, UUID player, ConfigManager config) {
        this(type, value, player, config, null, null);
    }

    private AuctionFilter(Type type, String value, UUID player, ConfigManager config,
                          AuctionType auctionType, String category) {
        this.type = type;
        this.value = value;
        this.player = player;
        this.config = config;
        this.auctionType = auctionType;
        this.category = category == null ? null : category.toLowerCase(Locale.ENGLISH);
    }

    public static AuctionFilter all() {
        return new AuctionFilter(Type.ALL, null, null, null);
    }

    public static AuctionFilter category(String id) {
        return new AuctionFilter(Type.CATEGORY, id, null, null);
    }

    public static AuctionFilter seller(UUID id) {
        return new AuctionFilter(Type.SELLER, null, id, null);
    }

    public static AuctionFilter mine(UUID id) {
        return new AuctionFilter(Type.MINE, null, id, null);
    }

    public static AuctionFilter search(String query, ConfigManager config) {
        return new AuctionFilter(Type.SEARCH, query == null ? "" : query.toLowerCase(Locale.ENGLISH), null, config);
    }

    public static AuctionFilter type(AuctionType type) {
        return new AuctionFilter(Type.ALL, null, null, null, type, null);
    }

    public static AuctionFilter mode(AuctionType type, String category) {
        return new AuctionFilter(Type.ALL, null, null, null, type, category);
    }

    public AuctionFilter withCategory(String value) {
        return new AuctionFilter(type, this.value, player, config, auctionType, value);
    }

    public AuctionFilter withType(AuctionType type) {
        return new AuctionFilter(this.type, value, player, config, type, category);
    }

    public boolean matches(AuctionItem auction) {
        if (auctionType != null && auction.type() != auctionType) return false;
        if (category != null && !category.equalsIgnoreCase(auction.category())) return false;
        switch (type) {
            case CATEGORY:
                return auction.category().equalsIgnoreCase(value);
            case SELLER:
            case MINE:
                return auction.seller().equals(player);
            case SEARCH:
                return search(auction);
            default:
                return true;
        }
    }

    private boolean search(AuctionItem auction) {
        if (value == null || value.isEmpty()) return true;
        if (config == null || config.bool("settings.search.by-seller", true)) {
            if (contains(auction.sellerName(), value)) return true;
        }
        if (contains(auction.currency(), value)) return true;
        if (contains(auction.category(), value)) return true;

        ItemStack item = auction.item();
        if (item == null) return false;
        String material = item.getType().name();
        if (config == null || config.bool("settings.search.by-material", true)) {
            if (contains(material, value) || contains(material.replace('_', ' '), value)) return true;
        }
        if (config == null || config.bool("settings.search.by-name", true)) {
            if (contains(auction.displayName(), value)) return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if ((config == null || config.bool("settings.search.by-name", true)) && meta.hasDisplayName()) {
            if (contains(org.bukkit.ChatColor.stripColor(meta.getDisplayName()), value)) return true;
        }
        if ((config == null || config.bool("settings.search.by-lore", true)) && meta.hasLore()) {
            for (String line : meta.getLore()) if (contains(org.bukkit.ChatColor.stripColor(line), value)) return true;
        }
        if (config == null || config.bool("settings.search.by-enchant", true)) {
            for (Map.Entry<Enchantment, Integer> entry : new ArrayList<>(meta.getEnchants().entrySet())) {
                String name = entry.getKey().getKey().getKey();
                if (contains(name, value) || contains(name.replace('_', ' '), value)) return true;
            }
        }
        return false;
    }

    private boolean contains(String source, String query) {
        if (source == null) return false;
        return source.toLowerCase(Locale.ENGLISH).contains(query);
    }

    public Type type() { return type; }
    public String value() { return value; }
    public UUID player() { return player; }
    public AuctionType auctionType() { return auctionType; }
    public String category() { return category; }

    public List<String> describe() {
        List<String> result = new ArrayList<>();
        result.add(type.name());
        if (value != null) result.add(value);
        return result;
    }
}
