package dev.aternum.auction.storage;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.model.AuctionItem;
import dev.aternum.auction.model.AuctionType;
import dev.aternum.auction.model.Payout;
import dev.aternum.auction.model.Transaction;
import dev.aternum.auction.model.TransactionType;
import dev.aternum.auction.util.ItemSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * YAML backend. Simple, human readable and dependency free.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class YamlStorage implements Storage {
    private final AternumAuctionPlugin plugin;
    private final File file;

    public YamlStorage(AternumAuctionPlugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
    }

    @Override
    public void init() throws Exception {
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            if (!file.createNewFile()) plugin.getLogger().warning("Could not create " + file.getName());
        }
    }

    @Override
    public List<AuctionItem> loadAuctions() {
        List<AuctionItem> result = new ArrayList<>();
        YamlConfiguration data = read();
        ConfigurationSection section = data.getConfigurationSection("auctions");
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            try {
                String path = "auctions." + key;
                ItemStack item = data.getItemStack(path + ".item");
                if (item == null) continue;
                long now = System.currentTimeMillis();
                result.add(new AuctionItem(
                        UUID.fromString(key),
                        UUID.fromString(data.getString(path + ".seller")),
                        data.getString(path + ".seller-name", "?"),
                        item,
                        AuctionType.from(data.getString(path + ".type", "BIN")),
                        data.getDouble(path + ".price"),
                        data.getDouble(path + ".bid-step", 1),
                        data.getString(path + ".currency", "vault"),
                        data.getString(path + ".category", "misc"),
                        data.getString(path + ".server", ""),
                        data.getLong(path + ".created", now),
                        data.getLong(path + ".expires", now + 3600000L),
                        data.getDouble(path + ".top-bid"),
                        uuid(data.getString(path + ".top-bidder")),
                        data.getString(path + ".top-bidder-name", ""),
                        data.getInt(path + ".views")
                ));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    @Override
    public Map<UUID, List<ItemStack>> loadClaims() {
        Map<UUID, List<ItemStack>> result = new HashMap<>();
        YamlConfiguration data = read();
        ConfigurationSection section = data.getConfigurationSection("claims");
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            try {
                List<ItemStack> items = new ArrayList<>();
                for (Object object : data.getList("claims." + key, Collections.emptyList())) {
                    if (object instanceof ItemStack) items.add((ItemStack) object);
                }
                result.put(UUID.fromString(key), items);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    @Override
    public List<ItemStack> loadClaims(UUID owner) {
        List<ItemStack> result = new ArrayList<>();
        YamlConfiguration data = read();
        for (Object object : data.getList("claims." + owner, Collections.emptyList())) {
            if (object instanceof ItemStack) result.add((ItemStack) object);
        }
        return result;
    }

    @Override
    public List<Payout> loadPayouts() {
        List<Payout> result = new ArrayList<>();
        for (Map<?, ?> map : read().getMapList("payouts")) {
            Payout payout = payout(map);
            if (payout != null) result.add(payout);
        }
        return result;
    }

    @Override
    public List<Payout> loadPayouts(UUID owner) {
        List<Payout> result = new ArrayList<>();
        for (Payout payout : loadPayouts()) if (payout.player().equals(owner)) result.add(payout);
        return result;
    }

    @Override
    public List<Transaction> loadTransactions() {
        List<Transaction> result = new ArrayList<>();
        for (Map<?, ?> map : read().getMapList("transactions")) {
            try {
                result.add(new Transaction(
                        UUID.fromString(String.valueOf(map.get("id"))),
                        UUID.fromString(String.valueOf(map.get("player"))),
                        String.valueOf(map.get("player-name")),
                        uuid(String.valueOf(map.get("auction-id"))),
                        TransactionType.valueOf(String.valueOf(map.get("type"))),
                        number(map.get("amount")),
                        String.valueOf(map.get("currency")),
                        String.valueOf(map.get("item-name")),
                        String.valueOf(map.get("counterparty")),
                        ((Number) map.get("created")).longValue(),
                        ItemSerializer.decode(String.valueOf(map.get("item")))
                ));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    @Override
    public Map<UUID, Integer> loadLimits() {
        Map<UUID, Integer> result = new HashMap<>();
        ConfigurationSection section = read().getConfigurationSection("limits");
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            try {
                int value = section.getInt(key);
                if (value > 0) result.put(UUID.fromString(key), value);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    @Override
    public int limitBonus(UUID player) {
        return read().getInt("limits." + player, 0);
    }

    @Override
    public synchronized void setLimitBonus(UUID player, int bonus) {
        YamlConfiguration data = read();
        data.set("limits." + player, bonus <= 0 ? null : bonus);
        write(data);
    }

    @Override
    public synchronized void saveAuction(AuctionItem auction) {
        YamlConfiguration data = read();
        String path = "auctions." + auction.id();
        data.set(path + ".seller", auction.seller().toString());
        data.set(path + ".seller-name", auction.sellerName());
        data.set(path + ".item", auction.item());
        data.set(path + ".type", auction.type().name());
        data.set(path + ".price", auction.price());
        data.set(path + ".bid-step", auction.bidStep());
        data.set(path + ".currency", auction.currency());
        data.set(path + ".category", auction.category());
        data.set(path + ".server", auction.server());
        data.set(path + ".created", auction.createdAt());
        data.set(path + ".expires", auction.expiresAt());
        data.set(path + ".top-bid", auction.topBid());
        data.set(path + ".top-bidder", auction.topBidder() == null ? "" : auction.topBidder().toString());
        data.set(path + ".top-bidder-name", auction.topBidderName());
        data.set(path + ".views", auction.views());
        write(data);
    }

    @Override
    public synchronized void deleteAuction(UUID id) {
        YamlConfiguration data = read();
        data.set("auctions." + id, null);
        write(data);
    }

    @Override
    public synchronized boolean reserveAuction(UUID id) {
        YamlConfiguration data = read();
        if (!data.isConfigurationSection("auctions." + id)) return false;
        data.set("auctions." + id, null);
        write(data);
        return true;
    }

    @Override
    public synchronized void saveClaims(UUID owner, List<ItemStack> items) {
        YamlConfiguration data = read();
        if (items == null || items.isEmpty()) data.set("claims." + owner, null);
        else data.set("claims." + owner, items);
        write(data);
    }

    @Override
    public synchronized void savePayout(Payout payout) {
        YamlConfiguration data = read();
        List<Map<?, ?>> payouts = new ArrayList<>(data.getMapList("payouts"));
        Map<String, Object> map = new HashMap<>();
        map.put("player", payout.player().toString());
        map.put("currency", payout.currency());
        map.put("amount", payout.amount());
        map.put("description", payout.description());
        map.put("created", payout.createdAt());
        payouts.add(map);
        data.set("payouts", payouts);
        write(data);
    }

    @Override
    public synchronized void deletePayout(Payout payout) {
        YamlConfiguration data = read();
        List<Map<?, ?>> kept = new ArrayList<>();
        for (Map<?, ?> map : data.getMapList("payouts")) {
            boolean match = String.valueOf(map.get("player")).equalsIgnoreCase(payout.player().toString())
                    && number(map.get("created")) == payout.createdAt();
            if (!match) kept.add(map);
        }
        data.set("payouts", new ArrayList<Object>(kept));
        write(data);
    }

    @Override
    public synchronized void addTransaction(Transaction transaction) {
        YamlConfiguration data = read();
        List<Map<?, ?>> transactions = new ArrayList<>(data.getMapList("transactions"));
        Map<String, Object> map = new HashMap<>();
        map.put("id", transaction.id().toString());
        map.put("player", transaction.player().toString());
        map.put("player-name", transaction.playerName());
        map.put("auction-id", transaction.auctionId() == null ? "" : transaction.auctionId().toString());
        map.put("type", transaction.type().name());
        map.put("amount", transaction.amount());
        map.put("currency", transaction.currency());
        map.put("item-name", transaction.itemName());
        map.put("item", ItemSerializer.encode(transaction.item()));
        map.put("counterparty", transaction.counterparty());
        map.put("created", transaction.createdAt());
        transactions.add(map);
        if (transactions.size() > 5000) {
            transactions = new ArrayList<Map<?, ?>>(transactions.subList(transactions.size() - 5000, transactions.size()));
        }
        data.set("transactions", transactions);
        write(data);
    }

    @Override
    public void close() {
    }

    private Payout payout(Map<?, ?> map) {
        try {
            Object created = map.get("created");
            return new Payout(
                    UUID.fromString(String.valueOf(map.get("player"))),
                    String.valueOf(map.get("currency")),
                    number(map.get("amount")),
                    String.valueOf(map.get("description")),
                    created instanceof Number ? ((Number) created).longValue() : 0L
            );
        } catch (Exception exception) {
            return null;
        }
    }

    private YamlConfiguration read() {
        return YamlConfiguration.loadConfiguration(file);
    }

    private void write(YamlConfiguration data) {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not write " + file.getName() + ": " + exception.getMessage());
        }
    }

    private static UUID uuid(String value) {
        try {
            return value == null || value.isEmpty() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static double number(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }
}
