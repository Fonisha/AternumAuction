package dev.aternum.auction.gui;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.config.CategoryManager;
import dev.aternum.auction.config.Layout;
import dev.aternum.auction.config.LayoutItem;
import dev.aternum.auction.economy.Currency;
import dev.aternum.auction.model.AuctionItem;
import dev.aternum.auction.model.AuctionSort;
import dev.aternum.auction.model.AuctionType;
import dev.aternum.auction.model.Payout;
import dev.aternum.auction.model.Transaction;
import dev.aternum.auction.service.AuctionFilter;
import dev.aternum.auction.service.AuctionResult;
import dev.aternum.auction.util.ContainerUtil;
import dev.aternum.auction.util.Scheduler;
import dev.aternum.auction.util.SoundUtil;
import dev.aternum.auction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal layout driven menu. Every screen (main, history, categories, confirm,
 * currency picker, container preview and admin) is rendered from a YAML file,
 * so the whole interface is configured by the server owner without recompiling.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public class LayoutGui extends Gui {
    protected final MenuContext context;
    protected final Layout layout;

    private List<AuctionItem> auctions = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();
    private List<ItemStack> items = new ArrayList<>();
    private List<Currency> currencies = new ArrayList<>();
    private List<CategoryManager.Category> categories = new ArrayList<>();
    private int pages = 1;
    private int safePage;
    private Map<String, String> cache;
    private Scheduler.Task updater;

    public LayoutGui(AternumAuctionPlugin plugin, MenuContext context) {
        this(plugin, context, layoutOf(plugin, context));
    }

    private LayoutGui(AternumAuctionPlugin plugin, MenuContext context, Layout layout) {
        super(plugin, layout.rows() * 9, title(plugin, context, layout));
        this.layout = layout;
        this.context = context;
        loadContent();
        render();
        scheduleUpdate();
    }

    @Override
    public void open(Player player) {
        Gui previous = plugin.getGuiManager().get(player);
        if (!(previous instanceof LayoutGui) || ((LayoutGui) previous).context.type != context.type) {
            SoundUtil.play(plugin, player, "open");
        }
        super.open(player);
    }

    private static Layout layoutOf(AternumAuctionPlugin plugin, MenuContext context) {
        Layout layout = plugin.getLayoutManager().get(context.type.layoutId());
        if (layout == null) layout = plugin.getLayoutManager().get("main-gui");
        return layout;
    }

    private static String title(AternumAuctionPlugin plugin, MenuContext context, Layout layout) {
        int pages = pages(plugin, context, layout);
        int page = Math.max(0, Math.min(context.page, pages - 1)) + 1;
        Map<String, String> placeholders = placeholders(plugin, context, pages, page);
        if (layout == null) return plugin.getMessages().resolve("lang:menu.main.title", placeholders);
        return plugin.getMessages().resolve(layout.title(), placeholders);
    }

    private static int pages(AternumAuctionPlugin plugin, MenuContext context, Layout layout) {
        int capacity = capacity(layout);
        int size;
        switch (context.type) {
            case MAIN:
            case MY:
                size = plugin.getAuctionManager().query(context.filter, context.sort).size();
                break;
            case HISTORY:
                size = plugin.getAuctionManager().history(context.viewer.getUniqueId(), 1000).size();
                break;
            case EXPIRED:
                size = plugin.getAuctionManager().claims(context.viewer.getUniqueId()).size();
                break;
            case SHULKER:
                size = context.items.size();
                break;
            case CURRENCY:
                size = plugin.getCurrencyRegistry().enabledFor(context.viewer).size();
                break;
            case CATEGORIES:
                size = plugin.getCategoryManager().all().size();
                break;
            default:
                size = 0;
        }
        return Math.max(1, (size + capacity - 1) / capacity);
    }

    private static int capacity(Layout layout) {
        if (layout == null) return 45;
        return Math.max(9, Math.min(layout.rows() * 9, layout.pageSize()));
    }

    private static Map<String, String> placeholders(AternumAuctionPlugin plugin, MenuContext context, int pages, int page) {
        Player viewer = context.viewer;
        int used = plugin.getAuctionManager().count(viewer.getUniqueId());
        int limit = plugin.getConfigManager().limit(viewer, plugin.getStorageManager().limitBonus(viewer.getUniqueId()));
        Map<String, String> map = new HashMap<>();
        map.put("page", String.valueOf(page));
        map.put("pages", String.valueOf(pages));
        map.put("sort", sortName(plugin, context.sort));
        map.put("filter", filterName(plugin, context.filter));
        map.put("filter-type", context.filter == null || context.filter.auctionType() == null
                ? "ALL" : context.filter.auctionType().name());
        map.put("menu-type", context.type == null ? "MAIN" : context.type.name());
        map.put("category", context.filter == null || context.filter.category() == null
                ? plugin.getMessages().raw("filter.all") : plugin.getCategoryManager().displayName(context.filter.category()));
        map.put("category-id", context.filter == null || context.filter.category() == null
                ? "all" : context.filter.category());
        map.put("total", String.valueOf(plugin.getAuctionManager().activeCount()));
        map.put("own", String.valueOf(used));
        map.put("used", String.valueOf(used));
        map.put("limit", limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit));
        map.put("history", String.valueOf(plugin.getAuctionManager().historyCount(viewer.getUniqueId())));
        map.put("pending", String.valueOf(plugin.getAuctionManager().pendingCount(viewer.getUniqueId())));
        map.put("server", plugin.getConfigManager().serverId());
        map.put("storage", plugin.getConfigManager().storageType());
        map.put("multi", plugin.getConfigManager().multiServer() ? "on" : "off");
        map.put("currency", plugin.getCurrencyRegistry().displayName(plugin.getPlayerSettings().currency(viewer.getUniqueId(), plugin.getCurrencyRegistry().defaultId())));
        map.put("tax", plugin.getConfig().getString("settings.tax-percent", "0"));
        map.put("version", plugin.getDescription().getVersion());
        map.put("name", context.auction == null ? "" : context.auction.displayName());
        if (context.auction != null) {
            map.putAll(plugin.getAuctionManager().placeholders(context.auction));
        }
        return map;
    }

    private static String sortName(AternumAuctionPlugin plugin, AuctionSort sort) {
        String id = sort == null ? "newest" : sort.id();
        String value = plugin.getMessages().raw("sort." + id);
        return value.equals("sort." + id) ? id : value;
    }

    private static String filterName(AternumAuctionPlugin plugin, AuctionFilter filter) {
        if (filter == null) return plugin.getMessages().raw("filter.all");
        if (filter.auctionType() != null) {
            String mode = filter.auctionType() == AuctionType.BUY_REQUEST
                    ? "buy-request" : filter.auctionType().name().toLowerCase();
            String display = plugin.getMessages().raw("filter." + mode);
            if (filter.category() != null) {
                return plugin.getCategoryManager().displayName(filter.category()) + " • " + display;
            }
            return display;
        }
        switch (filter.type()) {
            case MINE:
                return plugin.getMessages().raw("filter.mine");
            case CATEGORY:
                return plugin.getCategoryManager().displayName(filter.value());
            case SEARCH:
                return plugin.getMessages().raw("filter.search").replace("%query%",
                        filter.value() == null ? "" : filter.value()).trim();
            case SELLER:
                return plugin.getMessages().raw("filter.seller");
            case BIN:
                return plugin.getMessages().raw("filter.bin");
            case BID:
                return plugin.getMessages().raw("filter.bid");
            case BUY_REQUEST:
                return plugin.getMessages().raw("filter.buy-request");
            default:
                return plugin.getMessages().raw("filter.all");
        }
    }

    private void loadContent() {
        int capacity = capacity(layout);
        switch (context.type) {
            case MAIN:
            case MY:
                auctions = plugin.getAuctionManager().query(context.filter, context.sort);
                break;
            case HISTORY:
                transactions = plugin.getAuctionManager().history(context.viewer.getUniqueId(), 1000);
                break;
            case EXPIRED:
                items = plugin.getAuctionManager().claims(context.viewer.getUniqueId());
                break;
            case SHULKER:
                items = new ArrayList<>(context.items);
                break;
            case CURRENCY:
                currencies = plugin.getCurrencyRegistry().enabledFor(context.viewer);
                break;
            case CATEGORIES:
                categories = new ArrayList<>(plugin.getCategoryManager().all());
                break;
            default:
                break;
        }
        int size = 0;
        switch (context.type) {
            case MAIN:
            case MY: size = auctions.size(); break;
            case HISTORY: size = transactions.size(); break;
            case EXPIRED:
            case SHULKER: size = items.size(); break;
            case CURRENCY: size = currencies.size(); break;
            case CATEGORIES: size = categories.size(); break;
            default: size = 0;
        }
        pages = Math.max(1, (size + capacity - 1) / capacity);
        safePage = Math.max(0, Math.min(context.page, pages - 1));
        context.page = safePage;
    }

    private Map<String, String> placeholders() {
        if (cache == null) {
            cache = placeholders(plugin, context, pages, safePage + 1);
            int size = contentSize();
            cache.put("results", String.valueOf(size));
            cache.put("empty", size == 0 ? "yes" : "no");
        }
        return cache;
    }

    private int contentSize() {
        switch (context.type) {
            case MAIN:
            case MY: return auctions.size();
            case HISTORY: return transactions.size();
            case EXPIRED:
            case SHULKER: return items.size();
            case CURRENCY: return currencies.size();
            case CATEGORIES: return categories.size();
            default: return 0;
        }
    }

    private void render() {
        Material fillerMaterial = Material.matchMaterial(layout.fillerMaterial());
        if (fillerMaterial == null) fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
        String fillerName = plugin.getMessages().resolve(layout.fillerName(), placeholders());
        ItemStack filler = ItemBuilder.of(fillerMaterial).name(fillerName).build();
        for (int slot : layout.fillerSlots()) {
            if (slot >= 0 && slot < getInventory().getSize()) set(slot, filler);
        }

        renderContent();

        for (LayoutItem item : layout.items()) {
            if (context.type == MenuType.CONFIRM && "preview".equals(item.key())) continue;
            if (!conditions(item)) continue;
            int slot = resolveSlot(item);
            if (slot < 0 || slot >= getInventory().getSize()) continue;
            set(slot, build(item), createHandler(item));
        }
    }

    private int resolveSlot(LayoutItem item) {
        return item.slot();
    }

    private ClickHandler createHandler(final LayoutItem item) {
        return new ClickHandler() {
            @Override
            public void click(InventoryClickEvent event) {
                runActions(item, event);
            }
        };
    }

    private void renderContent() {
        int capacity = capacity(layout);
        int start = safePage * capacity;
        int offset = contentOffset(contentSize());
        switch (context.type) {
            case MAIN:
            case MY:
                renderAuctions(start, capacity, offset);
                break;
            case HISTORY:
                renderHistory(start, capacity, offset);
                break;
            case EXPIRED:
                renderItems(start, capacity, offset, true);
                break;
            case SHULKER:
                renderItems(start, capacity, offset, false);
                break;
            case CURRENCY:
                renderCurrencies(start, capacity, offset);
                break;
            case CATEGORIES:
                renderCategories(start, capacity, offset);
                break;
            case CONFIRM:
                renderConfirm();
                break;
            default:
                break;
        }
    }

    /**
     * First slot of the content block. Short lists are centered inside the
     * first content row, which keeps small menus from hugging the left edge.
     */
    private int contentOffset(int size) {
        int base = layout.contentStart();
        return base;
    }

    private void renderAuctions(int start, int capacity, int offset) {
        for (int index = start; index < Math.min(start + capacity, auctions.size()); index++) {
            final AuctionItem auction = auctions.get(index);
            ItemStack display = auctionDisplay(auction);
            set(offset + index - start, display, new ClickHandler() {
                @Override
                public void click(InventoryClickEvent event) {
                    Player player = (Player) event.getWhoClicked();
                    plugin.getAuctionManager().incrementViews(auction.id());
                    if (event.isRightClick() && plugin.getConfig().getBoolean("settings.item.preview-containers", true)
                            && ContainerUtil.isContainer(auction.item())) {
                        new LayoutGui(plugin, MenuContext.preview(player, auction.item(), ContainerUtil.contents(auction.item()), LayoutGui.this)).open(player);
                        return;
                    }
                    SoundUtil.play(plugin, player, "click");
                    if (auction.seller().equals(player.getUniqueId())) {
                        new LayoutGui(plugin, MenuContext.confirm(player, auction, LayoutGui.this, false, true)).open(player);
                    } else if (auction.type() == AuctionType.BUY_REQUEST) {
                        MenuContext request = MenuContext.confirm(player, auction, LayoutGui.this, false, false);
                        request.request = true;
                        new LayoutGui(plugin, request).open(player);
                    } else {
                        new LayoutGui(plugin, MenuContext.confirm(player, auction, LayoutGui.this,
                                auction.type() == AuctionType.BID, false)).open(player);
                    }
                }
            });
        }
    }

    private void renderHistory(int start, int capacity, int offset) {
        for (int index = start; index < Math.min(start + capacity, transactions.size()); index++) {
            Transaction transaction = transactions.get(index);
            String name = plugin.getMessages().resolve("lang:menu.history.entry-name",
                    AuctionManagerPlaceholders.of("type", MenuUtil.capitalize(transaction.type().name())));
            List<String> lore = new ArrayList<>();
            for (String line : plugin.getMessages().rawList("menu.history.entry-lore")) {
                Map<String, String> map = new HashMap<>();
                map.put("item", transaction.itemName() == null ? "—" : transaction.itemName());
                map.put("amount", plugin.getAuctionManager().format(transaction.currency(), transaction.amount()));
                map.put("counterparty", transaction.counterparty() == null || transaction.counterparty().isEmpty() ? "—" : transaction.counterparty());
                map.put("currency", plugin.getCurrencyRegistry().displayName(transaction.currency()));
                map.put("date", MenuUtil.date(transaction.createdAt()));
                lore.add(plugin.getMessages().resolve(line, map));
            }
            ItemStack historyItem = transaction.item();
            if (historyItem == null || historyItem.getType() == Material.AIR) historyItem = new ItemStack(Material.PAPER);
            ItemMeta historyMeta = historyItem.getItemMeta();
            if (historyMeta != null) {
                historyMeta.setDisplayName(Text.color(name));
                historyMeta.setLore(lore);
                historyItem.setItemMeta(historyMeta);
            }
            set(offset + index - start, historyItem);
        }
    }

    private void renderItems(int start, int capacity, int offset, final boolean claim) {
        for (int index = start; index < Math.min(start + capacity, items.size()); index++) {
            final ItemStack item = items.get(index);
            set(offset + index - start, item.clone(), new ClickHandler() {
                @Override
                public void click(InventoryClickEvent event) {
                    if (!claim) return;
                    Player player = (Player) event.getWhoClicked();
                    plugin.getAuctionManager().claim(player);
                    new LayoutGui(plugin, context.copy()).open(player);
                }
            });
        }
    }

    private void renderCurrencies(int start, int capacity, int offset) {
        for (int index = start; index < Math.min(start + capacity, currencies.size()); index++) {
            final Currency currency = currencies.get(index);
            Map<String, String> map = new HashMap<>(placeholders());
            map.put("currency", currency.displayName());
            map.put("balance", currency.available() ? "—" : plugin.getMessages().raw("messages.no-economy").replace("%currency%", currency.id()));
            List<String> lore = new ArrayList<>();
            for (String line : plugin.getMessages().rawList("menu.currency.item-lore")) {
                lore.add(plugin.getMessages().resolve(line, map));
            }
            if (!currency.available()) lore.add(plugin.getMessages().resolve("lang:menu.currency.unavailable", map));
            set(offset + index - start, ItemBuilder.of(currency.icon()).name(currency.displayName()).lore(lore).build(), new ClickHandler() {
                @Override
                public void click(InventoryClickEvent event) {
                    chooseCurrency((Player) event.getWhoClicked(), currency);
                }
            });
        }
    }

    private void renderCategories(int start, int capacity, int offset) {
        for (int index = start; index < Math.min(start + capacity, categories.size()); index++) {
            final CategoryManager.Category category = categories.get(index);
            Map<String, String> map = new HashMap<>(placeholders());
            map.put("id", category.id());
            map.put("count", String.valueOf(plugin.getAuctionManager().query(AuctionFilter.category(category.id()), context.sort).size()));
            List<String> lore = new ArrayList<>();
            for (String line : plugin.getMessages().rawList("menu.categories.item-lore")) {
                lore.add(plugin.getMessages().resolve(line, map));
            }
            set(offset + index - start, ItemBuilder.of(category.icon()).name(category.displayName()).lore(lore).build(), new ClickHandler() {
                @Override
                public void click(InventoryClickEvent event) {
                    Player player = (Player) event.getWhoClicked();
                    plugin.openMenu(player, MenuType.MAIN,
                            MenuContext.main(player, AuctionFilter.category(category.id()), context.sort, 0));
                }
            });
        }
    }

    private void renderConfirm() {
        LayoutItem preview = layout.item("preview");
        if (preview == null || context.auction == null) return;
        set(preview.slot(), auctionDisplay(context.auction));
    }

    private ItemStack auctionDisplay(AuctionItem auction) {
        ItemStack display = auction.item();
        if (display == null) display = new ItemStack(Material.STONE);
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            Map<String, String> map = new HashMap<>(placeholders());
            map.putAll(plugin.getAuctionManager().placeholders(auction));
            map.put("views", String.valueOf(auction.views()));
            map.put("time", MenuUtil.time(auction.expiresAt() - System.currentTimeMillis()));
            map.put("category", Text.strip(plugin.getCategoryManager().displayName(auction.category())));
                    String actionKey = auction.seller().equals(context.viewer.getUniqueId())
                            ? "menu.main.action-remove"
                            : auction.type() == AuctionType.BID ? "menu.main.action-bid" : "menu.main.action-buy";
                    map.put("action", plugin.getMessages().raw(actionKey));
            map.put("price", plugin.getAuctionManager().format(auction.currency(), auction.price()));
            map.put("amount", String.valueOf(auction.item() == null ? 0 : auction.item().getAmount()));
            String priceLine = plugin.getMessages().raw(auction.type() == AuctionType.BID ? "menu.main.start-line" : "menu.main.price-line");
            String bidLine = auction.hasBid() ? plugin.getMessages().raw("menu.main.bid-line") : "";
            List<String> lore = new ArrayList<>();
            for (String line : plugin.getMessages().rawList("menu.main.auction-lore")) {
                String trimmed = line.trim();
                String resolved;
                if ("%price_line%".equals(trimmed)) resolved = plugin.getMessages().resolve(priceLine, map);
                else if ("%preview_line%".equals(trimmed)) {
                    if (!ContainerUtil.isContainer(auction.item())) continue;
                    resolved = plugin.getMessages().resolve(plugin.getMessages().raw("menu.main.preview-line"), map);
                }
                else if ("%bid_line%".equals(trimmed)) {
                    if (bidLine.isEmpty()) continue;
                    Map<String, String> bidMap = new HashMap<>(map);
                    bidMap.put("price", plugin.getAuctionManager().format(auction.currency(), auction.topBid()));
                    bidMap.put("bidder", auction.topBidderName() == null || auction.topBidderName().isEmpty() ? "—" : auction.topBidderName());
                    resolved = plugin.getMessages().resolve(bidLine, bidMap);
                } else resolved = plugin.getMessages().resolve(line, map);
                lore.add(Text.color(resolved));
            }
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private boolean conditions(LayoutItem item) {
        for (String condition : item.conditions()) {
            if (!matches(condition)) return false;
        }
        return true;
    }

    private boolean matches(String condition) {
        String text = condition.trim();
        String operator;
        int position;
        if ((position = text.indexOf(">=")) >= 0) operator = ">=";
        else if ((position = text.indexOf("<=")) >= 0) operator = "<=";
        else if ((position = text.indexOf("!=")) >= 0) operator = "!=";
        else if ((position = text.indexOf("==")) >= 0) operator = "==";
        else if ((position = text.indexOf('>')) >= 0) operator = ">";
        else if ((position = text.indexOf('<')) >= 0) operator = "<";
        else return true;

        String left = plugin.getMessages().resolve(text.substring(0, position).trim(), placeholders());
        String right = plugin.getMessages().resolve(text.substring(position + operator.length()).trim(), placeholders());
        switch (operator) {
            case "==": return left.equalsIgnoreCase(right);
            case "!=": return !left.equalsIgnoreCase(right);
            case ">":
            case ">=":
            case "<":
            case "<=":
                try {
                    double first = Double.parseDouble(left);
                    double second = Double.parseDouble(right);
                    if (">".equals(operator)) return first > second;
                    if (">=".equals(operator)) return first >= second;
                    if ("<".equals(operator)) return first < second;
                    return first <= second;
                } catch (NumberFormatException exception) {
                    return false;
                }
            default:
                return true;
        }
    }

    private ItemStack build(LayoutItem item) {
        Map<String, String> map = new HashMap<>(placeholders());
        String name = plugin.getMessages().resolve(item.name(), map);
        List<String> lore = plugin.getMessages().resolveLines(item.lore(), map);

        if ("sort".equals(item.key())) {
            lore = sortLore();
        }
        if ("auction-type".equals(item.key())) {
            lore = auctionTypeLore();
        }
        if ("category-switch".equals(item.key())) {
            lore = categoryLore();
        }

        // zAuctionHouse-style category control: one slot, with the current
        // category icon rendered dynamically instead of one button per group.
        String materialName = item.material();
        if ("auction-type".equals(item.key()) && context.type == MenuType.MAIN) {
            AuctionType type = context.filter == null || context.filter.auctionType() == null
                    ? AuctionType.BIN : context.filter.auctionType();
            if (type == AuctionType.BID) materialName = "GOLDEN_SWORD";
            else if (type == AuctionType.BUY_REQUEST) materialName = "HOPPER";
            else materialName = "CHEST";
        }
        if ("category-switch".equals(item.key()) && context.type == MenuType.MAIN) {
            String selected = context.filter == null ? null : context.filter.category();
            if (selected != null) {
                CategoryManager.Category category = plugin.getCategoryManager().get(selected);
                if (category != null) materialName = category.icon().name();
            }
        }

        // These controls are always rendered as heads. The selected category/type
        // still changes the label and lore, while the configured texture keeps the
        // bottom navigation visually consistent with the reference menus.
        if ("auction-type".equals(item.key()) || "category-switch".equals(item.key())
                || "sort".equals(item.key()) || "refresh".equals(item.key())
                || "my-items".equals(item.key()) || "expired".equals(item.key())
                || "previous-page".equals(item.key()) || "next-page".equals(item.key())) {
            return ItemBuilder.head(item.texture(), item.head(), name, lore, item.glow());
        }

        if (context.type == MenuType.EXPIRED && "payouts".equals(item.key())) {
            List<Payout> pending = plugin.getAuctionManager().payouts(context.viewer.getUniqueId());
            List<String> payoutLore = new ArrayList<>();
            for (Payout payout : pending) {
                payoutLore.add(plugin.getMessages().resolve("lang:menu.expired.payouts.line",
                        AuctionManagerPlaceholders.of("description", payout.description(),
                                "amount", plugin.getAuctionManager().format(payout.currency(), payout.amount()))));
            }
            if (pending.isEmpty()) payoutLore.add(plugin.getMessages().resolve("lang:menu.expired.empty.name", map));
            return ItemBuilder.head(item.texture(), item.head(),
                    plugin.getMessages().resolve("lang:menu.expired.payouts.name", map), payoutLore, item.glow());
        }

        if (context.type == MenuType.CONFIRM && "confirm".equals(item.key())) {
            String base = context.remove ? "menu.confirm.remove" : context.request ? "menu.confirm.request" : context.bid ? "menu.confirm.bid" : "menu.confirm.buy";
            name = plugin.getMessages().raw(base + ".name");
            lore = plugin.getMessages().resolveList(plugin.getMessages().rawList(base + ".lore"), map);
            if (context.auction != null) {
                map.put("price", plugin.getAuctionManager().format(context.auction.currency(), context.auction.currentPrice()));
                name = plugin.getMessages().resolve(name, map);
                lore = plugin.getMessages().resolveList(lore, map);
            }
        }

            if (!item.head().isEmpty() || !item.texture().isEmpty()) {
                return ItemBuilder.head(item.texture(), item.head(), name, lore, item.glow());
            }
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.STONE;
        return ItemBuilder.of(material)
                .amount(item.amount())
                .name(name)
                .lore(lore)
                .glow(item.glow())
                .modelData(item.customModelData())
                .build();
    }

    private void runActions(LayoutItem item, InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        for (String raw : item.actions()) {
            String action = raw == null ? "" : raw.trim();
            if (action.isEmpty()) continue;
            int end = action.indexOf(']');
            if (end <= 0) continue;
            String tag = action.substring(1, end).trim().toUpperCase();
            String argument = action.substring(end + 1).trim();
            switch (tag) {
                case "MENU": openMenu(player, argument); break;
                case "PAGE": changePage(player, argument); break;
                case "SORT": sortNext(player, event); break;
                case "MODE": changeMode(player, argument, event); break;
                case "CATEGORY": changeCategory(player, argument, event); break;
                case "CLOSE": player.closeInventory(); break;
                case "BACK": back(player); break;
                case "SOUND": SoundUtil.play(plugin, player, argument); break;
                case "MESSAGE": plugin.getMessages().send(player, argument, placeholders()); break;
                case "CLAIM":
                    plugin.getAuctionManager().claim(player);
                    new LayoutGui(plugin, context.copy()).open(player);
                    break;
                case "BUY":
                case "BID": purchase(player); break;
                case "REMOVE": purchase(player); break;
                case "REFRESH": new LayoutGui(plugin, context.copy()).open(player); break;
                case "RELOAD":
                    plugin.reloadPlugin();
                    plugin.getMessages().send(player, "reloaded");
                    new LayoutGui(plugin, context.copy()).open(player);
                    break;
                case "PURGE": purge(player, argument); break;
                case "CMD": Bukkit.dispatchCommand(Bukkit.getConsoleSender(), argument); break;
                case "PLAYER": player.performCommand(argument); break;
                default: break;
            }
        }
    }

    private void openMenu(Player player, String target) {
        String id = target.toLowerCase();
        switch (id) {
            case "main":
            case "auction":
                plugin.openMenu(player, MenuType.MAIN, MenuContext.main(player, AuctionFilter.type(AuctionType.BIN), context.sort, 0));
                break;
            case "bin":
            case "items":
                openMode(player, AuctionType.BIN);
                break;
            case "bid":
            case "bids":
                openMode(player, AuctionType.BID);
                break;
            case "requests":
            case "buy":
                openMode(player, AuctionType.BUY_REQUEST);
                break;
            case "my":
            case "mine":
                plugin.openMenu(player, MenuType.MY, MenuContext.main(player, AuctionFilter.mine(player.getUniqueId()), context.sort, 0));
                break;
            case "history":
                plugin.openMenu(player, MenuType.HISTORY, new MenuContext(MenuType.HISTORY, player));
                break;
            case "expired":
                plugin.openMenu(player, MenuType.EXPIRED, new MenuContext(MenuType.EXPIRED, player));
                break;
            case "categories":
                plugin.openMenu(player, MenuType.MAIN, MenuContext.main(player, AuctionFilter.type(AuctionType.BIN), context.sort, 0));
                break;
            case "admin":
                plugin.openMenu(player, MenuType.ADMIN, new MenuContext(MenuType.ADMIN, player));
                break;
            case "currency":
                plugin.openMenu(player, MenuType.CURRENCY, context.copy());
                break;
            default:
                break;
        }
    }

    private void openMode(Player player, AuctionType type) {
        String category = context.filter == null ? null : context.filter.category();
        AuctionFilter filter;
        if (context.type == MenuType.MY) {
            filter = AuctionFilter.mine(player.getUniqueId()).withType(type);
            if (category != null) filter = filter.withCategory(category);
        } else {
            filter = AuctionFilter.mode(type, category);
        }
        plugin.openMenu(player, context.type == MenuType.MY ? MenuType.MY : MenuType.MAIN,
                MenuContext.main(player, filter, context.sort, 0));
    }

    private void changeMode(Player player, String mode, InventoryClickEvent event) {
        if ("cycle".equalsIgnoreCase(mode)) {
            AuctionType current = context.filter == null || context.filter.auctionType() == null
                    ? AuctionType.BIN : context.filter.auctionType();
            AuctionType[] values = AuctionType.values();
            int index = 0;
            for (int i = 0; i < values.length; i++) if (values[i] == current) index = i;
            int direction = event != null && event.isRightClick() ? -1 : 1;
            int next = (index + direction + values.length) % values.length;
            openMode(player, values[next]);
            return;
        }
        if ("bin".equalsIgnoreCase(mode) || "items".equalsIgnoreCase(mode)) openMode(player, AuctionType.BIN);
        else if ("bid".equalsIgnoreCase(mode) || "bids".equalsIgnoreCase(mode)) openMode(player, AuctionType.BID);
        else if ("request".equalsIgnoreCase(mode) || "requests".equalsIgnoreCase(mode) || "buy".equalsIgnoreCase(mode)) openMode(player, AuctionType.BUY_REQUEST);
        else plugin.openMenu(player, MenuType.MAIN, MenuContext.main(player, AuctionFilter.type(AuctionType.BIN), context.sort, 0));
    }

    private void changeCategory(Player player, String direction, InventoryClickEvent event) {
        List<CategoryManager.Category> all = new ArrayList<>(plugin.getCategoryManager().all());
        if (all.isEmpty()) return;
        if ("cycle".equalsIgnoreCase(direction)) {
            AuctionType mode = context.filter == null ? AuctionType.BIN : context.filter.auctionType();
            String current = context.filter == null ? null : context.filter.category();
            int index = 0;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id().equalsIgnoreCase(current)) {
                    index = i + 1;
                    break;
                }
            }
            boolean backwards = event.isRightClick();
            int next = backwards ? index - 1 : index + 1;
            if (next < 0) next = all.size();
            if (next > all.size()) next = 0;
            AuctionFilter filter = next == 0
                    ? AuctionFilter.type(mode)
                    : AuctionFilter.mode(mode, all.get(next - 1).id());
            plugin.openMenu(player, MenuType.MAIN, MenuContext.main(player, filter, context.sort, 0));
            SoundUtil.play(plugin, player, "click");
            return;
        }
        if (!"next".equalsIgnoreCase(direction) && !"previous".equalsIgnoreCase(direction)) {
            AuctionType mode = context.filter == null ? null : context.filter.auctionType();
            AuctionFilter filter = mode == null || "all".equalsIgnoreCase(direction)
                    ? AuctionFilter.all() : AuctionFilter.mode(mode, direction);
            if (mode != null && "all".equalsIgnoreCase(direction)) filter = AuctionFilter.type(mode);
            plugin.openMenu(player, MenuType.MAIN, MenuContext.main(player, filter, context.sort, 0));
            return;
        }
        String current = context.filter == null ? null : context.filter.category();
        int index = -1;
        for (int i = 0; i < all.size(); i++) if (all.get(i).id().equalsIgnoreCase(current)) index = i;
        int next = "previous".equalsIgnoreCase(direction) ? index - 1 : index + 1;
        if (next < 0) next = all.size() - 1;
        if (next >= all.size()) next = 0;
        AuctionType mode = context.filter == null ? null : context.filter.auctionType();
        AuctionFilter filter = mode == null ? AuctionFilter.category(all.get(next).id()) : AuctionFilter.mode(mode, all.get(next).id());
        plugin.openMenu(player, MenuType.MAIN, MenuContext.main(player, filter, context.sort, 0));
    }

    private void changePage(Player player, String direction) {
        int target = safePage + ("previous".equalsIgnoreCase(direction) ? -1 : 1);
        if (target < 0 || target >= pages) return;
        context.page = target;
        SoundUtil.play(plugin, player, "page");
        new LayoutGui(plugin, context.copy()).open(player);
    }

    private void sortNext(Player player, InventoryClickEvent event) {
        AuctionSort current = context.sort == null ? AuctionSort.NEWEST : context.sort;
        context.sort = event != null && event.isRightClick() ? previousSort(current) : current.next();
        plugin.getPlayerSettings().setSort(player.getUniqueId(), context.sort.id());
        context.page = 0;
        SoundUtil.play(plugin, player, "click");
        new LayoutGui(plugin, context.copy()).open(player);
    }

    private List<String> sortLore() {
        List<String> lore = new ArrayList<>();
        lore.add("&7Доступные типы сортировки:");
        AuctionSort selected = context.sort == null ? AuctionSort.NEWEST : context.sort;
        for (AuctionSort sort : AuctionSort.values()) {
            String label = plugin.getMessages().raw("sort." + sort.id());
            if (label.equals("sort." + sort.id())) label = sort.id();
            lore.add((sort == selected ? "&6➜ &f" : "&7• &f") + label);
        }
        lore.add("");
        lore.add("&eЛКМ &8— &fследующий тип");
        lore.add("&eПКМ &8— &fпредыдущий тип");
        return lore;
    }

    private List<String> auctionTypeLore() {
        List<String> lore = new ArrayList<>();
        AuctionType selected = context.filter == null || context.filter.auctionType() == null
                ? AuctionType.BIN : context.filter.auctionType();
        lore.add("&7Доступные типы аукциона:");
        for (AuctionType type : AuctionType.values()) {
            String id = type == AuctionType.BUY_REQUEST ? "buy-request" : type.name().toLowerCase();
            String label = plugin.getMessages().raw("filter." + id);
            lore.add((type == selected ? "&6➜ &f" : "&7• &f") + label);
        }
        lore.add("");
        lore.add("&eЛКМ &8— &fследующий тип");
        lore.add("&eПКМ &8— &fпредыдущий тип");
        return lore;
    }

    private List<String> categoryLore() {
        List<String> lore = new ArrayList<>();
        String selected = context.filter == null ? null : context.filter.category();
        lore.add("&7Доступные категории:");
        lore.add((selected == null ? "&6➜ &f" : "&7• &f") + "Все категории");
        for (CategoryManager.Category category : plugin.getCategoryManager().all()) {
            boolean active = selected != null && selected.equalsIgnoreCase(category.id());
            lore.add((active ? "&6➜ &f" : "&7• &f") + Text.color(category.displayName()));
        }
        lore.add("");
        lore.add("&eЛКМ &8— &fследующая категория");
        lore.add("&eПКМ &8— &fпредыдущая категория");
        return lore;
    }

    private AuctionSort previousSort(AuctionSort current) {
        AuctionSort previous = AuctionSort.NEWEST;
        for (AuctionSort value : AuctionSort.values()) {
            if (value == current) return previous;
            previous = value;
        }
        return previous;
    }

    private void promptSearch(Player player) {
        plugin.getInputManager().request(player, plugin.getMessages().get("search-hint"), query -> {
            if (query == null) {
                new LayoutGui(plugin, context.copy()).open(player);
                return;
            }
            int minimum = plugin.getConfigManager().integer("settings.search.min-length", 2);
            if (query.trim().length() < minimum) {
                plugin.getMessages().send(player, "search-empty", AuctionManagerPlaceholders.of("query", query));
                new LayoutGui(plugin, context.copy()).open(player);
                return;
            }
            plugin.openMenu(player, MenuType.MAIN,
                    MenuContext.main(player, AuctionFilter.search(query.trim(), plugin.getConfigManager()), context.sort, 0));
        });
    }

    private void back(Player player) {
        if (context.parent != null) context.parent.open(player);
        else plugin.openMenu(player, MenuType.MAIN, MenuContext.main(player, AuctionFilter.type(AuctionType.BIN), context.sort, 0));
    }

    private AuctionFilter returnFilter() {
        if (context.parent instanceof LayoutGui) return ((LayoutGui) context.parent).context.filter;
        return context.filter == null ? AuctionFilter.type(AuctionType.BIN) : context.filter;
    }

    private void purchase(Player player) {
        if (context.auction == null) return;
        AuctionResult result;
        if (context.remove) result = plugin.getAuctionManager().remove(player, context.auction.id(), false);
        else if (context.request) result = plugin.getAuctionManager().fulfilRequest(player, context.auction.id(),
                player.getItemInHand() == null ? 0 : player.getItemInHand().getAmount());
        else if (context.bid) result = plugin.getAuctionManager().bid(player, context.auction.id());
        else result = plugin.getAuctionManager().buy(player, context.auction.id());

        if (result.messageKey() != null) plugin.getMessages().send(player, result.messageKey(), result.placeholders());
        if (result.success()) {
            SoundUtil.play(plugin, player, "success");
            player.closeInventory();
            plugin.openMenu(player, MenuType.MAIN, MenuContext.main(player, returnFilter(), context.sort, 0));
        } else {
            SoundUtil.play(plugin, player, "error");
            new LayoutGui(plugin, context.copy()).open(player);
        }
    }

    private void purge(Player player, String amount) {
        int days;
        try {
            days = Integer.parseInt(amount);
        } catch (NumberFormatException exception) {
            days = 7;
        }
        int removed = plugin.getAuctionManager().purge(days);
        plugin.getMessages().send(player, "purge-done", AuctionManagerPlaceholders.of("amount", String.valueOf(removed)));
        new LayoutGui(plugin, context.copy()).open(player);
    }

    private void chooseCurrency(Player player, Currency currency) {
        if (!currency.available()) {
            plugin.getMessages().send(player, "no-economy", AuctionManagerPlaceholders.of("currency", currency.id()));
            SoundUtil.play(plugin, player, "error");
            return;
        }
        plugin.getPlayerSettings().setCurrency(player.getUniqueId(), currency.id());
        if (context.listingItem == null) {
            plugin.getMessages().send(player, "currency-selected", AuctionManagerPlaceholders.of("currency", currency.displayName()));
            player.closeInventory();
            return;
        }
        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR || !hand.isSimilar(context.listingItem)
                || hand.getAmount() < context.amount) {
            plugin.getMessages().send(player, "cancelled");
            player.closeInventory();
            return;
        }
        AuctionResult result = plugin.getAuctionManager().list(player, hand, context.price,
                context.amount, context.listingType, currency.id(), context.hours);
        if (result.messageKey() != null) plugin.getMessages().send(player, result.messageKey(), result.placeholders());
        if (result.success()) {
            SoundUtil.play(plugin, player, "sell");
            player.closeInventory();
        } else {
            SoundUtil.play(plugin, player, "error");
            new LayoutGui(plugin, context.copy()).open(player);
        }
    }

    private void scheduleUpdate() {
        if (updater != null) updater.cancel();
        int ticks = layout.autoUpdateTicks();
        if (ticks <= 0) return;
        updater = Scheduler.runTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!context.viewer.isOnline()) {
                    if (updater != null) updater.cancel();
                    return;
                }
                Scheduler.runFor(plugin, context.viewer, new Runnable() {
                    @Override
                    public void run() {
                        if (plugin.getGuiManager().get(context.viewer) != LayoutGui.this) {
                            if (updater != null) updater.cancel();
                            return;
                        }
                        new LayoutGui(plugin, context.copy()).open(context.viewer);
                    }
                });
            }
        }, ticks, ticks);
    }

    @Override
    public void onClose(Player player) {
        if (updater != null) {
            updater.cancel();
            updater = null;
        }
    }

    /** Tiny helper to build placeholder maps without importing service internals. */
    static final class AuctionManagerPlaceholders {
        static Map<String, String> of(String... values) {
            Map<String, String> result = new HashMap<>();
            for (int index = 0; index + 1 < values.length; index += 2) result.put(values[index], values[index + 1]);
            return result;
        }
    }
}
