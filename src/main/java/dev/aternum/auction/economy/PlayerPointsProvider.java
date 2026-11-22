package dev.aternum.auction.economy;

import org.bukkit.OfflinePlayer;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Native PlayerPoints bridge (no compile-time dependency).
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class PlayerPointsProvider implements EconomyProvider {
    private Object api;
    private Method getPoints;
    private Method take;
    private Method give;

    public boolean hook() {
        try {
            Class<?> pluginClass = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
            Object plugin = pluginClass.getMethod("getInstance").invoke(null);
            api = pluginClass.getMethod("getAPI").invoke(plugin);
            Class<?> apiClass = api.getClass();
            getPoints = apiClass.getMethod("getPoints", UUID.class);
            take = apiClass.getMethod("take", UUID.class, int.class);
            give = apiClass.getMethod("give", UUID.class, int.class);
            return true;
        } catch (Throwable throwable) {
            api = null;
            return false;
        }
    }

    @Override public boolean available() { return api != null; }
    @Override public boolean supportsOffline() { return true; }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        try {
            return ((Number) getPoints.invoke(api, player.getUniqueId())).doubleValue() >= amount;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        try {
            Object result = take.invoke(api, player.getUniqueId(), (int) Math.ceil(amount));
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        try {
            Object result = give.invoke(api, player.getUniqueId(), (int) Math.ceil(amount));
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Override
    public String format(double amount) {
        return (int) amount + " points";
    }
}
