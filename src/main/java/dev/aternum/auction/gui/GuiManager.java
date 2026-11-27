package dev.aternum.auction.gui;

import dev.aternum.auction.AternumAuctionPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class GuiManager implements Listener {
    private final AternumAuctionPlugin plugin;
    private final Map<UUID, Gui> open = new ConcurrentHashMap<>();

    public GuiManager(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Gui gui) {
        open.put(player.getUniqueId(), gui);
        player.openInventory(gui.getInventory());
    }

    public Gui get(Player player) {
        return open.get(player.getUniqueId());
    }

    public void close(Player player) {
        open.remove(player.getUniqueId());
        player.closeInventory();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Gui)) return;
        event.setCancelled(true);
        Gui gui = (Gui) event.getView().getTopInventory().getHolder();
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        UUID playerId = event.getWhoClicked().getUniqueId();
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        // DOUBLE_CLICK and all inventory manipulation clicks are rejected above.
        // A normal click is dispatched exactly once without an artificial cooldown.
        gui.handle(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Gui) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        open.remove(playerId);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        Gui gui = open.get(player.getUniqueId());
        if (gui == null) return;
        if (event.getInventory().getHolder() != gui) return;
        open.remove(player.getUniqueId());
        gui.onClose(player);
    }
}
