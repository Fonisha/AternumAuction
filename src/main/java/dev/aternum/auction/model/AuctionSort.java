package dev.aternum.auction.model;

/**
 * @author Fonisha (https://github.com/Fonisha)
 */
public enum AuctionSort {
    NEWEST("newest"),
    OLDEST("oldest"),
    CHEAPEST("cheapest"),
    EXPENSIVE("expensive"),
    ENDING("ending"),
    VIEWS("views"),
    CURRENCY("currency");

    private final String id;

    AuctionSort(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static AuctionSort from(String value) {
        if (value != null) for (AuctionSort sort : values()) {
            if (sort.id.equalsIgnoreCase(value) || sort.name().equalsIgnoreCase(value)) return sort;
        }
        return NEWEST;
    }

    public AuctionSort next() {
        AuctionSort[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
