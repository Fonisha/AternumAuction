package dev.aternum.auction.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class LevelProvider implements EconomyProvider {
    @Override public boolean available() { return true; }
    @Override public boolean supportsOffline() { return false; }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return player instanceof Player && ((Player) player).getLevel() >= amount;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!(player instanceof Player)) return false;
        Player online = (Player) player;
        int levels = (int) Math.ceil(amount);
        if (online.getLevel() < levels) return false;
        online.setLevel(online.getLevel() - levels);
        return true;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        if (!(player instanceof Player)) return false;
        ((Player) player).setLevel(((Player) player).getLevel() + (int) Math.ceil(amount));
        return true;
    }

    @Override
    public String format(double amount) {
        return (int) amount + " lvl";
    }
}
