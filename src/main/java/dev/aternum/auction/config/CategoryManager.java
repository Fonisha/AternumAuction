package dev.aternum.auction.config;

import dev.aternum.auction.AternumAuctionPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Categories loaded from categories.yml.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class CategoryManager {
    private final AternumAuctionPlugin plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();

    public CategoryManager(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        categories.clear();
        File file = new File(plugin.getDataFolder(), "categories.yml");
        if (!file.exists()) plugin.saveResource("categories.yml", false);
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = configuration.getConfigurationSection("categories");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            Material icon = Material.matchMaterial(section.getString("icon", "CHEST"));
            if (icon == null) icon = Material.CHEST;
            Set<String> materials = new LinkedHashSet<>();
            for (String material : section.getStringList("materials")) {
                if (material != null) materials.add(material.trim().toUpperCase());
            }
            categories.put(key.toLowerCase(), new Category(key.toLowerCase(), section.getString("display-name", key), icon, materials));
        }
        if (!categories.containsKey("misc")) categories.put("misc", new Category("misc", "&7Разное", Material.CHEST, new LinkedHashSet<String>()));
    }

    public Collection<Category> all() {
        return new ArrayList<>(categories.values());
    }

    public Category get(String id) {
        return id == null ? null : categories.get(id.toLowerCase());
    }

    public String resolve(ItemStack item) {
        if (item == null) return "misc";
        String material = item.getType().name();
        for (Category category : categories.values()) {
            if (category.materials().contains(material)) return category.id();
        }
        return "misc";
    }

    public String displayName(String id) {
        Category category = get(id);
        if (category == null) return id;
        return category.displayName();
    }

    public static final class Category {
        private final String id;
        private final String displayName;
        private final Material icon;
        private final Set<String> materials;

        public Category(String id, String displayName, Material icon, Set<String> materials) {
            this.id = id;
            this.displayName = displayName;
            this.icon = icon;
            this.materials = materials;
        }

        public String id() { return id; }
        public String displayName() { return displayName; }
        public Material icon() { return icon; }
        public Set<String> materials() { return materials; }
    }
}
