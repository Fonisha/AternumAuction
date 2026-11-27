package dev.aternum.auction.service;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.api.event.AuctionBidEvent;
import dev.aternum.auction.api.event.AuctionClaimEvent;
import dev.aternum.auction.api.event.AuctionExpireEvent;
import dev.aternum.auction.api.event.AuctionPostPurchaseEvent;
import dev.aternum.auction.api.event.AuctionPostSellEvent;
import dev.aternum.auction.api.event.AuctionPrePurchaseEvent;
import dev.aternum.auction.api.event.AuctionPreSellEvent;
import dev.aternum.auction.api.event.AuctionRemoveEvent;
import dev.aternum.auction.config.BlacklistManager;
import dev.aternum.auction.config.CategoryManager;
import dev.aternum.auction.config.ConfigManager;
import dev.aternum.auction.config.Messages;
import dev.aternum.auction.economy.CurrencyRegistry;
import dev.aternum.auction.model.AuctionItem;
import dev.aternum.auction.model.AuctionSort;
import dev.aternum.auction.model.AuctionType;
import dev.aternum.auction.model.Payout;
import dev.aternum.auction.model.Transaction;
import dev.aternum.auction.model.TransactionType;
import dev.aternum.auction.script.ScriptContext;
import dev.aternum.auction.script.ScriptManager;
import dev.aternum.auction.storage.StorageManager;
import dev.aternum.auction.util.Scheduler;
import dev.aternum.auction.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Core auction logic: listing, buying, bidding, expiry, claims and history.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class AuctionManager implements Listener {
    private final AternumAuctionPlugin plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final CategoryManager categories;
    private final BlacklistManager blacklist;
    private final CurrencyRegistry currencies;
    private final StorageManager storage;
    private final DiscordService discord;
    private final ScriptManager scripts;

    private final Map<UUID, AuctionItem> auctions = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> claims = new ConcurrentHashMap<>();
    private final Map<UUID, List<Payout>> payouts = new ConcurrentHashMap<>();
    private final Map<UUID, List<Transaction>> history = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private Scheduler.Task expireTask;

    public AuctionManager(AternumAuctionPlugin plugin, ConfigManager config, Messages messages,
                          CategoryManager categories, BlacklistManager blacklist, CurrencyRegistry currencies,
                           StorageManager storage, DiscordService discord) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.categories = categories;
        this.blacklist = blacklist;
        this.currencies = currencies;
        this.storage = storage;
        this.discord = discord;
        this.scripts = plugin.getScriptManager();
    }

    public void load() {
        auctions.clear();
        claims.clear();
        payouts.clear();
        history.clear();
        for (AuctionItem auction : storage.loadAuctions()) {
            auctions.put(auction.id(), auction);
        }
        for (Map.Entry<UUID, List<ItemStack>> entry : storage.loadClaims().entrySet()) {
            claims.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        for (Payout payout : storage.loadPayouts()) {
            payouts.computeIfAbsent(payout.player(), key -> new ArrayList<Payout>()).add(payout);
        }
        for (Transaction transaction : storage.loadTransactions()) {
            List<Transaction> list = history.computeIfAbsent(transaction.player(), key -> new ArrayList<Transaction>());
            list.add(transaction);
            if (list.size() > config.integer("settings.history-limit", 500)) list.remove(0);
        }
        startExpiryTask();
    }

    public void reload() {
        if (expireTask != null) expireTask.cancel();
        storage.flush();
        load();
    }

    public void shutdown() {
        if (expireTask != null) expireTask.cancel();
        expireTask = null;
    }

    private void startExpiryTask() {
        if (expireTask != null) expireTask.cancel();
        expireTask = Scheduler.runTimer(plugin, new Runnable() {
            @Override
            public void run() {
                expire();
            }
        }, 20L * 30, 20L * 30);
    }

    public AuctionResult list(Player seller, ItemStack hand, double price, int amount, AuctionType type,
                              String currency, int hours) {
        if (hand == null || hand.getType() == Material.AIR) return AuctionResult.fail("no-item");
        if (type == AuctionType.BIN && !config.bool("settings.allow-bin", true)) return AuctionResult.fail("disabled");
        if (type == AuctionType.BID && !config.bool("settings.allow-bidding", true)) return AuctionResult.fail("disabled");
        if (type == AuctionType.BUY_REQUEST) return listBuyRequest(seller, hand, price, amount, currency, hours);

        double min = config.decimal("settings.min-price", 1);
        double max = config.decimal("settings.max-price", 1000000000);
        if (price < min || price > max || Double.isNaN(price) || Double.isInfinite(price)) {
            return AuctionResult.fail("invalid-price", values("min", format(currency, min), "max", format(currency, max)));
        }
        if (amount < 1 || amount > hand.getAmount()) return AuctionResult.fail("invalid-amount");
        if (!currencies.available(currency)) return AuctionResult.fail("no-economy", values("currency", currency));
        dev.aternum.auction.economy.Currency selected = currencies.get(currency);
        if (selected != null && selected.permission() != null && !selected.permission().isEmpty()
                && !seller.hasPermission(selected.permission())) return AuctionResult.fail("no-permission");
        if (seller.getGameMode() == GameMode.CREATIVE && !config.bool("settings.allow-creative", false)
                && !seller.hasPermission("aternumauction.bypass.creative")) return AuctionResult.fail("no-permission");

        String blocked = blacklist.check(seller, hand);
        if (blocked != null) return AuctionResult.fail(blocked);

        int active = count(seller.getUniqueId());
        int limit = config.limit(seller, storage.limitBonus(seller.getUniqueId()));
        if (active >= limit) return AuctionResult.fail("listing-limit", values("limit", String.valueOf(limit)));

        ItemStack listed = hand.clone();
        listed.setAmount(amount);
        AuctionPreSellEvent pre = new AuctionPreSellEvent(seller, listed, amount, price, type, currency);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) return AuctionResult.fail("cancelled");
        price = pre.getPrice();
        if (!Double.isFinite(price) || price < min || price > max) {
            return AuctionResult.fail("invalid-price", values("min", format(currency, min), "max", format(currency, max)));
        }

        ScriptContext listContext = scripts.context(null, seller, price).item(listed);
        listContext.put("type", type.name());
        listContext.put("currency", currency);
        listContext.put("category", categories.resolve(listed));
        ScriptManager.Outcome listScripts = scripts.run(ScriptManager.LIST, listContext);
        if (listScripts.denied) {
            if (listScripts.reason != null) messages.sendRaw(seller, listScripts.reason);
            return AuctionResult.fail("cancelled");
        }

        hand.setAmount(hand.getAmount() - amount);
        if (hand.getAmount() <= 0) seller.getInventory().setItemInMainHand(null);

        long now = System.currentTimeMillis();
        AuctionItem auction = new AuctionItem(
                UUID.randomUUID(), seller.getUniqueId(), seller.getName(), listed, type, price,
                Math.max(0.01, config.decimal("settings.bid-step", 1)), currency, categories.resolve(listed),
                config.serverId(), now, now + config.durationMillis(hours), 0, null, "", 0
        );
        auctions.put(auction.id(), auction);
        storage.saveAuction(auction);
        record(seller.getUniqueId(), seller.getName(), auction, TransactionType.LISTING, 0, currency, auction.displayName(), "");
        Bukkit.getPluginManager().callEvent(new AuctionPostSellEvent(seller, auction));
        SoundUtil.play(plugin, seller, "success");

        Map<String, String> placeholders = placeholders(auction);
        if (config.bool("settings.announce.enabled", true)
                && price >= config.decimal("settings.announce.min-price", 1000)) {
            messages.broadcast("announce-listing", placeholders);
        }
        discord.send("listing", "Новый лот", seller.getName() + " — " + auction.displayName() + " — " + format(currency, price));
        return AuctionResult.ok(type == AuctionType.BID ? "listed-bid" : "listed", placeholders, auction);
    }

    /** Creates a request for another player to sell this item to the requester. */
    private AuctionResult listBuyRequest(Player requester, ItemStack hand, double price, int amount,
                                         String currency, int hours) {
        if (amount < 1 || amount > hand.getAmount()) return AuctionResult.fail("invalid-amount");
        if (!currencies.available(currency)) return AuctionResult.fail("no-economy", values("currency", currency));
        dev.aternum.auction.economy.Currency selected = currencies.get(currency);
        if (selected != null && selected.permission() != null && !selected.permission().isEmpty()
                && !requester.hasPermission(selected.permission())) return AuctionResult.fail("no-permission");
        if (requester.getGameMode() == GameMode.CREATIVE && !config.bool("settings.allow-creative", false)
                && !requester.hasPermission("aternumauction.bypass.creative")) return AuctionResult.fail("no-permission");
        String blocked = blacklist.check(requester, hand);
        if (blocked != null) return AuctionResult.fail(blocked);
        int active = count(requester.getUniqueId());
        int limit = config.limit(requester, storage.limitBonus(requester.getUniqueId()));
        if (active >= limit) return AuctionResult.fail("listing-limit", values("limit", String.valueOf(limit)));

        ItemStack wanted = hand.clone();
        wanted.setAmount(amount);
        AuctionPreSellEvent pre = new AuctionPreSellEvent(requester, wanted, amount, price, AuctionType.BUY_REQUEST, currency);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) return AuctionResult.fail("cancelled");
        price = pre.getPrice();
        if (price < config.decimal("settings.min-price", 1) || price > config.decimal("settings.max-price", 1000000000)
                || Double.isNaN(price) || Double.isInfinite(price)) {
            return AuctionResult.fail("invalid-price", values("min", format(currency, config.decimal("settings.min-price", 1)),
                    "max", format(currency, config.decimal("settings.max-price", 1000000000))));
        }
        if (!currencies.has(currency, requester, price) || !currencies.withdraw(currency, requester, price)) {
            return AuctionResult.fail("not-enough");
        }
        long now = System.currentTimeMillis();
        AuctionItem request = new AuctionItem(UUID.randomUUID(), requester.getUniqueId(), requester.getName(), wanted,
                AuctionType.BUY_REQUEST, price, 0, currency, categories.resolve(wanted), config.serverId(), now,
                now + config.durationMillis(hours), 0, null, "", 0);
        auctions.put(request.id(), request);
        storage.saveAuction(request);
        record(requester.getUniqueId(), requester.getName(), request, TransactionType.LISTING, 0, currency,
                request.displayName(), "");
        Bukkit.getPluginManager().callEvent(new AuctionPostSellEvent(requester, request));
        return AuctionResult.ok("buy-request-listed", placeholders(request), request);
    }

    /** Fulfils a buy request with the item in the seller's hand. */
    public AuctionResult fulfilRequest(Player seller, UUID id, int amount) {
        lock.lock();
        try {
            AuctionItem request = auctions.get(id);
            if (request == null || request.type() != AuctionType.BUY_REQUEST || !request.isActive()) {
                return AuctionResult.fail("not-found");
            }
            if (request.seller().equals(seller.getUniqueId())) return AuctionResult.fail("own-lot");
            ItemStack hand = seller.getItemInHand();
            ItemStack requested = request.item();
            if (hand == null || hand.getType() == Material.AIR || !sameItemIgnoringAmount(hand, requested)) {
                return AuctionResult.fail("request-item-mismatch");
            }
            int deliver = Math.min(Math.max(1, amount), Math.min(hand.getAmount(), requested.getAmount()));
            ItemStack original = hand.clone();
            ItemStack sold = hand.clone();
            sold.setAmount(deliver);
            hand.setAmount(hand.getAmount() - deliver);
            if (hand.getAmount() <= 0) seller.getInventory().setItemInMainHand(null);
            if (!storage.reserveAuction(id)) {
                seller.getInventory().setItemInMainHand(original);
                return AuctionResult.fail("not-active");
            }
            auctions.remove(id);
            double unit = request.price() / Math.max(1, requested.getAmount());
            double payout = unit * deliver;
            ScriptContext requestContext = scripts.context(request, seller, payout);
            requestContext.put("buyer", Bukkit.getOfflinePlayer(request.seller()));
            ScriptManager.Outcome requestScripts = scripts.run(ScriptManager.REQUEST, requestContext);
            double requestCommission = Math.min(Math.max(0, requestScripts.commission), payout);
            depositOrQueue(seller.getUniqueId(), request.currency(), payout - requestCommission,
                    "Продажа по запросу " + request.displayName());
            if (requestCommission > 0) {
                depositCommission(request.currency(), requestCommission, "Комиссия " + request.displayName());
            }
            record(seller.getUniqueId(), seller.getName(), request, TransactionType.REQUEST_FULFILL, payout - requestCommission,
                    request.currency(), request.displayName(), request.sellerName());
            record(request.seller(), request.sellerName(), request, TransactionType.PURCHASE, payout,
                    request.currency(), request.displayName(), seller.getName());
            if (deliver < requested.getAmount()) {
                // Remaining quantity is recreated as a fresh request with the original escrow remainder.
                ItemStack remaining = requested.clone();
                remaining.setAmount(remaining.getAmount() - deliver);
                AuctionItem remainder = new AuctionItem(UUID.randomUUID(), request.seller(), request.sellerName(), remaining,
                        AuctionType.BUY_REQUEST, request.price() - payout, 0, request.currency(), request.category(),
                        request.server(), request.createdAt(), request.expiresAt(), 0, null, "", request.views());
                auctions.put(remainder.id(), remainder);
                storage.saveAuction(remainder);
            }
            return AuctionResult.ok("buy-request-fulfilled", values("item", request.displayName(),
                    "price", format(request.currency(), payout)), request);
        } finally {
            lock.unlock();
        }
    }

    public AuctionResult buy(Player buyer, UUID id) {
        lock.lock();
        try {
            AuctionItem auction = auctions.get(id);
            if (auction == null || !visible(auction)) return AuctionResult.fail("not-found");
            if (!auction.isActive()) return AuctionResult.fail("not-active");
            if (auction.type() == AuctionType.BID || auction.type() == AuctionType.BUY_REQUEST) return AuctionResult.fail("not-active");
            if (config.bool("settings.prevent-own-purchase", true) && auction.seller().equals(buyer.getUniqueId())) {
                return AuctionResult.fail("own-lot");
            }
            String currency = auction.currency();
            if (!currencies.available(currency)) return AuctionResult.fail("no-economy", values("currency", currency));

            double price = auction.price();
            AuctionPrePurchaseEvent event = new AuctionPrePurchaseEvent(buyer, auction, price);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return AuctionResult.fail("cancelled");
            price = event.getPrice();
            double min = config.decimal("settings.min-price", 1);
            double max = config.decimal("settings.max-price", 1000000000);
            if (!Double.isFinite(price) || price < min || price > max) {
                return AuctionResult.fail("invalid-price", values("min", format(currency, min), "max", format(currency, max)));
            }

            if (!currencies.has(currency, buyer, price)) return AuctionResult.fail("not-enough");
            if (!hasSpace(buyer, auction.item())) return AuctionResult.fail("inventory-full");

            ScriptManager.Outcome purchaseScripts = scripts.run(ScriptManager.PURCHASE,
                    scripts.context(auction, buyer, price));
            if (purchaseScripts.denied) {
                if (purchaseScripts.reason != null) messages.sendRaw(buyer, purchaseScripts.reason);
                return AuctionResult.fail("cancelled");
            }

            if (!currencies.withdraw(currency, buyer, price)) return AuctionResult.fail("not-enough");

            if (!storage.reserveAuction(id)) {
                depositOrQueue(buyer.getUniqueId(), currency, price,
                        "Возврат покупки " + auction.displayName());
                return AuctionResult.fail("not-active");
            }
            auctions.remove(id);

            Map<Integer, ItemStack> left = buyer.getInventory().addItem(auction.item());
            for (ItemStack item : left.values()) addClaim(buyer.getUniqueId(), item);

            double tax = tax(price);
            double commission = Math.min(Math.max(0, purchaseScripts.commission), Math.max(0, price - tax));
            depositOrQueue(auction.seller(), currency, price - tax - commission, "Продажа " + auction.displayName());
            if (commission > 0) {
                depositCommission(currency, commission, "Комиссия " + auction.displayName());
            }
            record(buyer.getUniqueId(), buyer.getName(), auction, TransactionType.PURCHASE, price, currency, auction.displayName(), auction.sellerName());
            record(auction.seller(), auction.sellerName(), auction, TransactionType.SALE, price - tax - commission, currency, auction.displayName(), buyer.getName());

            Bukkit.getPluginManager().callEvent(new AuctionPostPurchaseEvent(buyer, auction, price));
            SoundUtil.play(plugin, buyer, "success");

            Map<String, String> placeholders = placeholders(auction);
            if (config.bool("settings.announce.enabled", true)
                    && price >= config.decimal("settings.announce.min-price", 1000)) {
                messages.broadcast("announce-purchase", placeholders);
            }
            discord.send("purchase", "Покупка", buyer.getName() + " — " + auction.displayName() + " — " + format(currency, price));
            return AuctionResult.ok("bought", placeholders, auction);
        } finally {
            lock.unlock();
        }
    }

    public AuctionResult bid(Player bidder, UUID id) {
        lock.lock();
        try {
            AuctionItem auction = auctions.get(id);
            if (auction == null || !visible(auction)) return AuctionResult.fail("not-found");
            if (!auction.isActive()) return AuctionResult.fail("not-active");
            if (auction.type() != AuctionType.BID) return AuctionResult.fail("not-active");
            if (auction.seller().equals(bidder.getUniqueId())) return AuctionResult.fail("bid-own");
            if (bidder.getUniqueId().equals(auction.topBidder())) {
                return AuctionResult.fail("bid-too-low", values("price", format(auction.currency(), auction.currentPrice())));
            }
            String currency = auction.currency();
            double amount = auction.currentPrice();
            if (!Double.isFinite(amount) || amount <= 0) return AuctionResult.fail("invalid-price");
            if (!currencies.available(currency)) return AuctionResult.fail("no-economy", values("currency", currency));

            AuctionBidEvent event = new AuctionBidEvent(bidder, auction, amount);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return AuctionResult.fail("cancelled");

            if (!currencies.has(currency, bidder, amount)) return AuctionResult.fail("not-enough");

            ScriptManager.Outcome bidScripts = scripts.run(ScriptManager.BID,
                    scripts.context(auction, bidder, amount));
            if (bidScripts.denied) {
                if (bidScripts.reason != null) messages.sendRaw(bidder, bidScripts.reason);
                return AuctionResult.fail("cancelled");
            }

            if (!currencies.withdraw(currency, bidder, amount)) return AuctionResult.fail("not-enough");

            if (auction.topBidder() != null) {
                depositOrQueue(auction.topBidder(), currency, auction.topBid(), "Возврат ставки " + auction.displayName());
                record(auction.topBidder(), auction.topBidderName(), auction, TransactionType.REFUND,
                        auction.topBid(), currency, auction.displayName(), bidder.getName());
            }
            auction.placeBid(bidder.getUniqueId(), bidder.getName(), amount);
            storage.saveAuction(auction);
            record(bidder.getUniqueId(), bidder.getName(), auction, TransactionType.BID, amount, currency, auction.displayName(), auction.sellerName());
            SoundUtil.play(plugin, bidder, "success");
            discord.send("bid", "Новая ставка", bidder.getName() + " — " + auction.displayName() + " — " + format(currency, amount));
            return AuctionResult.ok("bid-placed", values("price", format(currency, amount)), auction);
        } finally {
            lock.unlock();
        }
    }

    public AuctionResult remove(Player actor, UUID id, boolean admin) {
        return removeInternal(id, actor, actor == null ? "CONSOLE" : actor.getName(), admin);
    }

    public AuctionResult removeByConsole(UUID id) {
        return removeInternal(id, null, "CONSOLE", true);
    }

    private AuctionResult removeInternal(UUID id, Player actor, String actorName, boolean admin) {
        lock.lock();
        try {
            AuctionItem auction = auctions.get(id);
            if (auction == null) return AuctionResult.fail("not-found");
            if (!admin && (actor == null || !auction.seller().equals(actor.getUniqueId()))) {
                return AuctionResult.fail("no-permission");
            }
            ScriptManager.Outcome removeScripts = scripts.run(ScriptManager.REMOVE,
                    scripts.context(auction, actor, auction.currentPrice()));
            if (removeScripts.denied) {
                if (removeScripts.reason != null && actor != null) messages.sendRaw(actor, removeScripts.reason);
                return AuctionResult.fail("no-permission");
            }
            if (!storage.reserveAuction(id)) {
                return AuctionResult.fail("not-found");
            }
            auctions.remove(id);

            if (auction.type() == AuctionType.BUY_REQUEST) {
                depositOrQueue(auction.seller(), auction.currency(), auction.price(),
                        "Возврат запроса " + auction.displayName());
                record(auction.seller(), auction.sellerName(), auction, TransactionType.REFUND,
                        auction.price(), auction.currency(), auction.displayName(), actorName);
            } else if (auction.type() == AuctionType.BID && auction.topBidder() != null) {
                depositOrQueue(auction.topBidder(), auction.currency(), auction.topBid(), "Возврат ставки " + auction.displayName());
                record(auction.topBidder(), auction.topBidderName(), auction, TransactionType.REFUND,
                        auction.topBid(), auction.currency(), auction.displayName(), actorName);
            }
            if (auction.type() != AuctionType.BUY_REQUEST) deliver(auction.seller(), auction.item());
            record(auction.seller(), auction.sellerName(), auction, TransactionType.ADMIN_REMOVE, 0,
                    auction.currency(), auction.displayName(), actorName);
            Bukkit.getPluginManager().callEvent(new AuctionRemoveEvent(actor, auction, admin));
            discord.send("admin", admin ? "Лот удалён администратором" : "Лот снят",
                    auction.sellerName() + " — " + auction.displayName());
            return AuctionResult.ok(admin ? "admin-removed" : "removed", placeholders(auction), auction);
        } finally {
            lock.unlock();
        }
    }

    public void claim(Player player) {
        lock.lock();
        try {
            List<ItemStack> items = claims.remove(player.getUniqueId());
            List<Payout> pending = payouts.remove(player.getUniqueId());
            int itemCount = 0;
            List<ItemStack> remaining = new ArrayList<>();
            if (items != null) for (ItemStack item : items) {
                Map<Integer, ItemStack> left = player.getInventory().addItem(item);
                if (left.isEmpty()) itemCount++;
                else remaining.addAll(left.values());
            }
            if (!remaining.isEmpty()) claims.put(player.getUniqueId(), remaining);

            int paid = 0;
            List<Payout> unpaid = new ArrayList<>();
            if (pending != null) for (Payout payout : pending) {
                if (currencies.deposit(payout.currency(), player, payout.amount())) {
                    paid++;
                    record(player.getUniqueId(), player.getName(), null, TransactionType.CLAIM,
                            payout.amount(), payout.currency(), payout.description(), "");
                    storage.deletePayout(payout);
                } else unpaid.add(payout);
            }
            if (!unpaid.isEmpty()) payouts.put(player.getUniqueId(), unpaid);

            storage.saveClaims(player.getUniqueId(), remaining);
            Bukkit.getPluginManager().callEvent(new AuctionClaimEvent(player,
                    items == null ? Collections.<ItemStack>emptyList() : items,
                    pending == null ? Collections.<Payout>emptyList() : pending));
            if (itemCount == 0 && paid == 0) messages.send(player, "nothing-to-claim");
            else {
                if (itemCount > 0) messages.send(player, "claimed-items", values("amount", String.valueOf(itemCount)));
                if (paid > 0) messages.send(player, "claimed-money", values("amount", String.valueOf(paid)));
            }
        } finally {
            lock.unlock();
        }
    }

    private void expire() {
        lock.lock();
        try {
            for (AuctionItem auction : new ArrayList<>(auctions.values())) {
                if (!auction.isExpired()) continue;
                if (config.multiServer() && !auction.server().isEmpty()
                        && !auction.server().equalsIgnoreCase(config.serverId())) continue;
                if (!storage.reserveAuction(auction.id())) {
                    continue;
                }
                auctions.remove(auction.id());

                Player winner = null;
                if (auction.type() == AuctionType.BUY_REQUEST) {
                    depositOrQueue(auction.seller(), auction.currency(), auction.price(),
                            "Истёкший запрос " + auction.displayName());
                    record(auction.seller(), auction.sellerName(), auction, TransactionType.REFUND,
                            auction.price(), auction.currency(), auction.displayName(), "");
                } else if (auction.type() == AuctionType.BID && auction.topBidder() != null) {
                    winner = Bukkit.getPlayer(auction.topBidder());
                    deliver(auction.topBidder(), auction.item());
                    double tax = tax(auction.topBid());
                    ScriptContext expireContext = scripts.context(auction, winner, auction.topBid());
                    expireContext.put("buyer", Bukkit.getOfflinePlayer(auction.topBidder()));
                    ScriptManager.Outcome expireScripts = scripts.run(ScriptManager.EXPIRE, expireContext);
                    double expireCommission = Math.min(Math.max(0, expireScripts.commission),
                            Math.max(0, auction.topBid() - tax));
                    depositOrQueue(auction.seller(), auction.currency(),
                            auction.topBid() - tax - expireCommission,
                            "Завершение аукциона " + auction.displayName());
                    if (expireCommission > 0) {
                        depositCommission(auction.currency(), expireCommission, "Комиссия " + auction.displayName());
                    }
                    record(auction.seller(), auction.sellerName(), auction, TransactionType.SALE,
                            auction.topBid() - tax - expireCommission, auction.currency(), auction.displayName(), auction.topBidderName());
                    if (winner != null) messages.send(winner, "won",
                            values("item", auction.displayName(), "price", format(auction.currency(), auction.topBid())));
                } else {
                    deliver(auction.seller(), auction.item());
                    Player seller = Bukkit.getPlayer(auction.seller());
                    if (seller != null) messages.send(seller, "expired");
                }
                if (auction.type() != AuctionType.BID || auction.topBidder() == null) {
                    scripts.run(ScriptManager.EXPIRE, scripts.context(auction,
                            Bukkit.getPlayer(auction.seller()), auction.currentPrice()));
                }
                record(auction.seller(), auction.sellerName(), auction, TransactionType.EXPIRE, 0,
                        auction.currency(), auction.displayName(), "");
                Bukkit.getPluginManager().callEvent(new AuctionExpireEvent(auction, winner));
                discord.send("expiration", "Лот истёк", auction.sellerName() + " — " + auction.displayName());
            }
        } finally {
            lock.unlock();
        }
    }

    private double tax(double price) {
        double percent = Math.max(0, Math.min(100, config.decimal("settings.tax-percent", 0)));
        return price * (percent / 100.0);
    }

    private boolean hasSpace(Player player, ItemStack item) {
        if (item == null) return true;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null) return true;
            if (content.isSimilar(item) && content.getAmount() + item.getAmount() <= content.getMaxStackSize()) return true;
        }
        return false;
    }

    private boolean sameItemIgnoringAmount(ItemStack first, ItemStack second) {
        if (first == null || second == null) return first == second;
        ItemStack a = first.clone();
        ItemStack b = second.clone();
        a.setAmount(1);
        b.setAmount(1);
        return a.isSimilar(b);
    }

    private void deliver(final UUID owner, final ItemStack item) {
        if (item == null) return;
        final Player online = Bukkit.getPlayer(owner);
        if (online == null) {
            addClaim(owner, item);
            return;
        }
        Scheduler.runFor(plugin, online, new Runnable() {
            @Override
            public void run() {
                Map<Integer, ItemStack> left = online.getInventory().addItem(item);
                for (ItemStack stack : left.values()) addClaim(owner, stack);
            }
        });
    }

    private void depositOrQueue(UUID owner, String currency, double amount, String description) {
        if (amount <= 0) return;
        OfflinePlayer player = Bukkit.getOfflinePlayer(owner);
        if (currencies.deposit(currency, player, amount)) return;
        Payout payout = new Payout(owner, currency, amount, description, System.currentTimeMillis());
        payouts.computeIfAbsent(owner, key -> new ArrayList<Payout>()).add(payout);
        storage.savePayout(payout);
    }

    /** Sends a script commission to the configured treasury, or withholds it. */
    private void depositCommission(String currency, double amount, String description) {
        UUID treasury = scripts.treasury();
        if (treasury == null) return;
        depositOrQueue(treasury, currency, amount, description);
    }

    public void addClaim(UUID owner, ItemStack item) {
        if (item == null) return;
        List<ItemStack> list = claims.computeIfAbsent(owner, key -> new ArrayList<ItemStack>());
        list.add(item);
        storage.saveClaims(owner, list);
    }

    public void setClaims(UUID owner, List<ItemStack> items) {
        if (items == null) return;
        claims.put(owner, new ArrayList<>(items));
    }

    public void setPayouts(UUID owner, List<Payout> items) {
        if (items == null) return;
        payouts.put(owner, new ArrayList<>(items));
    }

    public List<AuctionItem> query(AuctionFilter filter, AuctionSort sort) {
        List<AuctionItem> result = new ArrayList<>();
        for (AuctionItem auction : auctions.values()) {
            if (!auction.isActive()) continue;
            if (!visible(auction)) continue;
            if (filter == null || filter.matches(auction)) result.add(auction);
        }
        Collections.sort(result, comparator(sort));
        return result;
    }

    private boolean visible(AuctionItem auction) {
        if (!config.multiServer() || config.showAllServers()) return true;
        return auction.server().isEmpty() || auction.server().equalsIgnoreCase(config.serverId());
    }

    private Comparator<AuctionItem> comparator(AuctionSort sort) {
        switch (sort) {
            case OLDEST:
                return new Comparator<AuctionItem>() {
                    @Override public int compare(AuctionItem first, AuctionItem second) { return Long.compare(first.createdAt(), second.createdAt()); }
                };
            case CHEAPEST:
                return new Comparator<AuctionItem>() {
                    @Override public int compare(AuctionItem first, AuctionItem second) { return Double.compare(first.currentPrice(), second.currentPrice()); }
                };
            case EXPENSIVE:
                return new Comparator<AuctionItem>() {
                    @Override public int compare(AuctionItem first, AuctionItem second) { return Double.compare(second.currentPrice(), first.currentPrice()); }
                };
            case ENDING:
                return new Comparator<AuctionItem>() {
                    @Override public int compare(AuctionItem first, AuctionItem second) { return Long.compare(first.expiresAt(), second.expiresAt()); }
                };
            case VIEWS:
                return new Comparator<AuctionItem>() {
                    @Override public int compare(AuctionItem first, AuctionItem second) { return Integer.compare(second.views(), first.views()); }
                };
            case CURRENCY:
                return new Comparator<AuctionItem>() {
                    @Override
                    public int compare(AuctionItem first, AuctionItem second) {
                        int currency = first.currency().compareToIgnoreCase(second.currency());
                        return currency != 0 ? currency : Double.compare(first.currentPrice(), second.currentPrice());
                    }
                };
            default:
                return new Comparator<AuctionItem>() {
                    @Override public int compare(AuctionItem first, AuctionItem second) { return Long.compare(second.createdAt(), first.createdAt()); }
                };
        }
    }

    public int count(UUID seller) {
        int count = 0;
        for (AuctionItem auction : auctions.values()) if (auction.seller().equals(seller)) count++;
        return count;
    }

    public int countVisible(UUID seller) {
        int count = 0;
        for (AuctionItem auction : auctions.values()) if (auction.seller().equals(seller) && visible(auction)) count++;
        return count;
    }

    public int activeCount() {
        return auctions.size();
    }

    public int purge(int days) {
        long threshold = System.currentTimeMillis() - Math.max(0, days) * 86400000L;
        int removed = 0;
        lock.lock();
        try {
            for (AuctionItem auction : new ArrayList<>(auctions.values())) {
                if (auction.expiresAt() > threshold) continue;
                if (config.multiServer() && !auction.server().isEmpty()
                        && !auction.server().equalsIgnoreCase(config.serverId())) continue;
                if (!storage.reserveAuction(auction.id())) {
                    continue;
                }
                auctions.remove(auction.id());
                if (auction.type() == AuctionType.BUY_REQUEST) {
                    depositOrQueue(auction.seller(), auction.currency(), auction.price(),
                            "Завершение запроса " + auction.displayName());
                    record(auction.seller(), auction.sellerName(), auction, TransactionType.REFUND,
                            auction.price(), auction.currency(), auction.displayName(), "");
                } else if (auction.type() == AuctionType.BID && auction.topBidder() != null) {
                    deliver(auction.topBidder(), auction.item());
                    double tax = tax(auction.topBid());
                    depositOrQueue(auction.seller(), auction.currency(), auction.topBid() - tax,
                            "Завершение аукциона " + auction.displayName());
                    record(auction.seller(), auction.sellerName(), auction, TransactionType.SALE,
                            auction.topBid() - tax, auction.currency(), auction.displayName(), auction.topBidderName());
                } else {
                    deliver(auction.seller(), auction.item());
                }
                removed++;
            }
        } finally {
            lock.unlock();
        }
        return removed;
    }

    public AuctionItem get(UUID id) {
        AuctionItem auction = auctions.get(id);
        return auction == null ? null : auction.copy();
    }

    public Collection<AuctionItem> active() {
        List<AuctionItem> result = new ArrayList<>();
        for (AuctionItem auction : auctions.values()) if (visible(auction)) result.add(auction.copy());
        return result;
    }

    public void incrementViews(UUID id) {
        AuctionItem auction = auctions.get(id);
        if (auction != null) auction.incrementViews();
    }

    public List<Transaction> history(UUID player, int limit) {
        List<Transaction> result = new ArrayList<>(history.getOrDefault(player, Collections.<Transaction>emptyList()));
        Collections.sort(result, new Comparator<Transaction>() {
            @Override public int compare(Transaction first, Transaction second) { return Long.compare(second.createdAt(), first.createdAt()); }
        });
        if (result.size() > limit) return new ArrayList<>(result.subList(0, Math.max(0, limit)));
        return result;
    }

    public int historyCount(UUID player) {
        return history.getOrDefault(player, Collections.<Transaction>emptyList()).size();
    }

    public List<ItemStack> claims(UUID player) {
        return new ArrayList<>(claims.getOrDefault(player, Collections.<ItemStack>emptyList()));
    }

    public List<Payout> payouts(UUID player) {
        return new ArrayList<>(payouts.getOrDefault(player, Collections.<Payout>emptyList()));
    }

    public int pendingCount(UUID player) {
        return claims(player).size() + payouts(player).size();
    }

    public void importAuction(AuctionItem auction) {
        auctions.put(auction.id(), auction);
        storage.saveAuction(auction);
    }

    public String format(String currency, double amount) {
        return currencies.format(currency, amount);
    }

    public String currencyName(String currency) {
        return currencies.displayName(currency);
    }

    public Map<String, String> placeholders(AuctionItem auction) {
        return values(
                "item", auction.displayName(),
                "price", format(auction.currency(), auction.price()),
                "current", format(auction.currency(), auction.currentPrice()),
                "seller", auction.sellerName(),
                "amount", String.valueOf(auction.item() == null ? 0 : auction.item().getAmount()),
                "currency", currencyName(auction.currency()),
                "server", auction.server() == null || auction.server().isEmpty() ? config.serverId() : auction.server()
        );
    }

    private void record(UUID player, String playerName, AuctionItem auction, TransactionType type,
                        double amount, String currency, String itemName, String counterparty) {
        Transaction transaction = new Transaction(UUID.randomUUID(), player, playerName,
                auction == null ? null : auction.id(), type, amount, currency, itemName, counterparty,
                System.currentTimeMillis(), auction == null ? null : auction.item());
        List<Transaction> list = history.computeIfAbsent(player, key -> new ArrayList<Transaction>());
        list.add(transaction);
        if (list.size() > config.integer("settings.history-limit", 500)) list.remove(0);
        storage.addTransaction(transaction);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!claims(player.getUniqueId()).isEmpty() || !payouts(player.getUniqueId()).isEmpty()) {
            messages.send(player, "pending-claims");
        }
    }

    public static Map<String, String> values(String... values) {
        Map<String, String> result = new HashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) result.put(values[index], values[index + 1]);
        return result;
    }
}
