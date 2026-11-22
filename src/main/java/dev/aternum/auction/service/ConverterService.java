package dev.aternum.auction.service;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.model.AuctionItem;
import dev.aternum.auction.model.AuctionType;
import dev.aternum.auction.util.ItemSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Best-effort import from other auction plugins.
 * SQLite sources are read through metadata, so small schema differences are tolerated.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class ConverterService {
    private final AternumAuctionPlugin plugin;
    private final AuctionManager auctions;

    public ConverterService(AternumAuctionPlugin plugin, AuctionManager auctions) {
        this.plugin = plugin;
        this.auctions = auctions;
    }

    public List<String> available() {
        List<String> result = new ArrayList<>();
        result.add("aternium");
        if (database("zAuctionHouse/auctionhouse.db") != null) result.add("zauctionhouse");
        if (database("AuctionHouse/auctions.db") != null) result.add("auctionhouse");
        if (database("AxAuctions/database.db") != null) result.add("axauctions");
        if (database("DeluxeAuctions/auctions.db") != null) result.add("deluxeauctions");
        return result;
    }

    public int convert(String name) {
        String id = name.toLowerCase(Locale.ENGLISH);
        if ("aternium".equals(id) || "legacy".equals(id) || "yaml".equals(id)) {
            return importYaml(new File(plugin.getDataFolder(), "auctions.yml"))
                    + importYaml(new File(plugin.getDataFolder(), "data.yml"));
        }
        File file = databaseFor(id);
        if (file == null) return -1;
        return importSqlite(file);
    }

    private File databaseFor(String id) {
        if ("zauctionhouse".equals(id)) return database("zAuctionHouse/auctionhouse.db");
        if ("auctionhouse".equals(id)) return database("AuctionHouse/auctions.db");
        if ("axauctions".equals(id)) return database("AxAuctions/database.db");
        if ("deluxeauctions".equals(id) || "deluxe".equals(id)) return database("DeluxeAuctions/auctions.db");
        return null;
    }

    private File database(String path) {
        File plugins = plugin.getDataFolder().getParentFile();
        File file = new File(plugins, path);
        return file.exists() ? file : null;
    }

    private int importYaml(File file) {
        if (file == null || !file.exists()) return 0;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        int imported = 0;
        ConfigurationSection section = data.getConfigurationSection("auctions");
        if (section == null) return 0;
        long now = System.currentTimeMillis();
        for (String key : section.getKeys(false)) {
            try {
                String path = "auctions." + key;
                ItemStack item = data.getItemStack(path + ".item");
                if (item == null) continue;
                auctions.importAuction(new AuctionItem(
                        UUID.fromString(key),
                        UUID.fromString(data.getString(path + ".seller")),
                        data.getString(path + ".seller-name", "?"),
                        item,
                        AuctionType.from(data.getString(path + ".type", "BIN")),
                        data.getDouble(path + ".price"),
                        data.getDouble(path + ".bid-step", 1),
                        data.getString(path + ".currency", "vault"),
                        data.getString(path + ".category", "misc"),
                        data.getString(path + ".server", plugin.getConfigManager().serverId()),
                        data.getLong(path + ".created", now),
                        data.getLong(path + ".expires", now + 3600000L),
                        data.getDouble(path + ".top-bid"),
                        uuid(data.getString(path + ".top-bidder")),
                        data.getString(path + ".top-bidder-name", ""),
                        data.getInt(path + ".views")
                ));
                imported++;
            } catch (Exception ignored) {
            }
        }
        return imported;
    }

    private int importSqlite(File file) {
        int imported = 0;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Throwable ignored) {
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath())) {
            DatabaseMetaData meta = connection.getMetaData();
            List<String> tables = new ArrayList<>();
            try (ResultSet rows = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rows.next()) tables.add(rows.getString("TABLE_NAME"));
            }
            for (String table : tables) {
                List<String> columns = new ArrayList<>();
                try (ResultSet rows = meta.getColumns(null, null, table, "%")) {
                    while (rows.next()) columns.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ENGLISH));
                }
                String itemColumn = first(columns, "item", "stack", "data");
                String priceColumn = first(columns, "price", "cost", "amount");
                String sellerColumn = first(columns, "seller", "owner", "player", "uuid");
                if (itemColumn == null || priceColumn == null || sellerColumn == null) continue;
                String currencyColumn = first(columns, "currency", "economy");
                String createdColumn = first(columns, "created", "create", "date", "time");
                String expiresColumn = first(columns, "expire", "expiry", "end");
                String idColumn = first(columns, "id", "uuid");

                try (Statement statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery("SELECT * FROM " + table)) {
                    long now = System.currentTimeMillis();
                    while (rows.next()) {
                        ItemStack item = decode(rows.getObject(itemColumn));
                        if (item == null) continue;
                        UUID seller = uuid(rows.getString(sellerColumn));
                        if (seller == null) continue;
                        double price = rows.getDouble(priceColumn);
                        if (price <= 0) continue;
                        long created = createdColumn == null ? now : rows.getLong(createdColumn);
                        if (created < 100000000000L) created = now;
                        long expires = expiresColumn == null ? created + 172800000L : rows.getLong(expiresColumn);
                        if (expires < 100000000000L) expires = now + 172800000L;
                        auctions.importAuction(new AuctionItem(
                                UUID.randomUUID(),
                                seller,
                                shortName(rows, sellerColumn),
                                item,
                                AuctionType.BIN,
                                price,
                                1,
                                currencyColumn == null ? plugin.getConfigManager().currencyId() : rows.getString(currencyColumn),
                                plugin.getCategoryManager().resolve(item),
                                plugin.getConfigManager().serverId(),
                                created,
                                expires,
                                0,
                                null,
                                "",
                                0
                        ));
                        imported++;
                    }
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Converter could not read " + file.getName() + ": " + exception.getMessage());
            return -1;
        }
        return imported;
    }

    private String shortName(ResultSet rows, String sellerColumn) {
        try {
            String name = rows.getString("seller_name");
            if (name != null && !name.isEmpty()) return name;
        } catch (Exception ignored) {
        }
        return "imported";
    }

    private static String first(List<String> columns, String... candidates) {
        for (String candidate : candidates) {
            for (String column : columns) {
                if (column.equals(candidate) || column.contains(candidate)) return column;
            }
        }
        return null;
    }

    private static ItemStack decode(Object value) {
        if (value == null) return null;
        if (value instanceof byte[]) return decodeBytes((byte[]) value);
        if (value instanceof String) {
            ItemStack decoded = ItemSerializer.decode((String) value);
            if (decoded != null) return decoded;
            try {
                return decodeBytes(java.util.Base64.getDecoder().decode((String) value));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static ItemStack decodeBytes(byte[] bytes) {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object object = input.readObject();
            return object instanceof ItemStack ? (ItemStack) object : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static UUID uuid(String value) {
        try {
            return value == null || value.isEmpty() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public List<String> emptyList() {
        return Collections.emptyList();
    }
}
