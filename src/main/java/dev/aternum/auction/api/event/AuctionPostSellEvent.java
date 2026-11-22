package dev.aternum.auction.api.event;

import dev.aternum.auction.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class AuctionPostSellEvent extends AuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player seller;

    public AuctionPostSellEvent(Player seller, AuctionItem auction) {
        super(auction);
        this.seller = seller;
    }

    public Player getSeller() { return seller; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
