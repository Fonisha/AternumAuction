package dev.aternum.auction.command;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.economy.Currency;
import dev.aternum.auction.gui.MenuContext;
import dev.aternum.auction.gui.MenuType;
import dev.aternum.auction.model.AuctionSort;
import dev.aternum.auction.model.AuctionType;
import dev.aternum.auction.service.AuctionFilter;
import dev.aternum.auction.service.AuctionResult;
import dev.aternum.auction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /ah and aliases.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class AuctionCommand implements CommandExecutor, TabCompleter {
    private final AternumAuctionPlugin plugin;

    public AuctionCommand(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            open(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            if (!sender.hasPermission("aternumauction.reload")) {
                plugin.getMessages().send(sender, "no-permission");
                return true;
            }
            plugin.reloadPlugin();
            plugin.getMessages().send(sender, "reloaded");
            return true;
        }
        if ("version".equals(sub)) {
            sender.sendMessage(Text.color(plugin.getMessages().prefix() + "AternumAuction " + plugin.getDescription().getVersion()));
            return true;
        }
        if ("admin".equals(sub)) {
            return plugin.getAdminCommand().execute(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        if (!(sender instanceof Player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }
        Player player = (Player) sender;
        switch (sub) {
            case "open":
            case "menu":
                open(sender);
                return true;
            case "sell":
                create(player, args, AuctionType.BIN);
                return true;
            case "buy":
            case "request":
                create(player, args, AuctionType.BUY_REQUEST);
                return true;
            case "bid":
                create(player, args, AuctionType.BID);
                return true;
            case "view":
                view(player, args);
                return true;
            case "mine":
                if (!hasUse(player)) return denied(player);
                plugin.openMenu(player, MenuType.MY, MenuContext.main(player, AuctionFilter.mine(player.getUniqueId()), sort(player), 0));
                return true;
            case "expired":
            case "claim":
                if (!player.hasPermission("aternumauction.expired")) return denied(player);
                plugin.openMenu(player, MenuType.EXPIRED, new MenuContext(MenuType.EXPIRED, player));
                return true;
            case "history":
                if (!player.hasPermission("aternumauction.history")) return denied(player);
                plugin.openMenu(player, MenuType.HISTORY, new MenuContext(MenuType.HISTORY, player));
                return true;
            case "categories":
                if (!hasUse(player)) return denied(player);
                plugin.openMenu(player, MenuType.MAIN, MenuContext.main(player, AuctionFilter.type(AuctionType.BIN), sort(player), 0));
                return true;
            case "limit":
                if (!hasUse(player)) return denied(player);
                int used = plugin.getAuctionManager().count(player.getUniqueId());
                int limit = plugin.getConfigManager().limit(player, plugin.getStorageManager().limitBonus(player.getUniqueId()));
                plugin.getMessages().send(player, "limit-info", values(
                        "used", String.valueOf(used),
                        "limit", limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit)));
                return true;
            case "currency":
                if (!hasUse(player)) return denied(player);
                currency(player, args);
                return true;
            case "help":
                if (!hasUse(player)) return denied(player);
                for (String line : plugin.getMessages().list("help")) {
                    player.sendMessage(Text.color(line.replace("%version%", plugin.getDescription().getVersion())));
                }
                return true;
            default:
                plugin.getMessages().send(player, "unknown-command");
                return true;
        }
    }

    private boolean hasUse(Player player) {
        return player.hasPermission("aternumauction.use");
    }

    private boolean denied(Player player) {
        plugin.getMessages().send(player, "no-permission");
        return true;
    }

    private void open(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.getMessages().send(sender, "player-only");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aternumauction.use")) {
            plugin.getMessages().send(player, "no-permission");
            return;
        }
        plugin.openMenu(player, MenuType.MAIN, MenuContext.main(player, AuctionFilter.type(AuctionType.BIN), sort(player), 0));
    }

    private void create(Player player, String[] args, AuctionType type) {
        String permission = type == AuctionType.BID ? "aternumauction.bid"
                : type == AuctionType.BUY_REQUEST ? "aternumauction.buy" : "aternumauction.sell";
        if (!player.hasPermission(permission)) {
            plugin.getMessages().send(player, "no-permission");
            return;
        }
        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR) {
            plugin.getMessages().send(player, "no-item");
            return;
        }
        if (args.length < 2) {
            plugin.getInputManager().request(player, plugin.getMessages().get("price-hint"), value -> {
                if (value != null) create(player, new String[]{args[0], value}, type);
            });
            return;
        }
        double minimum = plugin.getConfigManager().decimal("settings.min-price", 1);
        double maximum = plugin.getConfigManager().decimal("settings.max-price", 1000000000);
        double price;
        try {
            price = Double.parseDouble(args[1].replace(',', '.'));
        } catch (NumberFormatException exception) {
            plugin.getMessages().send(player, "invalid-price", values(
                    "min", String.valueOf(minimum), "max", String.valueOf(maximum)));
            return;
        }
        int amount = hand.getAmount();
        if (args.length > 2) {
            try {
                amount = Math.max(1, Math.min(hand.getAmount(), Integer.parseInt(args[2])));
            } catch (NumberFormatException exception) {
                plugin.getMessages().send(player, "invalid-amount");
                return;
            }
        }
        int hours = plugin.getConfigManager().defaultDurationHours();
        String preferred = plugin.getPlayerSettings().currency(player.getUniqueId(), plugin.getCurrencyRegistry().defaultId());
        Currency preferredCurrency = plugin.getCurrencyRegistry().get(preferred);
        if (preferredCurrency == null || !preferredCurrency.available()) {
            preferred = plugin.getCurrencyRegistry().defaultId();
        }
        List<Currency> available = plugin.getCurrencyRegistry().enabledFor(player);
        boolean select = plugin.getConfig().getBoolean("currencies.selection-gui", true)
                && player.hasPermission("aternumauction.currency.select")
                && available.size() > 1;

        if (select) {
            plugin.openMenu(player, MenuType.CURRENCY, MenuContext.listing(player, hand, price, amount, type, hours));
            return;
        }
        AuctionResult result = plugin.getAuctionManager().list(player, hand, price, amount, type,
                preferred, hours);
        if (result.messageKey() != null) plugin.getMessages().send(player, result.messageKey(), result.placeholders());
    }

    private void view(Player player, String[] args) {
        if (!player.hasPermission("aternumauction.view")) {
            plugin.getMessages().send(player, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "player-not-found");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        plugin.openMenu(player, MenuType.MAIN,
                MenuContext.main(player, AuctionFilter.seller(target.getUniqueId()), sort(player), 0));
    }

    private void currency(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessages().send(player, "currency-selected", values("currency",
                    plugin.getCurrencyRegistry().displayName(plugin.getPlayerSettings().currency(player.getUniqueId(),
                            plugin.getCurrencyRegistry().defaultId()))));
            return;
        }
        Currency currency = plugin.getCurrencyRegistry().get(args[1]);
        if (currency == null) {
            plugin.getMessages().send(player, "currency-not-found");
            return;
        }
        plugin.getPlayerSettings().setCurrency(player.getUniqueId(), currency.id());
        plugin.getMessages().send(player, "currency-selected", values("currency", currency.displayName()));
    }

    private AuctionSort sort(Player player) {
        String stored = plugin.getPlayerSettings().sort(player.getUniqueId(),
                plugin.getConfig().getString("settings.default-sort", "newest"));
        return AuctionSort.from(stored);
    }

    private static Map<String, String> values(String... values) {
        Map<String, String> result = new HashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) result.put(values[index], values[index + 1]);
        return result;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(Arrays.asList(
                    "open", "sell", "buy", "bid", "view", "mine", "expired", "history",
                    "categories", "limit", "currency", "help", "version", "reload", "admin"));
            return filter(values, args[0]);
        }
        if (args.length == 2 && "currency".equalsIgnoreCase(args[0])) {
            List<String> ids = new ArrayList<>();
            for (Currency currency : plugin.getCurrencyRegistry().all()) ids.add(currency.id());
            return filter(ids, args[1]);
        }
        if (args.length == 2 && "view".equalsIgnoreCase(args[0])) {
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
