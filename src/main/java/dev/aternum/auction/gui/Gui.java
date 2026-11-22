package dev.aternum.auction.gui;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Base inventory holder. Using InventoryHolder instead of title matching
 * prevents clicking through spoofed menus.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public abstract class Gui implements InventoryHolder {
    protected final AternumAuctionPlugin plugin;
    private final Inventory inventory;
    private final Map<Integer, ClickHandler> actions = new HashMap<>();

    protected Gui(AternumAuctionPlugin plugin, int size, String title) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, size, Text.color(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void set(int slot, ItemStack item, ClickHandler action) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (action == null) actions.remove(slot);
        else actions.put(slot, action);
    }

    public void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    public void clear(int slot) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, null);
        actions.remove(slot);
    }

    public void fill(Material material) {
        if (material == null) return;
        ItemStack filler = new ItemBuilder(material).name(" ").build();
        for (int slot = 0; slot < inventory.getSize(); slot++) if (inventory.getItem(slot) == null) inventory.setItem(slot, filler);
    }

    public void open(Player player) {
        plugin.getGuiManager().open(player, this);
    }

    public void handle(InventoryClickEvent event) {
        ClickHandler handler = actions.get(event.getRawSlot());
        if (handler != null) handler.click(event);
    }

    public void onClose(Player player) {
    }

    public interface ClickHandler {
        void click(InventoryClickEvent event);
    }
}
