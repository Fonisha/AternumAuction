package dev.aternum.auction.command;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.economy.Currency;
import dev.aternum.auction.gui.MenuContext;
import dev.aternum.auction.gui.MenuType;
import dev.aternum.auction.model.AuctionSort;
import dev.aternum.auction.model.Transaction;
import dev.aternum.auction.service.AuctionFilter;
import dev.aternum.auction.service.AuctionResult;
import dev.aternum.auction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /ahadmin and aliases.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class AuctionAdminCommand implements CommandExecutor, TabCompleter {
    private final AternumAuctionPlugin plugin;

    public AuctionAdminCommand(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, args);
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aternumauction.admin")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                if (!sender.hasPermission("aternumauction.reload")) {
                    plugin.getMessages().send(sender, "no-permission");
                    return true;
                }
                plugin.reloadPlugin();
                plugin.getMessages().send(sender, "reloaded");
                return true;
            case "menu":
            case "gui":
                if (!(sender instanceof Player)) {
                    plugin.getMessages().send(sender, "player-only");
                    return true;
                }
                plugin.openMenu((Player) sender, MenuType.ADMIN, new MenuContext(MenuType.ADMIN, (Player) sender));
                return true;
            case "purge":
                if (!sender.hasPermission("aternumauction.admin.purge")) {
                    plugin.getMessages().send(sender, "no-permission");
                    return true;
                }
                int days = args.length > 1 ? parseInt(args[1], 7) : 7;
                int count = plugin.getAuctionManager().purge(days);
                plugin.getMessages().send(sender, "purge-done", values("amount", String.valueOf(count)));
                return true;
            case "open":
                if (!sender.hasPermission("aternumauction.admin.open")) {
                    plugin.getMessages().send(sender, "no-permission");
                    return true;
                }
                if (!(sender instanceof Player) || args.length < 2) {
                    plugin.getMessages().send(sender, "player-not-found");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                plugin.openMenu((Player) sender, MenuType.MAIN, MenuContext.main((Player) sender,
                        AuctionFilter.seller(target.getUniqueId()), AuctionSort.NEWEST, 0));
                return true;
            case "remove":
                if (!sender.hasPermission("aternumauction.admin.remove")) {
                    plugin.getMessages().send(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    help(sender);
                    return true;
                }
                UUID id;
                try {
                    id = UUID.fromString(args[1]);
                } catch (IllegalArgumentException exception) {
                    plugin.getMessages().send(sender, "not-found");
                    return true;
                }
                AuctionResult result = sender instanceof Player
                        ? plugin.getAuctionManager().remove((Player) sender, id, true)
                        : plugin.getAuctionManager().removeByConsole(id);
                plugin.getMessages().send(sender, result.messageKey(), result.placeholders());
                return true;
            case "history":
                if (!sender.hasPermission("aternumauction.admin.history")) {
                    plugin.getMessages().send(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    help(sender);
                    return true;
                }
                OfflinePlayer historyTarget = Bukkit.getOfflinePlayer(args[1]);
                List<Transaction> history = plugin.getAuctionManager().history(historyTarget.getUniqueId(), 20);
                if (history.isEmpty()) plugin.getMessages().send(sender, "history-empty");
                else for (Transaction transaction : history) {
                    sender.sendMessage(Text.color("&8• &7" + transaction.type().name() + " &f"
                            + transaction.itemName() + " &7— &e"
                            + plugin.getAuctionManager().format(transaction.currency(), transaction.amount())));
                }
                return true;
            case "limit":
                if (!sender.hasPermission("aternumauction.admin.limit")) {
                    plugin.getMessages().send(sender, "no-permission");
                    return true;
                }
                limit(sender, args);
                return true;
            case "convert":
                if (!sender.hasPermission("aternumauction.admin.convert")) {
                    plugin.getMessages().send(sender, "no-permission");
                    return true;
                }
                convert(sender, args);
                return true;
            default:
                help(sender);
                return true;
        }
    }

    private void limit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            help(sender);
            return;
        }
        String action = args[1].toLowerCase();
        if (!Arrays.asList("set", "give", "take", "reset").contains(action)) {
            OfflinePlayer info = Bukkit.getOfflinePlayer(args[1]);
            int used = plugin.getAuctionManager().count(info.getUniqueId());
            int base = info.getPlayer() == null ? plugin.getConfigManager().integer("settings.limits.default", 10)
                    : plugin.getConfigManager().limit(info.getPlayer(), 0);
            int total = base + plugin.getStorageManager().limitBonus(info.getUniqueId());
            plugin.getMessages().send(sender, "limit-info", values(
                    "used", String.valueOf(used), "limit", String.valueOf(total)));
            return;
        }
        if (args.length < 3) {
            help(sender);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        int amount = args.length > 3 ? parseInt(args[3], 0) : 0;
        int base = target.getPlayer() == null ? plugin.getConfigManager().integer("settings.limits.default", 10)
                : plugin.getConfigManager().limit(target.getPlayer(), 0);
        int current = plugin.getStorageManager().limitBonus(target.getUniqueId());
        int bonus;
        switch (action) {
            case "set":
                bonus = Math.max(0, amount - base);
                break;
            case "give":
                bonus = current + Math.max(0, amount);
                break;
            case "take":
                bonus = Math.max(0, current - Math.max(0, amount));
                break;
            default:
                bonus = 0;
                break;
        }
        plugin.getStorageManager().setLimitBonus(target.getUniqueId(), bonus);
        plugin.getMessages().send(sender, "admin-limit-set", values(
                "player", target.getName() == null ? target.getUniqueId().toString() : target.getName(),
                "limit", String.valueOf(base + bonus)));
    }

    private void convert(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Text.color("&6&lAternumAuction &7— converters"));
            for (String name : plugin.getConverterService().available()) {
                sender.sendMessage(Text.color("&e• &f" + name));
            }
            sender.sendMessage(Text.color("&7Usage: &f/ahadmin convert <plugin>"));
            return;
        }
        String name = args[1];
        int imported = plugin.getConverterService().convert(name);
        if (imported < 0) {
            plugin.getMessages().send(sender, "converter-not-found", values("plugin", name));
            return;
        }
        plugin.getMessages().send(sender, "converted", values("plugin", name, "amount", String.valueOf(imported)));
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.color("&6&lAternumAuction &7— admin"));
        sender.sendMessage(Text.color("&e/ahadmin menu"));
        sender.sendMessage(Text.color("&e/ahadmin open <player>"));
        sender.sendMessage(Text.color("&e/ahadmin remove <uuid>"));
        sender.sendMessage(Text.color("&e/ahadmin purge <days>"));
        sender.sendMessage(Text.color("&e/ahadmin history <player>"));
        sender.sendMessage(Text.color("&e/ahadmin limit set|give|take|reset <player> [amount]"));
        sender.sendMessage(Text.color("&e/ahadmin convert [plugin]"));
        sender.sendMessage(Text.color("&e/ahadmin reload"));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static Map<String, String> values(String... values) {
        Map<String, String> result = new HashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) result.put(values[index], values[index + 1]);
        return result;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("menu", "open", "remove", "purge", "history", "limit", "convert", "reload"), args[0]);
        }
        if (args.length == 2 && "limit".equalsIgnoreCase(args[0])) {
            return filter(Arrays.asList("set", "give", "take", "reset"), args[1]);
        }
        if (args.length == 2 && "convert".equalsIgnoreCase(args[0])) {
            return filter(plugin.getConverterService().available(), args[1]);
        }
        if (args.length == 2 && Arrays.asList("open", "history").contains(args[0].toLowerCase())) {
            List<String> names = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) names.add(player.getName());
            return filter(names, args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase().startsWith(prefix.toLowerCase())) result.add(value);
        return result;
    }
}
