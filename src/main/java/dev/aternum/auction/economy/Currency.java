package dev.aternum.auction.economy;

import org.bukkit.Material;

/**
 * A currency available for trading. Backed by one {@link EconomyProvider}.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class Currency {
    private final String id;
    private final String displayName;
    private final String symbol;
    private final Material icon;
    private final String permission;
    private final boolean allowOffline;
    private final EconomyProvider provider;

    public Currency(String id, String displayName, String symbol, Material icon, String permission,
                    boolean allowOffline, EconomyProvider provider) {
        this.id = id;
        this.displayName = displayName;
        this.symbol = symbol;
        this.icon = icon;
        this.permission = permission;
        this.allowOffline = allowOffline;
        this.provider = provider;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String symbol() { return symbol; }
    public Material icon() { return icon; }
    public String permission() { return permission; }
    public boolean allowOffline() { return allowOffline; }
    public EconomyProvider provider() { return provider; }

    public boolean available() {
        return provider != null && provider.available();
    }
}
