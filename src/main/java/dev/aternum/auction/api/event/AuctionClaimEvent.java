package dev.aternum.auction.api.event;

import dev.aternum.auction.model.Payout;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;

public class AuctionClaimEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final List<ItemStack> items;
    private final List<Payout> payouts;

    public AuctionClaimEvent(Player player, List<ItemStack> items, List<Payout> payouts) {
        this.player = player;
        this.items = items == null ? Collections.<ItemStack>emptyList() : items;
        this.payouts = payouts == null ? Collections.<Payout>emptyList() : payouts;
    }

    public Player getPlayer() { return player; }
    public List<ItemStack> getItems() { return items; }
    public List<Payout> getPayouts() { return payouts; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
