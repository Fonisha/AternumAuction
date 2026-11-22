package dev.aternum.auction.service;

import dev.aternum.auction.model.AuctionItem;

import java.util.Collections;
import java.util.Map;

/**
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class AuctionResult {
    private final boolean success;
    private final String messageKey;
    private final Map<String, String> placeholders;
    private final AuctionItem auction;

    private AuctionResult(boolean success, String messageKey, Map<String, String> placeholders, AuctionItem auction) {
        this.success = success;
        this.messageKey = messageKey;
        this.placeholders = placeholders == null ? Collections.<String, String>emptyMap() : placeholders;
        this.auction = auction;
    }

    public static AuctionResult ok(AuctionItem auction) {
        return new AuctionResult(true, null, Collections.<String, String>emptyMap(), auction);
    }

    public static AuctionResult ok(String key, Map<String, String> placeholders, AuctionItem auction) {
        return new AuctionResult(true, key, placeholders, auction);
    }

    public static AuctionResult fail(String key) {
        return new AuctionResult(false, key, Collections.<String, String>emptyMap(), null);
    }

    public static AuctionResult fail(String key, Map<String, String> placeholders) {
        return new AuctionResult(false, key, placeholders, null);
    }

    public boolean success() { return success; }
    public String messageKey() { return messageKey; }
    public Map<String, String> placeholders() { return placeholders; }
    public AuctionItem auction() { return auction; }
}
