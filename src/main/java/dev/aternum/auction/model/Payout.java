package dev.aternum.auction.model;

import java.util.UUID;

/**
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class Payout {
    private final UUID player;
    private final String currency;
    private final double amount;
    private final String description;
    private final long createdAt;

    public Payout(UUID player, String currency, double amount, String description, long createdAt) {
        this.player = player;
        this.currency = currency;
        this.amount = amount;
        this.description = description;
        this.createdAt = createdAt;
    }

    public UUID player() { return player; }
    public String currency() { return currency; }
    public double amount() { return amount; }
    public String description() { return description; }
    public long createdAt() { return createdAt; }
}
