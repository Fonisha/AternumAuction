package dev.aternum.auction.api.event;

import dev.aternum.auction.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class AuctionBidEvent extends AuctionEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player bidder;
    private final double amount;
    private boolean cancelled;

    public AuctionBidEvent(Player bidder, AuctionItem auction, double amount) {
        super(auction);
        this.bidder = bidder;
        this.amount = amount;
    }

    public Player getBidder() { return bidder; }
    public double getAmount() { return amount; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
