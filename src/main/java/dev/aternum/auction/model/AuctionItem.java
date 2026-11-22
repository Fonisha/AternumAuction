package dev.aternum.auction.model;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/**
 * Immutable auction lot. Bid state is updated under the auction manager lock.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class AuctionItem {
    private final UUID id;
    private final UUID seller;
    private final String sellerName;
    private final ItemStack item;
    private final AuctionType type;
    private final double price;
    private final double bidStep;
    private final String currency;
    private final String category;
    private final String server;
    private final long createdAt;
    private final long expiresAt;
    private volatile double topBid;
    private volatile UUID topBidder;
    private volatile String topBidderName;
    private volatile int views;

    public AuctionItem(UUID id, UUID seller, String sellerName, ItemStack item, AuctionType type,
                       double price, double bidStep, String currency, String category,
                       String server, long createdAt, long expiresAt, double topBid,
                       UUID topBidder, String topBidderName, int views) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.item = item == null ? null : item.clone();
        this.type = type == null ? AuctionType.BIN : type;
        this.price = price;
        this.bidStep = bidStep;
        this.currency = currency == null ? "vault" : currency;
        this.category = category == null ? "misc" : category;
        this.server = server == null ? "" : server;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.topBid = topBid;
        this.topBidder = topBidder;
        this.topBidderName = topBidderName;
        this.views = views;
    }

    public UUID id() { return id; }
    public UUID seller() { return seller; }
    public String sellerName() { return sellerName; }
    public ItemStack item() { return item == null ? null : item.clone(); }
    public ItemStack rawItem() { return item; }
    public AuctionType type() { return type; }
    public double price() { return price; }
    public double bidStep() { return bidStep; }
    public String currency() { return currency; }
    public String category() { return category; }
    public String server() { return server; }
    public long createdAt() { return createdAt; }
    public long expiresAt() { return expiresAt; }
    public double topBid() { return topBid; }
    public UUID topBidder() { return topBidder; }
    public String topBidderName() { return topBidderName; }
    public int views() { return views; }

    public double currentPrice() {
        return type == AuctionType.BID && topBid > 0 ? topBid + bidStep : price;
    }

    public boolean hasBid() {
        return topBidder != null;
    }

    public boolean isExpired() {
        return expiresAt <= System.currentTimeMillis();
    }

    public boolean isActive() {
        return !isExpired();
    }

    public void incrementViews() {
        views++;
    }

    public void placeBid(UUID bidder, String bidderName, double amount) {
        this.topBid = amount;
        this.topBidder = bidder;
        this.topBidderName = bidderName;
    }

    public String displayName() {
        if (item == null) return "?";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return ChatColor.stripColor(meta.getDisplayName());
        return item.getType().name().toLowerCase().replace('_', ' ');
    }

    public AuctionItem copy() {
        return new AuctionItem(id, seller, sellerName, item, type, price, bidStep, currency,
                category, server, createdAt, expiresAt, topBid, topBidder, topBidderName, views);
    }
}
