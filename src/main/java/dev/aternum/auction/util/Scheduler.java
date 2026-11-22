package dev.aternum.auction.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Scheduler facade that works on Bukkit/Spigot/Paper and on Folia.
 * Folia is detected at runtime, no hard dependency is required.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class Scheduler {
    private static final boolean FOLIA = detect();

    private Scheduler() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean detect() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void run(Plugin plugin, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Method method = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
            method.invoke(scheduler, plugin, consumer(task));
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not schedule task on Folia: " + throwable.getMessage());
        }
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return;
        }
        try {
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Method method = scheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);
            method.invoke(scheduler, plugin, consumer(task));
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not schedule async task on Folia: " + throwable.getMessage());
        }
    }

    public static void runFor(Plugin plugin, Entity entity, Runnable task) {
        if (!FOLIA) {
            task.run();
            return;
        }
        if (entity == null) {
            run(plugin, task);
            return;
        }
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method method = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            method.invoke(scheduler, plugin, consumer(task), null);
        } catch (Throwable throwable) {
            run(plugin, task);
        }
    }

    public static Task runTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!FOLIA) {
            return new Task(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks), false);
        }
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Method method = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            Object scheduled = method.invoke(scheduler, plugin, consumer(task), Math.max(1, delayTicks), Math.max(1, periodTicks));
            return new Task(scheduled, true);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not start timer on Folia: " + throwable.getMessage());
            return new Task(null, true);
        }
    }

    public static void cancel(Task task) {
        if (task != null) task.cancel();
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> consumer(Runnable task) {
        return (Consumer<Object>) ignored -> task.run();
    }

    public static final class Task {
        private final Object handle;
        private final boolean folia;

        private Task(Object handle, boolean folia) {
            this.handle = handle;
            this.folia = folia;
        }

        public void cancel() {
            if (handle == null) return;
            try {
                if (folia) {
                    handle.getClass().getMethod("cancel").invoke(handle);
                } else if (handle instanceof org.bukkit.scheduler.BukkitTask) {
                    ((org.bukkit.scheduler.BukkitTask) handle).cancel();
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
