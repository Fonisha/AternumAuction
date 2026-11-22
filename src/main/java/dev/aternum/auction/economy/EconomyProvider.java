package dev.aternum.auction.economy;

import org.bukkit.OfflinePlayer;

/**
 * Bridge between the auction and a concrete economy implementation.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public interface EconomyProvider {
    boolean available();
    boolean supportsOffline();
    boolean has(OfflinePlayer player, double amount);
    boolean withdraw(OfflinePlayer player, double amount);
    boolean deposit(OfflinePlayer player, double amount);
    String format(double amount);
}
