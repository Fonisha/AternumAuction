package dev.aternum.auction.api.event;

import dev.aternum.auction.model.AuctionType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired before a lot is created. Cancelling prevents the listing.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public class AuctionPreSellEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player seller;
    private final ItemStack item;
    private final int amount;
    private final AuctionType type;
    private final String currency;
    private double price;
    private boolean cancelled;

    public AuctionPreSellEvent(Player seller, ItemStack item, int amount, double price, AuctionType type, String currency) {
        this.seller = seller;
        this.item = item;
        this.amount = amount;
        this.price = price;
        this.type = type;
        this.currency = currency;
    }

    public Player getSeller() { return seller; }
    public ItemStack getItem() { return item; }
    public int getAmount() { return amount; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public AuctionType getType() { return type; }
    public String getCurrency() { return currency; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
