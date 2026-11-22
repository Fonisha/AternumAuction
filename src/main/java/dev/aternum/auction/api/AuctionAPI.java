package dev.aternum.auction.api;

import dev.aternum.auction.model.AuctionItem;
import dev.aternum.auction.model.Transaction;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AuctionAPI {
    Collection<AuctionItem> getActiveAuctions();
    AuctionItem getAuction(UUID id);
    List<Transaction> getHistory(UUID player, int limit);
    boolean removeAuction(Player actor, UUID id, boolean admin);
    void reload();
}
