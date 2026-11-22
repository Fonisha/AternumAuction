package dev.aternum.auction.economy;

import dev.aternum.auction.AternumAuctionPlugin;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads currencies from config.yml and exposes them by id.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class CurrencyRegistry {
    private final AternumAuctionPlugin plugin;
    private final Map<String, Currency> currencies = new LinkedHashMap<>();
    private String defaultId = "vault";

    public CurrencyRegistry(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        currencies.clear();
        defaultId = plugin.getConfig().getString("currencies.default", "vault").toLowerCase(Locale.ENGLISH);
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("currencies");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null || "default".equalsIgnoreCase(key)) continue;
            if (!section.getBoolean("enabled", true)) continue;
            register(key.toLowerCase(Locale.ENGLISH), section);
        }
        if (currencies.isEmpty()) {
            VaultProvider vault = new VaultProvider();
            vault.hook();
            currencies.put("vault", new Currency("vault", "&aМонеты", "$", Material.GOLD_INGOT, "", true, vault));
        }
        if (!currencies.containsKey(defaultId)) defaultId = currencies.keySet().iterator().next();
    }

    private void register(String id, ConfigurationSection section) {
        EconomyProvider provider;
        if ("vault".equals(id)) {
            VaultProvider vault = new VaultProvider();
            vault.hook();
            provider = vault;
        } else if ("experience".equals(id)) {
            provider = new ExperienceProvider();
        } else if ("levels".equals(id)) {
            provider = new LevelProvider();
        } else if ("item".equals(id)) {
            Material material = Material.matchMaterial(section.getString("material", "DIAMOND"));
            provider = new ItemProvider(material, section.getInt("amount-per-unit", 1));
        } else if ("playerpoints".equals(id)) {
            PlayerPointsProvider points = new PlayerPointsProvider();
            points.hook();
            provider = points;
        } else if ("coinsengine".equals(id)) {
            CoinsEngineProvider coins = new CoinsEngineProvider(section.getString("currency-id", "coins"));
            coins.hook();
            provider = coins;
        } else {
            return;
        }
        Material icon = Material.matchMaterial(section.getString("icon", "GOLD_INGOT"));
        if (icon == null) icon = Material.GOLD_INGOT;
        currencies.put(id, new Currency(
                id,
                section.getString("display-name", id),
                section.getString("symbol", id),
                icon,
                section.getString("permission", ""),
                section.getBoolean("allow-offline", true),
                provider
        ));
    }

    public Currency get(String id) {
        if (id == null) return null;
        return currencies.get(id.toLowerCase(Locale.ENGLISH));
    }

    public Collection<Currency> all() {
        return new ArrayList<>(currencies.values());
    }

    public List<Currency> enabledFor(org.bukkit.entity.Player player) {
        List<Currency> result = new ArrayList<>();
        for (Currency currency : currencies.values()) {
            if (currency.permission() != null && !currency.permission().isEmpty()
                    && !player.hasPermission(currency.permission())) continue;
            result.add(currency);
        }
        return result;
    }

    public String defaultId() {
        return defaultId;
    }

    public boolean available(String id) {
        Currency currency = get(id);
        return currency != null && currency.available();
    }

    public String displayName(String id) {
        Currency currency = get(id);
        return currency == null ? id : currency.displayName();
    }

    public String format(String id, double amount) {
        Currency currency = get(id);
        return currency == null ? String.format("%,.2f", amount) : currency.provider().format(amount);
    }

    public boolean has(String id, OfflinePlayer player, double amount) {
        Currency currency = get(id);
        return currency != null && currency.available() && currency.provider().has(player, amount);
    }

    public boolean withdraw(String id, OfflinePlayer player, double amount) {
        Currency currency = get(id);
        return currency != null && currency.available() && currency.provider().withdraw(player, amount);
    }

    public boolean deposit(String id, OfflinePlayer player, double amount) {
        Currency currency = get(id);
        return currency != null && currency.available() && currency.provider().deposit(player, amount);
    }

    public boolean supportsOffline(String id) {
        Currency currency = get(id);
        return currency != null && currency.provider() != null && currency.provider().supportsOffline();
    }
}
