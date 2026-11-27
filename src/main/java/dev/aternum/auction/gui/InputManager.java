package dev.aternum.auction.gui;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.util.Scheduler;
import dev.aternum.auction.util.SoundUtil;
import dev.aternum.auction.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Chat input helper used by search and price prompts.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class InputManager implements Listener {
    private final AternumAuctionPlugin plugin;
    private final Map<UUID, Consumer<String>> requests = new ConcurrentHashMap<>();

    public InputManager(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, String prompt, Consumer<String> consumer) {
        requests.put(player.getUniqueId(), consumer);
        player.closeInventory();
        player.sendMessage(Text.color(prompt));
        SoundUtil.play(plugin, player, "click");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        final Consumer<String> consumer = requests.remove(event.getPlayer().getUniqueId());
        if (consumer == null) return;
        event.setCancelled(true);
        final String message = event.getMessage();
        final Player player = event.getPlayer();
        Scheduler.run(plugin, new Runnable() {
            @Override
            public void run() {
                consumer.accept("cancel".equalsIgnoreCase(message) ? null : message);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        requests.remove(event.getPlayer().getUniqueId());
    }
}
