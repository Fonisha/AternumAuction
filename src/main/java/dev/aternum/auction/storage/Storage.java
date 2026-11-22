package dev.aternum.auction.storage;

import dev.aternum.auction.model.AuctionItem;
import dev.aternum.auction.model.Payout;
import dev.aternum.auction.model.Transaction;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence contract. Implementations must be safe to call from the storage thread.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public interface Storage {
    void init() throws Exception;

    List<AuctionItem> loadAuctions();
    Map<UUID, List<ItemStack>> loadClaims();
    List<ItemStack> loadClaims(UUID owner);
    List<Payout> loadPayouts();
    List<Payout> loadPayouts(UUID owner);
    List<Transaction> loadTransactions();

    Map<UUID, Integer> loadLimits();
    int limitBonus(UUID player);
    void setLimitBonus(UUID player, int bonus);

    void saveAuction(AuctionItem auction);
    void deleteAuction(UUID id);

    /**
     * Atomically removes a lot from the database.
     * Returns false when another server already claimed it.
     */
    boolean reserveAuction(UUID id);
    void saveClaims(UUID owner, List<ItemStack> items);
    void savePayout(Payout payout);
    void deletePayout(Payout payout);
    void addTransaction(Transaction transaction);

    void close();
}
