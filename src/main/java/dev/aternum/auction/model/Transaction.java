package dev.aternum.auction.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class Transaction {
    private final UUID id;
    private final UUID player;
    private final String playerName;
    private final UUID auctionId;
    private final TransactionType type;
    private final double amount;
    private final String currency;
    private final String itemName;
    private final ItemStack item;
    private final String counterparty;
    private final long createdAt;

    public Transaction(UUID id, UUID player, String playerName, UUID auctionId, TransactionType type,
                       double amount, String currency, String itemName, String counterparty, long createdAt) {
        this(id, player, playerName, auctionId, type, amount, currency, itemName, counterparty, createdAt, null);
    }

    public Transaction(UUID id, UUID player, String playerName, UUID auctionId, TransactionType type,
                       double amount, String currency, String itemName, String counterparty, long createdAt,
                       ItemStack item) {
        this.id = id;
        this.player = player;
        this.playerName = playerName;
        this.auctionId = auctionId;
        this.type = type;
        this.amount = amount;
        this.currency = currency == null ? "vault" : currency;
        this.itemName = itemName;
        this.item = item == null ? null : item.clone();
        this.counterparty = counterparty;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID player() { return player; }
    public String playerName() { return playerName; }
    public UUID auctionId() { return auctionId; }
    public TransactionType type() { return type; }
    public double amount() { return amount; }
    public String currency() { return currency; }
    public String itemName() { return itemName; }
    public ItemStack item() { return item == null ? null : item.clone(); }
    public String counterparty() { return counterparty; }
    public long createdAt() { return createdAt; }
}
