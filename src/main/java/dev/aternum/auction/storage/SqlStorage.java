package dev.aternum.auction.storage;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.model.AuctionItem;
import dev.aternum.auction.model.AuctionType;
import dev.aternum.auction.model.Payout;
import dev.aternum.auction.model.Transaction;
import dev.aternum.auction.model.TransactionType;
import dev.aternum.auction.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite and MySQL backend. Used for shared, multi-server setups.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class SqlStorage implements Storage {
    private final AternumAuctionPlugin plugin;
    private final boolean mysql;
    private final String url;
    private final String username;
    private final String password;
    private final String prefix;
    private final String items;
    private final String claims;
    private final String payouts;
    private final String transactions;
    private final String limits;

    public SqlStorage(AternumAuctionPlugin plugin, boolean mysql) {
        this.plugin = plugin;
        this.mysql = mysql;
        String rawPrefix = plugin.getConfig().getString("storage.mysql.table-prefix", "aternum_");
        if (rawPrefix == null || !rawPrefix.matches("[A-Za-z0-9_]{0,20}")) rawPrefix = "aternum_";
        this.prefix = rawPrefix;
        this.items = prefix + "auction_items";
        this.claims = prefix + "auction_claims";
        this.payouts = prefix + "auction_payouts";
        this.transactions = prefix + "auction_transactions";
        this.limits = prefix + "auction_limits";

        if (mysql) {
            String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
            String database = plugin.getConfig().getString("storage.mysql.database", "aternum_auction");
            this.username = plugin.getConfig().getString("storage.mysql.username", "root");
            this.password = plugin.getConfig().getString("storage.mysql.password", "");
            boolean ssl = plugin.getConfig().getBoolean("storage.mysql.use-ssl", false);
            this.url = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + ssl + "&characterEncoding=UTF-8&serverTimezone=UTC&useUnicode=true";
        } else {
            this.username = "";
            this.password = "";
            File file = new File(plugin.getDataFolder(), plugin.getConfig().getString("storage.sqlite-file", "data.db"));
            this.url = "jdbc:sqlite:" + file.getAbsolutePath();
        }
    }

    @Override
    public void init() throws Exception {
        if (mysql) {
            tryLoad("com.mysql.cj.jdbc.Driver", "dev.aternum.auction.libs.mysql.cj.jdbc.Driver");
        } else {
            tryLoad("org.sqlite.JDBC", "dev.aternum.auction.libs.sqlite.JDBC");
        }
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + items + " ("
                    + "id VARCHAR(36) PRIMARY KEY,"
                    + "seller VARCHAR(36) NOT NULL,"
                    + "seller_name VARCHAR(32),"
                    + "item TEXT NOT NULL,"
                    + "type VARCHAR(16),"
                    + "price DOUBLE,"
                    + "bid_step DOUBLE,"
                    + "currency VARCHAR(32),"
                    + "category VARCHAR(32),"
                    + "server VARCHAR(48),"
                    + "created BIGINT,"
                    + "expires BIGINT,"
                    + "top_bid DOUBLE,"
                    + "top_bidder VARCHAR(36),"
                    + "top_bidder_name VARCHAR(32),"
                    + "views INT"
                    + ")");
            statement.execute("CREATE TABLE IF NOT EXISTS " + claims + " ("
                    + "id " + (mysql ? "BIGINT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT") + ","
                    + "owner VARCHAR(36) NOT NULL,"
                    + "item TEXT NOT NULL,"
                    + "created BIGINT"
                    + ")");
            statement.execute("CREATE TABLE IF NOT EXISTS " + payouts + " ("
                    + "id " + (mysql ? "BIGINT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT") + ","
                    + "player VARCHAR(36) NOT NULL,"
                    + "currency VARCHAR(32),"
                    + "amount DOUBLE,"
                    + "description VARCHAR(160),"
                    + "created BIGINT"
                    + ")");
            statement.execute("CREATE TABLE IF NOT EXISTS " + transactions + " ("
                    + "id VARCHAR(36) PRIMARY KEY,"
                    + "player VARCHAR(36) NOT NULL,"
                    + "player_name VARCHAR(32),"
                    + "auction_id VARCHAR(36),"
                    + "type VARCHAR(24),"
                    + "amount DOUBLE,"
                    + "currency VARCHAR(32),"
                    + "item_name VARCHAR(160),"
                    + "item TEXT,"
                    + "counterparty VARCHAR(32),"
                    + "created BIGINT"
                    + ")");
            try {
                statement.execute("ALTER TABLE " + transactions + " ADD COLUMN item TEXT");
            } catch (SQLException ignored) {
                // Column already exists on upgraded installations.
            }
            try {
                if (mysql) statement.execute("ALTER TABLE " + items + " MODIFY COLUMN type VARCHAR(16)");
            } catch (SQLException ignored) {
                // SQLite and already-compatible schemas do not need this migration.
            }
            statement.execute("CREATE TABLE IF NOT EXISTS " + limits + " ("
                    + "player VARCHAR(36) PRIMARY KEY,"
                    + "bonus INT"
                    + ")");
            try {
                statement.execute("CREATE INDEX IF NOT EXISTS " + prefix + "idx_claims_owner ON " + claims + " (owner)");
            } catch (SQLException ignored) {
            }
            try {
                statement.execute("CREATE INDEX IF NOT EXISTS " + prefix + "idx_payouts_player ON " + payouts + " (player)");
            } catch (SQLException ignored) {
            }
        }
    }

    private void tryLoad(String... names) {
        for (String name : names) {
            try {
                Class.forName(name);
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    @Override
    public List<AuctionItem> loadAuctions() {
        List<AuctionItem> result = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + items)) {
            ResultSet rows = statement.executeQuery();
            while (rows.next()) {
                try {
                    ItemStack item = ItemSerializer.decode(rows.getString("item"));
                    if (item == null) continue;
                    result.add(new AuctionItem(
                            UUID.fromString(rows.getString("id")),
                            UUID.fromString(rows.getString("seller")),
                            rows.getString("seller_name"),
                            item,
                            AuctionType.from(rows.getString("type")),
                            rows.getDouble("price"),
                            rows.getDouble("bid_step"),
                            rows.getString("currency"),
                            rows.getString("category"),
                            rows.getString("server"),
                            rows.getLong("created"),
                            rows.getLong("expires"),
                            rows.getDouble("top_bid"),
                            uuid(rows.getString("top_bidder")),
                            rows.getString("top_bidder_name"),
                            rows.getInt("views")
                    ));
                } catch (Exception ignored) {
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load auctions: " + exception.getMessage());
        }
        return result;
    }

    @Override
    public Map<UUID, List<ItemStack>> loadClaims() {
        Map<UUID, List<ItemStack>> result = new HashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT owner, item FROM " + claims + " ORDER BY id ASC")) {
            ResultSet rows = statement.executeQuery();
            while (rows.next()) {
                try {
                    UUID owner = UUID.fromString(rows.getString("owner"));
                    ItemStack item = ItemSerializer.decode(rows.getString("item"));
                    if (item != null) result.computeIfAbsent(owner, key -> new ArrayList<ItemStack>()).add(item);
                } catch (Exception ignored) {
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load claims: " + exception.getMessage());
        }
        return result;
    }

    @Override
    public List<ItemStack> loadClaims(UUID owner) {
        List<ItemStack> result = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT item FROM " + claims + " WHERE owner=? ORDER BY id ASC")) {
            statement.setString(1, owner.toString());
            ResultSet rows = statement.executeQuery();
            while (rows.next()) {
                ItemStack item = ItemSerializer.decode(rows.getString("item"));
                if (item != null) result.add(item);
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load claims: " + exception.getMessage());
        }
        return result;
    }

    @Override
    public List<Payout> loadPayouts() {
        List<Payout> result = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + payouts + " ORDER BY id ASC")) {
            ResultSet rows = statement.executeQuery();
            while (rows.next()) {
                try {
                    result.add(new Payout(UUID.fromString(rows.getString("player")), rows.getString("currency"),
                            rows.getDouble("amount"), rows.getString("description"), rows.getLong("created")));
                } catch (Exception ignored) {
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load payouts: " + exception.getMessage());
        }
        return result;
    }

    @Override
    public List<Payout> loadPayouts(UUID owner) {
        List<Payout> result = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + payouts + " WHERE player=? ORDER BY id ASC")) {
            statement.setString(1, owner.toString());
            ResultSet rows = statement.executeQuery();
            while (rows.next()) {
                try {
                    result.add(new Payout(owner, rows.getString("currency"), rows.getDouble("amount"),
                            rows.getString("description"), rows.getLong("created")));
                } catch (Exception ignored) {
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load payouts: " + exception.getMessage());
        }
        return result;
    }

    @Override
    public List<Transaction> loadTransactions() {
        List<Transaction> result = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + transactions + " ORDER BY created DESC LIMIT 5000")) {
            ResultSet rows = statement.executeQuery();
            while (rows.next()) {
                try {
                    result.add(new Transaction(
                            UUID.fromString(rows.getString("id")),
                            UUID.fromString(rows.getString("player")),
                            rows.getString("player_name"),
                            uuid(rows.getString("auction_id")),
                            TransactionType.valueOf(rows.getString("type")),
                            rows.getDouble("amount"),
                            rows.getString("currency"),
                            rows.getString("item_name"),
                            rows.getString("counterparty"),
                            rows.getLong("created"),
                            ItemSerializer.decode(rows.getString("item"))
                    ));
                } catch (Exception ignored) {
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load transactions: " + exception.getMessage());
        }
        return result;
    }

    @Override
    public Map<UUID, Integer> loadLimits() {
        Map<UUID, Integer> result = new HashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT player, bonus FROM " + limits)) {
            ResultSet rows = statement.executeQuery();
            while (rows.next()) {
                try {
                    int bonus = rows.getInt("bonus");
                    if (bonus > 0) result.put(UUID.fromString(rows.getString("player")), bonus);
                } catch (Exception ignored) {
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load limits: " + exception.getMessage());
        }
        return result;
    }

    @Override
    public int limitBonus(UUID player) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT bonus FROM " + limits + " WHERE player=?")) {
            statement.setString(1, player.toString());
            ResultSet rows = statement.executeQuery();
            return rows.next() ? rows.getInt("bonus") : 0;
        } catch (SQLException exception) {
            return 0;
        }
    }

    @Override
    public void setLimitBonus(UUID player, int bonus) {
        try (Connection connection = connection()) {
            if (bonus <= 0) {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + limits + " WHERE player=?")) {
                    delete.setString(1, player.toString());
                    delete.executeUpdate();
                }
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "REPLACE INTO " + limits + " (player, bonus) VALUES (?,?)")) {
                statement.setString(1, player.toString());
                statement.setInt(2, bonus);
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save limit: " + exception.getMessage());
        }
    }

    @Override
    public void saveAuction(AuctionItem auction) {
        String sql = "REPLACE INTO " + items + " (id, seller, seller_name, item, type, price, bid_step, currency, category, server, created, expires, top_bid, top_bidder, top_bidder_name, views)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, auction.id().toString());
            statement.setString(2, auction.seller().toString());
            statement.setString(3, auction.sellerName());
            statement.setString(4, ItemSerializer.encode(auction.item()));
            statement.setString(5, auction.type().name());
            statement.setDouble(6, auction.price());
            statement.setDouble(7, auction.bidStep());
            statement.setString(8, auction.currency());
            statement.setString(9, auction.category());
            statement.setString(10, auction.server());
            statement.setLong(11, auction.createdAt());
            statement.setLong(12, auction.expiresAt());
            statement.setDouble(13, auction.topBid());
            statement.setString(14, auction.topBidder() == null ? "" : auction.topBidder().toString());
            statement.setString(15, auction.topBidderName());
            statement.setInt(16, auction.views());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save auction: " + exception.getMessage());
        }
    }

    @Override
    public void deleteAuction(UUID id) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM " + items + " WHERE id=?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not delete auction: " + exception.getMessage());
        }
    }

    @Override
    public boolean reserveAuction(UUID id) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM " + items + " WHERE id=?")) {
            statement.setString(1, id.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not reserve auction: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public void saveClaims(UUID owner, List<ItemStack> items) {
        try (Connection connection = connection()) {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + claims + " WHERE owner=?")) {
                delete.setString(1, owner.toString());
                delete.executeUpdate();
            }
            if (items == null || items.isEmpty()) return;
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + claims + " (owner, item, created) VALUES (?,?,?)")) {
                for (ItemStack item : items) {
                    insert.setString(1, owner.toString());
                    insert.setString(2, ItemSerializer.encode(item));
                    insert.setLong(3, System.currentTimeMillis());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save claims: " + exception.getMessage());
        }
    }

    @Override
    public void savePayout(Payout payout) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + payouts + " (player, currency, amount, description, created) VALUES (?,?,?,?,?)")) {
            statement.setString(1, payout.player().toString());
            statement.setString(2, payout.currency());
            statement.setDouble(3, payout.amount());
            statement.setString(4, payout.description());
            statement.setLong(5, payout.createdAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save payout: " + exception.getMessage());
        }
    }

    @Override
    public void deletePayout(Payout payout) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + payouts + " WHERE player=? AND currency=? AND created=?")) {
            statement.setString(1, payout.player().toString());
            statement.setString(2, payout.currency());
            statement.setLong(3, payout.createdAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not delete payout: " + exception.getMessage());
        }
    }

    @Override
    public void addTransaction(Transaction transaction) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO " + transactions + " (id, player, player_name, auction_id, type, amount, currency, item_name, item, counterparty, created) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, transaction.id().toString());
            statement.setString(2, transaction.player().toString());
            statement.setString(3, transaction.playerName());
            statement.setString(4, transaction.auctionId() == null ? "" : transaction.auctionId().toString());
            statement.setString(5, transaction.type().name());
            statement.setDouble(6, transaction.amount());
            statement.setString(7, transaction.currency());
            statement.setString(8, transaction.itemName());
            statement.setString(9, ItemSerializer.encode(transaction.item()));
            statement.setString(10, transaction.counterparty());
            statement.setLong(11, transaction.createdAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save transaction: " + exception.getMessage());
        }
    }

    @Override
    public void close() {
    }

    private static UUID uuid(String value) {
        try {
            return value == null || value.isEmpty() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
