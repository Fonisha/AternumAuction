package dev.aternum.auction.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single GUI layout loaded from guis/*.yml.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class Layout {
    private final String id;
    private String parent;
    private String title = "&8Menu";
    private int rows = 6;
    private int pageSize = 45;
    private int contentStart = 0;
    private boolean centerContent = true;
    private int autoUpdateTicks = -1;
    private List<Integer> fillerSlots = new ArrayList<>();
    private String fillerMaterial = "GRAY_STAINED_GLASS_PANE";
    private String fillerName = " ";
    private final Map<String, LayoutItem> items = new LinkedHashMap<>();

    public Layout(String id) {
        this.id = id;
    }

    public static Layout load(String id, YamlConfiguration configuration) {
        Layout layout = new Layout(id);
        layout.apply(configuration);
        return layout;
    }

    public void apply(YamlConfiguration configuration) {
        if (configuration == null) return;
        parent = configuration.getString("inherit", null);
        title = configuration.getString("title", title);
        rows = Math.max(1, Math.min(6, configuration.getInt("rows", rows)));
        pageSize = Math.max(1, configuration.getInt("page-size", pageSize));
        contentStart = Math.max(0, configuration.getInt("content-start", contentStart));
        centerContent = configuration.getBoolean("center-content", centerContent);
        autoUpdateTicks = configuration.getInt("auto-update-ticks", autoUpdateTicks);

        ConfigurationSection filler = configuration.getConfigurationSection("filler");
        if (filler != null) {
            List<Integer> slots = Slots.parse(filler.get("slots"));
            if (slots.isEmpty()) slots = Slots.parse(filler.get("slot"));
            if (!slots.isEmpty()) fillerSlots = slots;
            fillerMaterial = filler.getString("material", fillerMaterial);
            fillerName = filler.getString("name", fillerName);
        }

        ConfigurationSection itemsSection = configuration.getConfigurationSection("items");
        if (itemsSection != null) for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(key);
            LayoutItem item = LayoutItem.parse(key, section);
            if (item != null) items.put(key, item);
        }
    }

    public boolean hasParent() {
        return parent != null && !parent.isEmpty();
    }

    public String parent() { return parent; }
    public String id() { return id; }
    public String title() { return title; }
    public int rows() { return rows; }
    public int pageSize() { return pageSize; }
    public int contentStart() { return contentStart; }
    public boolean centerContent() { return centerContent; }
    public int autoUpdateTicks() { return autoUpdateTicks; }
    public List<Integer> fillerSlots() { return fillerSlots; }
    public String fillerMaterial() { return fillerMaterial; }
    public String fillerName() { return fillerName; }
    public LayoutItem item(String key) { return items.get(key); }
    public Collection<LayoutItem> items() { return new ArrayList<>(items.values()); }
    public Map<String, LayoutItem> itemMap() { return items; }
}
