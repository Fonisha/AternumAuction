package dev.aternum.auction.api.event;

import dev.aternum.auction.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class AuctionPostPurchaseEvent extends AuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player buyer;
    private final double price;

    public AuctionPostPurchaseEvent(Player buyer, AuctionItem auction, double price) {
        super(auction);
        this.buyer = buyer;
        this.price = price;
    }

    public Player getBuyer() { return buyer; }
    public double getPrice() { return price; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
