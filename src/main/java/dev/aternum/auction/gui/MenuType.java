package dev.aternum.auction.gui;

/**
 * Every menu maps to a layout file inside plugins/AternumAuction/guis.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public enum MenuType {
    MAIN("main-gui"),
    MY("my-items-gui"),
    HISTORY("history-gui"),
    EXPIRED("expired-gui"),
    CATEGORIES("categories-gui"),
    CONFIRM("confirm-gui"),
    CURRENCY("currency-gui"),
    SHULKER("shulker-gui"),
    ADMIN("admin-gui");

    private final String layoutId;

    MenuType(String layoutId) {
        this.layoutId = layoutId;
    }

    public String layoutId() {
        return layoutId;
    }
}
