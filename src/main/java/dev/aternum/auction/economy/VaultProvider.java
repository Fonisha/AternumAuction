package dev.aternum.auction.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Vault bridge. Works with every Vault-compatible economy,
 * including RoyaleEconomy, UltraEconomy, KingdomsX banks and others.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class VaultProvider implements EconomyProvider {
    private Object provider;
    private Method has;
    private Method withdraw;
    private Method deposit;
    private Method format;

    public boolean hook() {
        try {
            Class<?> type = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(type);
            if (registration == null) return false;
            provider = registration.getProvider();
            has = type.getMethod("has", OfflinePlayer.class, double.class);
            withdraw = find(type, "withdrawPlayer", OfflinePlayer.class);
            deposit = find(type, "depositPlayer", OfflinePlayer.class);
            format = type.getMethod("format", double.class);
            return true;
        } catch (Throwable throwable) {
            provider = null;
            return false;
        }
    }

    private static Method find(Class<?> type, String name, Class<?> playerType) {
        try {
            return type.getMethod(name, playerType, double.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public boolean available() {
        return provider != null && has != null && withdraw != null && deposit != null;
    }

    @Override
    public boolean supportsOffline() {
        return true;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        try {
            return (Boolean) has.invoke(provider, player, amount);
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (withdraw == null) return false;
        try {
            return responseSuccess(withdraw.invoke(provider, player, amount));
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        if (deposit == null) return false;
        try {
            return responseSuccess(deposit.invoke(provider, player, amount));
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static boolean responseSuccess(Object response) {
        if (response == null) return true;
        if (response instanceof Boolean) return (Boolean) response;
        try {
            Method success = response.getClass().getMethod("transactionSuccess");
            Object value = success.invoke(response);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public String format(double amount) {
        try {
            return format == null ? defaultFormat(amount) : (String) format.invoke(provider, amount);
        } catch (Throwable throwable) {
            return defaultFormat(amount);
        }
    }

    static String defaultFormat(double amount) {
        return String.format("%,.2f", amount);
    }
}
