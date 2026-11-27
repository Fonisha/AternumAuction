package dev.aternum.auction.script;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.config.Messages;
import dev.aternum.auction.economy.CurrencyRegistry;
import dev.aternum.auction.model.AuctionItem;
import dev.aternum.auction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Executes admin scripts bound to auction events.
 *
 * Scripts are loaded from scripts.yml and scripts/*.yml by {@link ScriptConfig}.
 * They can calculate a commission, cancel an operation, send messages, write
 * to the console or run a console command (only when explicitly allowed).
 *
 * @author Fonisha
 */
public final class ScriptManager {
    public static final String LIST = "list";
    public static final String PURCHASE = "purchase";
    public static final String BID = "bid";
    public static final String EXPIRE = "expire";
    public static final String REQUEST = "request";
    public static final String REMOVE = "remove";

    private final AternumAuctionPlugin plugin;
    private final CurrencyRegistry currencies;
    private final Messages messages;
    private final Set<String> reported = new HashSet<String>();
    private ScriptConfig config;

    public ScriptManager(AternumAuctionPlugin plugin, CurrencyRegistry currencies, Messages messages) {
        this.plugin = plugin;
        this.currencies = currencies;
        this.messages = messages;
        reload();
    }

    public void reload() {
        config = new ScriptConfig(plugin);
        config.reload();
        reported.clear();
    }

    public boolean enabled() {
        return config.enabled();
    }

    public UUID treasury() {
        return config.treasury();
    }

    public String debugInfo() {
        return config.scriptCount() + " scripts"
                + (config.enabled() ? " (enabled)" : " (disabled)")
                + (config.debug() ? ", debug" : "");
    }

    /** Builds the standard variable set available in every script. */
    public ScriptContext context(AuctionItem auction, Player actor, double price) {
        ScriptContext context = new ScriptContext().time();
        if (auction != null) {
            context.put("auction_id", auction.id());
            context.put("seller", auction.sellerName());
            context.put("seller_uuid", auction.seller());
            context.put("item_id", auction.id());
            context.put("currency", auction.currency());
            context.put("type", auction.type().name());
            context.put("category", auction.category());
            context.put("server", auction.server() == null || auction.server().isEmpty()
                    ? plugin.getConfigManager().serverId() : auction.server());
            context.put("price", price);
            context.put("list_price", auction.price());
            context.put("current_price", auction.currentPrice());
            context.put("top_bid", auction.topBid());
            context.put("total", price);
            ItemStack item = auction.item();
            context.item(item);
            if (item != null) context.put("total", price * item.getAmount());
        } else {
            context.put("price", price);
            context.put("total", price);
            context.item(null);
        }
        if (actor != null) {
            context.put("player", actor);
            context.put("buyer", actor);
            context.put("bidder", actor);
            context.put("winner", actor);
            context.put("actor", actor);
            context.put("player_uuid", actor.getUniqueId());
            context.recipient(actor);
        }
        context.put("currency_name", auction == null ? "" : currencies.displayName(auction.currency()));
        return context;
    }

    /**
     * Runs every script bound to the hook. All commissions are summed, a single
     * deny cancels the operation, and messages/commands are executed in order.
     */
    public Outcome run(String hook, ScriptContext context) {
        Outcome outcome = new Outcome();
        if (!config.enabled() || context == null) return outcome;
        for (String name : config.hook(hook)) {
            Map<String, Object> script = config.script(name);
            if (script == null) {
                warn("Hook '" + hook + "' references unknown script '" + name + "'");
                continue;
            }
            if (Boolean.FALSE.equals(script.get("enabled"))) continue;
            try {
                String condition = text(script.get("when"));
                if (!condition.isEmpty() && !Expression.bool(evaluate(condition, context, name))) continue;

                String commission = text(script.get("commission"));
                if (!commission.isEmpty()) {
                    double value = Expression.number(evaluate(commission, context, name));
                    if (value > 0) outcome.commission += value;
                }

                String deny = text(script.get("deny"));
                if (!deny.isEmpty() && Expression.bool(evaluate(deny, context, name))) {
                    outcome.denied = true;
                    if (outcome.reason == null) {
                        String reason = text(script.get("reason"));
                        outcome.reason = reason.isEmpty() ? null : context.resolve(reason);
                    }
                }

                String message = text(script.get("message"));
                if (!message.isEmpty() && context.recipient() != null) {
                    messages.sendRaw(context.recipient(), context.resolve(message));
                }
                String broadcast = text(script.get("broadcast"));
                if (!broadcast.isEmpty()) {
                    plugin.getServer().broadcastMessage(Text.color(context.resolve(broadcast)));
                }
                String log = text(script.get("log"));
                if (!log.isEmpty()) {
                    plugin.getLogger().info("[Script:" + name + "] " + Text.strip(context.resolve(log)));
                } else if (config.debug()) {
                    plugin.getLogger().info("[Script:" + name + "] hook=" + hook
                            + " commission=" + outcome.commission + " denied=" + outcome.denied);
                }
                String command = text(script.get("command"));
                if (!command.isEmpty()) {
                    if (config.allowCommands()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), context.resolve(command));
                    } else {
                        warn("Script '" + name + "' uses a command, but settings.allow-commands is false");
                    }
                }
            } catch (Throwable throwable) {
                warn("Script '" + name + "' failed on " + hook + ": " + throwable.getMessage());
            }
        }
        return outcome;
    }

    /** Convenience method for callers that only need the commission amount. */
    public double commission(String hook, ScriptContext context) {
        return run(hook, context).commission;
    }

    private Object evaluate(String expression, ScriptContext context, String script) {
        return Expression.evaluate(expression, context.variables(), config.maxSteps());
    }

    private static String text(Object value) {
        if (value == null) return "";
        if (value instanceof Boolean) return (Boolean) value ? "true" : "false";
        if (value instanceof java.util.List) {
            StringBuilder builder = new StringBuilder();
            for (Object entry : (java.util.List<?>) value) {
                if (builder.length() > 0) builder.append(" ");
                builder.append(String.valueOf(entry));
            }
            return builder.toString();
        }
        return String.valueOf(value);
    }

    private void warn(String message) {
        if (!reported.add(message)) return;
        plugin.getLogger().warning("[Scripts] " + message);
    }

    /** Result of a hook execution. */
    public static final class Outcome {
        public double commission;
        public boolean denied;
        public String reason;

        public static Outcome none() {
            return new Outcome();
        }
    }
}
