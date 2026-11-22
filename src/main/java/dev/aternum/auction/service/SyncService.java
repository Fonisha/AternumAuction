package dev.aternum.auction.service;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.model.Payout;
import dev.aternum.auction.storage.StorageManager;
import dev.aternum.auction.util.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Pulls claims and payouts created by other servers in a shared database.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class SyncService implements Listener {
    private final AternumAuctionPlugin plugin;
    private final StorageManager storage;
    private final AuctionManager auctions;
    private Scheduler.Task task;

    public SyncService(AternumAuctionPlugin plugin, StorageManager storage, AuctionManager auctions) {
        this.plugin = plugin;
        this.storage = storage;
        this.auctions = auctions;
    }

    public void start() {
        if (!plugin.getConfigManager().multiServer()) return;
        int seconds = Math.max(5, plugin.getConfig().getInt("server.multi-server.sync-interval-seconds", 20));
        task = Scheduler.runTimer(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) pull(player);
            }
        }, 20L * seconds, 20L * seconds);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().multiServer()) return;
        pull(event.getPlayer());
    }

    private void pull(final Player player) {
        if (!auctions.claims(player.getUniqueId()).isEmpty() && !auctions.payouts(player.getUniqueId()).isEmpty()) return;
        Scheduler.runAsync(plugin, new Runnable() {
            @Override
            public void run() {
                final List<ItemStack> claims = auctions.claims(player.getUniqueId()).isEmpty()
                        ? storage.backend().loadClaims(player.getUniqueId()) : null;
                final List<Payout> payouts = auctions.payouts(player.getUniqueId()).isEmpty()
                        ? storage.backend().loadPayouts(player.getUniqueId()) : null;
                if ((claims == null || claims.isEmpty()) && (payouts == null || payouts.isEmpty())) return;
                Scheduler.run(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (claims != null && !claims.isEmpty()) auctions.setClaims(player.getUniqueId(), claims);
                        if (payouts != null && !payouts.isEmpty()) auctions.setPayouts(player.getUniqueId(), payouts);
                    }
                });
            }
        });
    }
}
