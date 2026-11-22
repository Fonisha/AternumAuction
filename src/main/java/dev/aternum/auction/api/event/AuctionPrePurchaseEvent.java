package dev.aternum.auction.api.event;

import dev.aternum.auction.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class AuctionPrePurchaseEvent extends AuctionEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player buyer;
    private double price;
    private boolean cancelled;

    public AuctionPrePurchaseEvent(Player buyer, AuctionItem auction, double price) {
        super(auction);
        this.buyer = buyer;
        this.price = price;
    }

    public Player getBuyer() { return buyer; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
