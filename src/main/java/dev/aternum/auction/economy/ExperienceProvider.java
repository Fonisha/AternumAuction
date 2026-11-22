package dev.aternum.auction.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class ExperienceProvider implements EconomyProvider {
    @Override public boolean available() { return true; }
    @Override public boolean supportsOffline() { return false; }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return player instanceof Player && ((Player) player).getTotalExperience() >= amount;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!(player instanceof Player)) return false;
        Player online = (Player) player;
        int points = (int) Math.ceil(amount);
        if (online.getTotalExperience() < points) return false;
        online.giveExp(-points);
        return true;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        if (!(player instanceof Player)) return false;
        ((Player) player).giveExp((int) Math.ceil(amount));
        return true;
    }

    @Override
    public String format(double amount) {
        return (int) amount + " xp";
    }
}
