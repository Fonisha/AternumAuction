package dev.aternum.auction.api.event;

import dev.aternum.auction.model.AuctionItem;
import org.bukkit.event.Event;

public abstract class AuctionEvent extends Event {
    private final AuctionItem auction;

    protected AuctionEvent(AuctionItem auction) {
        this.auction = auction;
    }

    public AuctionItem getAuction() {
        return auction;
    }
}
