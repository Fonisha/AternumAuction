package dev.aternum.auction.model;

public enum AuctionType {
    BIN,
    BID,
    BUY_REQUEST;

    public static AuctionType from(String value) {
        if ("bid".equalsIgnoreCase(value) || "auction".equalsIgnoreCase(value)) return BID;
        if ("buy_request".equalsIgnoreCase(value) || "request".equalsIgnoreCase(value)
                || "buy".equalsIgnoreCase(value)) return BUY_REQUEST;
        return BIN;
    }
}
