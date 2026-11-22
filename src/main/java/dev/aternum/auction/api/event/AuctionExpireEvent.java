package dev.aternum.auction.api.event;

import dev.aternum.auction.model.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class AuctionExpireEvent extends AuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player winner;

    public AuctionExpireEvent(AuctionItem auction, Player winner) {
        super(auction);
        this.winner = winner;
    }

    public Player getWinner() { return winner; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
