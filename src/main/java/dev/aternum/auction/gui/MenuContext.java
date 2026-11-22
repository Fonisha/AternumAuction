package dev.aternum.auction.gui;

import dev.aternum.auction.model.AuctionItem;
import dev.aternum.auction.model.AuctionSort;
import dev.aternum.auction.model.AuctionType;
import dev.aternum.auction.service.AuctionFilter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable state passed between menus.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class MenuContext {
    public MenuType type;
    public final Player viewer;
    public int page;
    public AuctionFilter filter = AuctionFilter.all();
    public AuctionSort sort = AuctionSort.NEWEST;
    public Gui parent;
    public AuctionItem auction;
    public boolean bid;
    public boolean remove;
    public boolean request;
    public List<ItemStack> items = new ArrayList<>();
    public ItemStack listingItem;
    public double price;
    public int amount;
    public AuctionType listingType = AuctionType.BIN;
    public int hours;

    public MenuContext(Player viewer) {
        this.viewer = viewer;
    }

    public MenuContext(MenuType type, Player viewer) {
        this.type = type;
        this.viewer = viewer;
    }

    public static MenuContext main(Player player, AuctionFilter filter, AuctionSort sort, int page) {
        MenuContext context = new MenuContext(MenuType.MAIN, player);
        context.filter = filter == null ? AuctionFilter.all() : filter;
        context.sort = sort == null ? AuctionSort.NEWEST : sort;
        context.page = Math.max(0, page);
        if (filter != null && filter.type() == AuctionFilter.Type.MINE) context.type = MenuType.MY;
        return context;
    }

    public static MenuContext confirm(Player player, AuctionItem auction, Gui parent, boolean bid, boolean remove) {
        MenuContext context = new MenuContext(MenuType.CONFIRM, player);
        context.auction = auction;
        context.parent = parent;
        context.bid = bid;
        context.remove = remove;
        return context;
    }

    public static MenuContext preview(Player player, ItemStack item, List<ItemStack> contents, Gui parent) {
        MenuContext context = new MenuContext(MenuType.SHULKER, player);
        context.items = contents == null ? new ArrayList<ItemStack>() : contents;
        context.listingItem = item;
        context.parent = parent;
        return context;
    }

    public static MenuContext listing(Player player, ItemStack item, double price, int amount,
                                      AuctionType type, int hours) {
        MenuContext context = new MenuContext(MenuType.CURRENCY, player);
        context.listingItem = item == null ? null : item.clone();
        context.price = price;
        context.amount = amount;
        context.listingType = type;
        context.hours = hours;
        return context;
    }

    public MenuContext copy() {
        MenuContext copy = new MenuContext(type, viewer);
        copy.page = page;
        copy.filter = filter;
        copy.sort = sort;
        copy.parent = parent;
        copy.auction = auction;
        copy.bid = bid;
        copy.remove = remove;
        copy.request = request;
        copy.items = items;
        copy.listingItem = listingItem;
        copy.price = price;
        copy.amount = amount;
        copy.listingType = listingType;
        copy.hours = hours;
        return copy;
    }
}
