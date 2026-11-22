package dev.aternum.auction.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Native CoinsEngine bridge. The internal currency id is configurable.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class CoinsEngineProvider implements EconomyProvider {
    private final String currencyId;
    private Object api;
    private Method getBalance;
    private Method addBalance;
    private Method removeBalance;
    private boolean playerType;

    public CoinsEngineProvider(String currencyId) {
        this.currencyId = currencyId == null || currencyId.isEmpty() ? "coins" : currencyId;
    }

    public boolean hook() {
        try {
            Class<?> type = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            api = type;
            for (Method method : type.getMethods()) {
                if (!method.getName().equals("getBalance") || method.getParameterCount() != 2) continue;
                Class<?> first = method.getParameterTypes()[0];
                if (first == Player.class || first == OfflinePlayer.class) {
                    getBalance = method;
                    playerType = first == Player.class;
                    break;
                }
            }
            addBalance = find(type, "addBalance");
            removeBalance = find(type, "removeBalance");
            return getBalance != null && addBalance != null && removeBalance != null;
        } catch (Throwable throwable) {
            api = null;
            return false;
        }
    }

    private static Method find(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 3) continue;
            Class<?> first = method.getParameterTypes()[0];
            if (first == Player.class || first == OfflinePlayer.class) return method;
        }
        return null;
    }

    @Override public boolean available() { return api != null && getBalance != null; }
    @Override public boolean supportsOffline() { return !playerType; }

    private Object subject(OfflinePlayer player) {
        return playerType ? player.getPlayer() : player;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        Object subject = subject(player);
        if (subject == null) return false;
        try {
            return ((Number) getBalance.invoke(api, subject, currencyId)).doubleValue() >= amount;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        Object subject = subject(player);
        if (subject == null) return false;
        try {
            removeBalance.invoke(api, subject, currencyId, amount);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        Object subject = subject(player);
        if (subject == null) return false;
        try {
            addBalance.invoke(api, subject, currencyId, amount);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Override
    public String format(double amount) {
        return String.format("%,.2f %s", amount, currencyId);
    }
}
