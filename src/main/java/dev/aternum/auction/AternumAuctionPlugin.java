package dev.aternum.auction;

import dev.aternum.auction.api.AuctionAPI;
import dev.aternum.auction.api.placeholder.AternumExpansion;
import dev.aternum.auction.command.AuctionAdminCommand;
import dev.aternum.auction.command.AuctionCommand;
import dev.aternum.auction.config.BlacklistManager;
import dev.aternum.auction.config.CategoryManager;
import dev.aternum.auction.config.ConfigManager;
import dev.aternum.auction.config.LayoutManager;
import dev.aternum.auction.config.Messages;
import dev.aternum.auction.config.PlayerSettings;
import dev.aternum.auction.economy.CurrencyRegistry;
import dev.aternum.auction.gui.GuiManager;
import dev.aternum.auction.gui.InputManager;
import dev.aternum.auction.gui.LayoutGui;
import dev.aternum.auction.gui.MenuContext;
import dev.aternum.auction.gui.MenuType;
import dev.aternum.auction.model.AuctionItem;
import dev.aternum.auction.model.Transaction;
import dev.aternum.auction.service.AuctionManager;
import dev.aternum.auction.service.ConverterService;
import dev.aternum.auction.script.ScriptManager;
import dev.aternum.auction.service.DiscordService;
import dev.aternum.auction.service.SyncService;
import dev.aternum.auction.storage.StorageManager;
import dev.aternum.auction.util.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bstats.bukkit.Metrics;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * AternumAuction entry point.
 *
 * Author: Fonisha — https://github.com/Fonisha
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class AternumAuctionPlugin extends JavaPlugin implements AuctionAPI, Listener {
    private ConfigManager configManager;
    private Messages messages;
    private CategoryManager categoryManager;
    private LayoutManager layoutManager;
    private PlayerSettings playerSettings;
    private BlacklistManager blacklistManager;
    private CurrencyRegistry currencyRegistry;
    private StorageManager storageManager;
    private AuctionManager auctionManager;
    private DiscordService discordService;
    private SyncService syncService;
    private ConverterService converterService;
    private ScriptManager scriptManager;
    private GuiManager guiManager;
    private InputManager inputManager;
    private AuctionAdminCommand adminCommand;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveIfMissing("categories.yml");
        saveIfMissing("lang/ru.yml");
        saveIfMissing("lang/en.yml");

        configManager = new ConfigManager(this);
        messages = new Messages(this);
        categoryManager = new CategoryManager(this);
        layoutManager = new LayoutManager(this);
        playerSettings = new PlayerSettings(this);
        blacklistManager = new BlacklistManager(this);
        currencyRegistry = new CurrencyRegistry(this);
        currencyRegistry.reload();
        discordService = new DiscordService(this);
        scriptManager = new ScriptManager(this, currencyRegistry, messages);
        storageManager = new StorageManager(this);
        storageManager.init();
        auctionManager = new AuctionManager(this, configManager, messages, categoryManager, blacklistManager,
                currencyRegistry, storageManager, discordService);
        auctionManager.load();
        printStartupBanner();
        syncService = new SyncService(this, storageManager, auctionManager);
        syncService.start();
        converterService = new ConverterService(this, auctionManager);

        guiManager = new GuiManager(this);
        inputManager = new InputManager(this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(inputManager, this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(auctionManager, this);
        getServer().getPluginManager().registerEvents(syncService, this);

        AuctionCommand command = new AuctionCommand(this);
        adminCommand = new AuctionAdminCommand(this);
        if (getCommand("auction") != null) {
            getCommand("auction").setExecutor(command);
            getCommand("auction").setTabCompleter(command);
        }
        if (getCommand("auctionadmin") != null) {
            getCommand("auctionadmin").setExecutor(adminCommand);
            getCommand("auctionadmin").setTabCompleter(adminCommand);
        }

        registerPlaceholders();
        registerMetrics();
        getLogger().info("AternumAuction " + getDescription().getVersion() + " enabled: "
                + auctionManager.activeCount() + " lots, " + currencyRegistry.all().size() + " currencies, "
                + (Scheduler.isFolia() ? "Folia" : "Bukkit") + " scheduler, "
                + scriptManager.debugInfo() + ".");
    }

    private void printStartupBanner() {
        String line = "===================================================";
        getLogger().info("");
        getLogger().info(line);
        getLogger().info("                    AternumAuction");
        getLogger().info(line);
        getLogger().info("              Version: " + getDescription().getVersion());
        getLogger().info("              Active lots: " + auctionManager.activeCount());
        getLogger().info("              You are running the latest version!");
        getLogger().info("              Developed by Fonisha.");
        getLogger().info(line);
        getLogger().info("");
    }

    private void registerMetrics() {
        int pluginId = getConfig().getInt("bstats.plugin-id", 0);
        if (pluginId <= 0) {
            // Existing installations keep their old config when saveDefaultConfig
            // runs. Migrate the registered project id instead of silently disabling metrics.
            pluginId = 35506;
            getConfig().set("bstats.plugin-id", pluginId);
            saveConfig();
        }
        if (pluginId <= 0) {
            getLogger().warning("bStats is not configured yet. Set bstats.plugin-id after registering AternumAuction on bStats.org.");
            return;
        }
        try {
            new Metrics(this, pluginId);
            getLogger().info("bStats metrics enabled.");
        } catch (Throwable throwable) {
            getLogger().warning("Could not enable bStats: " + throwable.getMessage());
        }
    }

    private void saveIfMissing(String resource) {
        java.io.File file = new java.io.File(getDataFolder(), resource);
        if (file.exists()) return;
        try {
            saveResource(resource, false);
        } catch (Throwable throwable) {
            getLogger().warning("Could not save " + resource + ": " + throwable.getMessage());
        }
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            new AternumExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        } catch (Throwable throwable) {
            getLogger().warning("PlaceholderAPI expansion failed: " + throwable.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (syncService != null) syncService.stop();
        if (auctionManager != null) auctionManager.shutdown();
        if (storageManager != null) storageManager.shutdown();
    }

    public void reloadPlugin() {
        configManager.reload();
        messages.reload();
        categoryManager.load();
        layoutManager.reload();
        currencyRegistry.reload();
        scriptManager.reload();
        auctionManager.reload();
    }

    public void openMenu(Player player, MenuType type, MenuContext context) {
        context.type = type;
        new LayoutGui(this, context).open(player);
    }

    public ConfigManager getConfigManager() { return configManager; }
    public Messages getMessages() { return messages; }
    public CategoryManager getCategoryManager() { return categoryManager; }
    public LayoutManager getLayoutManager() { return layoutManager; }
    public PlayerSettings getPlayerSettings() { return playerSettings; }
    public BlacklistManager getBlacklistManager() { return blacklistManager; }
    public CurrencyRegistry getCurrencyRegistry() { return currencyRegistry; }
    public StorageManager getStorageManager() { return storageManager; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public DiscordService getDiscordService() { return discordService; }
    public SyncService getSyncService() { return syncService; }
    public ConverterService getConverterService() { return converterService; }
    public ScriptManager getScriptManager() { return scriptManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public InputManager getInputManager() { return inputManager; }
    public AuctionAdminCommand getAdminCommand() { return adminCommand; }
    public AuctionAPI getAPI() { return this; }

    @Override
    public Collection<AuctionItem> getActiveAuctions() {
        return auctionManager.active();
    }

    @Override
    public AuctionItem getAuction(UUID id) {
        return auctionManager.get(id);
    }

    @Override
    public List<Transaction> getHistory(UUID player, int limit) {
        return auctionManager.history(player, limit);
    }

    @Override
    public boolean removeAuction(Player actor, UUID id, boolean admin) {
        return auctionManager.remove(actor, id, admin).success();
    }

    @Override
    public void reload() {
        reloadPlugin();
    }
}
