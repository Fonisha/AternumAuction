package dev.aternum.auction.storage;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.model.AuctionItem;
import dev.aternum.auction.model.Payout;
import dev.aternum.auction.model.Transaction;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Single-threaded persistence queue. Reads happen on load / sync, writes are queued.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class StorageManager {
    private final AternumAuctionPlugin plugin;
    private final Map<UUID, Integer> limitCache = new ConcurrentHashMap<>();
    private Storage storage;
    private ExecutorService writer;

    public StorageManager(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        storage = create();
        try {
            storage.init();
        } catch (Exception exception) {
            plugin.getLogger().warning("Storage initialization failed: " + exception.getMessage());
            plugin.getLogger().warning("Falling back to YAML storage.");
            storage = new YamlStorage(plugin, new File(plugin.getDataFolder(), "data.yml"));
            try {
                storage.init();
            } catch (Exception ignored) {
            }
        }
        try {
            limitCache.putAll(storage.loadLimits());
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load limit bonuses: " + exception.getMessage());
        }
        writer = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "AternumAuction-Storage");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private Storage create() {
        String type = plugin.getConfig().getString("storage.type", "yaml").toLowerCase();
        if ("sqlite".equals(type)) return new SqlStorage(plugin, false);
        if ("mysql".equals(type)) return new SqlStorage(plugin, true);
        return new YamlStorage(plugin, new File(plugin.getDataFolder(), "data.yml"));
    }

    public Storage backend() {
        return storage;
    }

    public List<AuctionItem> loadAuctions() {
        return storage.loadAuctions();
    }

    public Map<UUID, List<ItemStack>> loadClaims() {
        return storage.loadClaims();
    }

    public List<Payout> loadPayouts() {
        return storage.loadPayouts();
    }

    public List<Transaction> loadTransactions() {
        return storage.loadTransactions();
    }

    public int limitBonus(UUID player) {
        return limitCache.getOrDefault(player, 0);
    }

    public void loadLimit(UUID player) {
        if (!limitCache.containsKey(player)) {
            int bonus = storage.limitBonus(player);
            if (bonus > 0) limitCache.put(player, bonus);
        }
    }

    public void setLimitBonus(UUID player, int bonus) {
        int safe = Math.max(0, bonus);
        if (safe == 0) limitCache.remove(player);
        else limitCache.put(player, safe);
        final UUID target = player;
        final int value = safe;
        submit(new Runnable() {
            @Override public void run() { storage.setLimitBonus(target, value); }
        });
    }

    public void saveAuction(AuctionItem auction) {
        final AuctionItem copy = auction.copy();
        submit(new Runnable() {
            @Override public void run() { storage.saveAuction(copy); }
        });
    }

    public void deleteAuction(UUID id) {
        final UUID target = id;
        submit(new Runnable() {
            @Override public void run() { storage.deleteAuction(target); }
        });
    }

    /**
     * Removes the row synchronously so two servers can never sell the same lot.
     */
    public boolean reserveAuction(UUID id) {
        if (storage == null) return false;
        if (writer == null || writer.isShutdown()) return storage.reserveAuction(id);
        try {
            final UUID target = id;
            return writer.submit(new java.util.concurrent.Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return storage.reserveAuction(target);
                }
            }).get(8, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return false;
        }
    }

    public void saveClaims(UUID owner, List<ItemStack> items) {
        final UUID target = owner;
        final List<ItemStack> copy = items == null ? null : new ArrayList<>(items);
        submit(new Runnable() {
            @Override public void run() { storage.saveClaims(target, copy); }
        });
    }

    public void savePayout(Payout payout) {
        final Payout copy = payout;
        submit(new Runnable() {
            @Override public void run() { storage.savePayout(copy); }
        });
    }

    public void deletePayout(Payout payout) {
        final Payout copy = payout;
        submit(new Runnable() {
            @Override public void run() { storage.deletePayout(copy); }
        });
    }

    public void addTransaction(Transaction transaction) {
        final Transaction copy = transaction;
        submit(new Runnable() {
            @Override public void run() { storage.addTransaction(copy); }
        });
    }

    private void submit(Runnable task) {
        if (writer == null || writer.isShutdown()) return;
        writer.submit(task);
    }

    public void flush() {
        if (writer == null) return;
        try {
            writer.submit(new Runnable() {
                @Override public void run() { }
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    public void shutdown() {
        if (writer != null) {
            writer.shutdown();
            try {
                writer.awaitTermination(8, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        if (storage != null) storage.close();
    }
}
