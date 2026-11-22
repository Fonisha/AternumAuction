package dev.aternum.auction.config;

import dev.aternum.auction.AternumAuctionPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads every gui layout from plugins/AternumAuction/guis.
 * Bundled layouts are copied on first start and can be edited freely afterwards.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class LayoutManager {
    /** Bundled layout revision. Older copies are backed up and replaced. */
    private static final int LAYOUT_VERSION = 17;
    private static final List<String> DEFAULTS = Arrays.asList(
            "main-gui.yml", "my-items-gui.yml", "history-gui.yml", "expired-gui.yml",
            "categories-gui.yml", "confirm-gui.yml", "currency-gui.yml", "shulker-gui.yml",
            "admin-gui.yml");

    private final AternumAuctionPlugin plugin;
    private final Map<String, Layout> layouts = new HashMap<>();

    public LayoutManager(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        layouts.clear();
        File folder = new File(plugin.getDataFolder(), "guis");
        if (!folder.exists() && !folder.mkdirs()) plugin.getLogger().warning("Could not create guis folder.");
        File obsolete = new File(folder, "layout-editor-gui.yml");
        if (obsolete.exists() && !obsolete.delete()) plugin.getLogger().warning("Could not remove layout-editor-gui.yml.");
        for (String name : DEFAULTS) {
            File file = new File(folder, name);
            if (!file.exists()) {
                saveBundled(name);
                continue;
            }
            int installed = YamlConfiguration.loadConfiguration(file).getInt("layout-version", 0);
            if (installed >= LAYOUT_VERSION) continue;
            File backup = new File(folder, name.substring(0, name.length() - 4) + ".old.yml");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not back up " + name + ": " + exception.getMessage());
            }
            if (!file.delete()) plugin.getLogger().warning("Could not replace old layout " + name + ".");
            saveBundled(name);
        }
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.getName().endsWith(".yml") || file.getName().endsWith(".old.yml")) continue;
            String id = file.getName().substring(0, file.getName().length() - 4);
            layouts.put(id, Layout.load(id, YamlConfiguration.loadConfiguration(file)));
        }
        for (Layout layout : new java.util.ArrayList<>(layouts.values())) {
            if (layout.hasParent()) {
                Layout parent = layouts.get(layout.parent());
                if (parent != null) {
                    Layout merged = new Layout(layout.id());
                    File parentFile = new File(folder, parent.id() + ".yml");
                    File ownFile = new File(folder, layout.id() + ".yml");
                    merged.apply(YamlConfiguration.loadConfiguration(parentFile));
                    merged.apply(YamlConfiguration.loadConfiguration(ownFile));
                    layouts.put(layout.id(), merged);
                }
            }
        }
    }

    private void saveBundled(String name) {
        try {
            plugin.saveResource("guis/" + name, false);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not write default layout " + name + ": " + throwable.getMessage());
        }
    }

    public Layout get(String id) {
        return layouts.get(id);
    }

    public int count() {
        return layouts.size();
    }
}
