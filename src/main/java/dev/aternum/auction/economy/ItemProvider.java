package dev.aternum.auction.economy;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class ItemProvider implements EconomyProvider {
    private final Material material;
    private final int amountPerUnit;

    public ItemProvider(Material material, int amountPerUnit) {
        this.material = material == null ? Material.DIAMOND : material;
        this.amountPerUnit = Math.max(1, amountPerUnit);
    }

    @Override public boolean available() { return material != null && material.isItem(); }
    @Override public boolean supportsOffline() { return false; }

    private int required(double amount) {
        return (int) Math.ceil(amount * amountPerUnit);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return player instanceof Player && count((Player) player) >= required(amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!(player instanceof Player)) return false;
        Player online = (Player) player;
        int left = required(amount);
        if (count(online) < left) return false;
        ItemStack[] contents = online.getInventory().getStorageContents();
        for (int index = 0; index < contents.length && left > 0; index++) {
            ItemStack content = contents[index];
            if (content == null || content.getType() != material) continue;
            int take = Math.min(left, content.getAmount());
            content.setAmount(content.getAmount() - take);
            if (content.getAmount() <= 0) online.getInventory().setItem(index, null);
            left -= take;
        }
        return left == 0;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        if (!(player instanceof Player)) return false;
        Player online = (Player) player;
        int count = required(amount);
        if (freeSpace(online) < count) return false;
        while (count > 0) {
            int stack = Math.min(64, count);
            online.getInventory().addItem(new ItemStack(material, stack));
            count -= stack;
        }
        return true;
    }

    private int count(Player player) {
        int total = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content != null && content.getType() == material) total += content.getAmount();
        }
        return total;
    }

    private int freeSpace(Player player) {
        int free = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null) free += 64;
            else if (content.getType() == material) free += Math.max(0, content.getMaxStackSize() - content.getAmount());
        }
        return free;
    }

    @Override
    public String format(double amount) {
        return required(amount) + " x " + material.name();
    }
}
