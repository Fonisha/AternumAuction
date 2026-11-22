package dev.aternum.auction.api.event;

import dev.aternum.auction.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class AuctionRemoveEvent extends AuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player actor;
    private final boolean admin;

    public AuctionRemoveEvent(Player actor, AuctionItem auction, boolean admin) {
        super(auction);
        this.actor = actor;
        this.admin = admin;
    }

    public Player getActor() { return actor; }
    public boolean isAdmin() { return admin; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
