package dev.aternum.auction.script;

import dev.aternum.auction.AternumAuctionPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Loads scripts.yml and every *.yml file inside the scripts/ folder.
 *
 * scripts.yml owns the settings and hook bindings. Script definitions can be
 * written directly in scripts.yml or in any file in scripts/ (for example
 * scripts/examples.yml). This keeps script logic out of config.yml.
 *
 * @author Fonisha
 */
public final class ScriptConfig {
    private final AternumAuctionPlugin plugin;
    private boolean enabled;
    private boolean debug;
    private boolean allowCommands;
    private boolean loadFolder;
    private int maxSteps;
    private UUID treasury;
    private final Map<String, Map<String, Object>> scripts = new LinkedHashMap<String, Map<String, Object>>();
    private final Map<String, List<String>> hooks = new HashMap<String, List<String>>();

    public ScriptConfig(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        scripts.clear();
        hooks.clear();

        File file = new File(plugin.getDataFolder(), "scripts.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("scripts.yml", false);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Could not save scripts.yml: " + throwable.getMessage());
            }
        }
        YamlConfiguration root = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection settings = root.getConfigurationSection("settings");
        enabled = settings == null || settings.getBoolean("enabled", true);
        debug = settings != null && settings.getBoolean("debug", false);
        allowCommands = settings != null && settings.getBoolean("allow-commands", false);
        loadFolder = settings == null || settings.getBoolean("load-scripts-folder", true);
        maxSteps = settings == null ? 2000 : settings.getInt("max-steps", 2000);
        treasury = null;
        if (settings != null) {
            String id = settings.getString("treasury-uuid", "");
            if (id != null && !id.trim().isEmpty()) {
                try {
                    treasury = UUID.fromString(id.trim());
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("scripts.yml: invalid settings.treasury-uuid, commissions will be removed from circulation.");
                }
            }
        }

        readScripts(root);
        readHooks(root);

        List<String> imports = settings == null ? Collections.<String>emptyList()
                : settings.getStringList("imports");
        for (String name : imports) {
            loadFile(new File(plugin.getDataFolder(), "scripts" + File.separator + normalize(name)));
        }

        if (loadFolder) {
            File folder = new File(plugin.getDataFolder(), "scripts");
            File[] files = folder.listFiles();
            if (files != null) {
                Arrays.sort(files);
                for (File child : files) {
                    String name = child.getName();
                    if (!child.isFile() || !name.toLowerCase(Locale.ENGLISH).endsWith(".yml")) continue;
                    if (name.startsWith("disabled-") || name.startsWith("_")) continue;
                    loadFile(child);
                }
            }
        }
    }

    private static String normalize(String name) {
        String value = name == null ? "" : name.trim();
        return value.toLowerCase(Locale.ENGLISH).endsWith(".yml") ? value : value + ".yml";
    }

    private void loadFile(File file) {
        if (file == null || !file.exists()) return;
        try {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            readScripts(data);
            readHooks(data);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not read script file " + file.getName() + ": " + throwable.getMessage());
        }
    }

    private void readScripts(YamlConfiguration data) {
        ConfigurationSection section = data.getConfigurationSection("scripts");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection definition = section.getConfigurationSection(key);
            if (definition == null) continue;
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            for (String option : definition.getKeys(false)) {
                values.put(option.toLowerCase(Locale.ENGLISH), definition.get(option));
            }
            scripts.put(key, values);
        }
    }

    private void readHooks(YamlConfiguration data) {
        ConfigurationSection section = data.getConfigurationSection("hooks");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String hook = key.toLowerCase(Locale.ENGLISH).replace('-', '_');
            List<String> names = hooks.get(hook);
            if (names == null) {
                names = new ArrayList<String>();
                hooks.put(hook, names);
            }
            names.addAll(section.getStringList(key));
        }
    }

    public boolean enabled() { return enabled; }
    public boolean debug() { return debug; }
    public boolean allowCommands() { return allowCommands; }
    public int maxSteps() { return Math.max(64, maxSteps); }
    public UUID treasury() { return treasury; }

    public List<String> hook(String name) {
        List<String> result = hooks.get(name == null ? "" : name.toLowerCase(Locale.ENGLISH));
        return result == null ? Collections.<String>emptyList() : result;
    }

    public Map<String, Object> script(String name) {
        return scripts.get(name);
    }

    public int scriptCount() {
        return scripts.size();
    }
}
