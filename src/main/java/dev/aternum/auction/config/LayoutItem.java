package dev.aternum.auction.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single configurable button inside a layout.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class LayoutItem {
    private final String key;
    private final int slot;
    private final String material;
    private final int amount;
    private final String name;
    private final List<String> lore;
    private final List<String> actions;
    private final List<String> conditions;
    private final boolean glow;
    private final String head;
    private final String texture;
    private final int customModelData;

    public LayoutItem(String key, int slot, String material, int amount, String name, List<String> lore,
                      List<String> actions, List<String> conditions, boolean glow, String head,
                      String texture, int customModelData) {
        this.key = key;
        this.slot = slot;
        this.material = material;
        this.amount = amount;
        this.name = name;
        this.lore = lore == null ? Collections.<String>emptyList() : lore;
        this.actions = actions == null ? Collections.<String>emptyList() : actions;
        this.conditions = conditions == null ? Collections.<String>emptyList() : conditions;
        this.glow = glow;
        this.head = head;
        this.texture = texture;
        this.customModelData = customModelData;
    }

    public static LayoutItem parse(String key, ConfigurationSection section) {
        if (section == null) return null;
        Object slotValue = section.get("slot");
        int slot = -1;
        if (slotValue instanceof Number) slot = ((Number) slotValue).intValue();
        else if (slotValue instanceof String) {
            List<Integer> parsed = Slots.parse(slotValue);
            if (!parsed.isEmpty()) slot = parsed.get(0);
        }
        List<String> actions = new ArrayList<>();
        for (Object action : section.getList("actions", Collections.emptyList())) actions.add(String.valueOf(action));
        return new LayoutItem(
                key,
                slot,
                section.getString("material", "STONE"),
                section.getInt("amount", 1),
                section.getString("name", ""),
                readLines(section.get("lore")),
                actions,
                section.getStringList("conditions"),
                section.getBoolean("glow", false),
                section.getString("head", ""),
                section.getString("texture", ""),
                section.getInt("custom-model-data", 0)
        );
    }

    /**
     * Accepts both a YAML list and a single string. A string may also point at
     * a language key with the "lang:" prefix, which is expanded when rendered.
     */
    private static List<String> readLines(Object value) {
        if (value instanceof List) {
            List<String> lines = new ArrayList<>();
            for (Object line : (List<?>) value) lines.add(String.valueOf(line));
            return lines;
        }
        if (value instanceof String) {
            List<String> lines = new ArrayList<>();
            lines.add((String) value);
            return lines;
        }
        return new ArrayList<>();
    }

    public LayoutItem withSlot(int newSlot) {
        return new LayoutItem(key, newSlot, material, amount, name, lore, actions, conditions, glow, head, texture, customModelData);
    }

    public String key() { return key; }
    public int slot() { return slot; }
    public String material() { return material; }
    public int amount() { return amount; }
    public String name() { return name; }
    public List<String> lore() { return lore; }
    public List<String> actions() { return actions; }
    public List<String> conditions() { return conditions; }
    public boolean glow() { return glow; }
    public String head() { return head; }
    public String texture() { return texture; }
    public int customModelData() { return customModelData; }
}
