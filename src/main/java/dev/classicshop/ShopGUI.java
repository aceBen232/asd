package dev.classicshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The shop screens:
 * MAIN     - one icon per category, plus the quick buy toggle
 * CATEGORY - the items in a category (paged)
 * ITEM     - pick an amount to buy or sell
 * EDIT     - operators only: change an item's buy/sell price or remove it
 */
public class ShopGUI implements Listener {

    private static final String ADMIN = "classicshop.admin";

    private static final int[] AMOUNTS = {1, 8, 16, 32, 64};
    private static final int[] BUY_SLOTS = {20, 21, 22, 23, 24};
    private static final int[] SELL_SLOTS = {29, 30, 31, 32, 33};
    private static final int BUY_LABEL_SLOT = 19;
    private static final int SELL_LABEL_SLOT = 28;
    private static final int DISPLAY_SLOT = 13;
    private static final int SELL_ALL_SLOT = 40;
    private static final int BACK_SLOT = 49;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int TOGGLE_SLOT_CATEGORY = 51;
    private static final int PAGE_SIZE = 45;
    private static final int MAX_CATEGORIES = 28;

    // Price editor layout: two rows of nine, [-1000 -100 -10 -1 (toggle) +1 +10 +100 +1000]
    private static final int[] EDIT_BUY_ROW = {18, 19, 20, 21, 22, 23, 24, 25, 26};
    private static final int[] EDIT_SELL_ROW = {36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final double[] EDIT_STEPS = {-1000, -100, -10, -1, 0, 1, 10, 100, 1000};
    private static final int EDIT_ITEM_SLOT = 4;
    private static final int EDIT_REMOVE_SLOT = 45;
    private static final double MAX_PRICE = 1_000_000_000_000.0;

    public enum Type { MAIN, CATEGORY, ITEM, EDIT }

    /** Marks an inventory as one of ours and remembers what it is showing. */
    public static final class Holder implements InventoryHolder {
        private final Type type;
        private final String categoryId;
        private final int page;
        private final ShopItem item;
        private final Map<Integer, String> slotToCategory = new HashMap<>();
        private int toggleSlot = -1;
        private Inventory inventory;

        private Holder(Type type, String categoryId, int page, ShopItem item) {
            this.type = type;
            this.categoryId = categoryId;
            this.page = page;
            this.item = item;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ClassicShop plugin;
    private final Set<UUID> viewers = new HashSet<>();

    public ShopGUI(ClassicShop plugin) {
        this.plugin = plugin;
    }

    public static int maxCategories() {
        return MAX_CATEGORIES;
    }

    // ------------------------------------------------------------------
    // Opening screens
    // ------------------------------------------------------------------

    public void openMain(Player player) {
        List<ShopCategory> categories = new ArrayList<>(plugin.shop().getCategories());
        int shown = Math.min(categories.size(), MAX_CATEGORIES);
        int rows = Math.max(3, Math.min(6, (int) Math.ceil(shown / 7.0) + 2));

        Holder holder = new Holder(Type.MAIN, null, 0, null);
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
                ClassicShop.color(plugin.getConfig().getString("menu-title", "&8Shop")));
        holder.inventory = inv;

        int index = 0;
        for (int row = 1; row < rows - 1 && index < shown; row++) {
            for (int col = 1; col <= 7 && index < shown; col++) {
                ShopCategory category = categories.get(index++);
                int slot = row * 9 + col;
                inv.setItem(slot, categoryIcon(category));
                holder.slotToCategory.put(slot, category.getId());
            }
        }

        if (shown == 0) {
            inv.setItem(13, named(Material.BARRIER, "&cThe shop is empty",
                    "&7An admin can create categories with", "&f/shop addcategory"));
        }

        holder.toggleSlot = (rows - 1) * 9 + 4;
        inv.setItem(holder.toggleSlot, quickBuyToggle(player));

        fillEmpty(inv, 0, inv.getSize());
        show(player, inv);
    }

    public void openCategory(Player player, ShopCategory category, int page) {
        List<ShopItem> items = category.getItems();
        int pages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));

        Holder holder = new Holder(Type.CATEGORY, category.getId(), current, null);
        Inventory inv = Bukkit.createInventory(holder, 54, title(category.getDisplayName()));
        holder.inventory = inv;

        int start = current * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < items.size(); i++) {
            inv.setItem(i, listing(items.get(start + i), player));
        }

        if (items.isEmpty()) {
            inv.setItem(22, named(Material.BARRIER, "&cNo items yet",
                    "&7An admin can add items with", "&f/shop addhanditem <price> <sell price>"));
        }
        if (current > 0) {
            inv.setItem(PREV_SLOT, named(Material.ARROW, "&ePrevious page", "&7Page " + current + "/" + pages));
        }
        if (current < pages - 1) {
            inv.setItem(NEXT_SLOT, named(Material.ARROW, "&eNext page", "&7Page " + (current + 2) + "/" + pages));
        }
        inv.setItem(BACK_SLOT, named(Material.OAK_DOOR, "&cBack", "&7Return to the categories"));

        holder.toggleSlot = TOGGLE_SLOT_CATEGORY;
        inv.setItem(TOGGLE_SLOT_CATEGORY, quickBuyToggle(player));

        fillEmpty(inv, PAGE_SIZE, 54);
        show(player, inv);
    }

    public void openItem(Player player, ShopCategory category, ShopItem item, int page) {
        Holder holder = new Holder(Type.ITEM, category.getId(), page, item);
        Inventory inv = Bukkit.createInventory(holder, 54, title(ClassicShop.itemName(item.getTemplate())));
        holder.inventory = inv;

        renderItem(inv, player, holder);
        show(player, inv);
    }

    /** Opens the operator price editor for an item that is already in the shop. */
    public void openEditor(Player player, ShopCategory category, ShopItem item, int page) {
        if (!player.hasPermission(ADMIN)) {
            plugin.send(player, "no-permission");
            return;
        }
        Holder holder = new Holder(Type.EDIT, category.getId(), page, item);
        Inventory inv = Bukkit.createInventory(holder, 54, title("Edit: " + ClassicShop.itemName(item.getTemplate())));
        holder.inventory = inv;

        renderEditor(inv, holder);
        show(player, inv);
    }

    /** Same as above, looking the category and page up from the item (used by /shop edit). */
    public void openEditor(Player player, ShopItem item) {
        ShopCategory category = plugin.shop().categoryOf(item);
        if (category == null) {
            plugin.send(player, "item-not-in-shop");
            return;
        }
        openEditor(player, category, item, category.getItems().indexOf(item) / PAGE_SIZE);
    }

    private void show(Player player, Inventory inv) {
        player.openInventory(inv);
        // Added after opening: opening a menu while another is open fires the old menu's close event first.
        viewers.add(player.getUniqueId());
    }

    public void closeAll() {
        for (UUID id : new ArrayList<>(viewers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.closeInventory();
            }
        }
        viewers.clear();
    }

    // ------------------------------------------------------------------
    // Building items
    // ------------------------------------------------------------------

    private ItemStack categoryIcon(ShopCategory category) {
        int count = category.getItems().size();
        return named(category.getIcon(), category.getDisplayName(),
                "&7" + count + (count == 1 ? " item" : " items"),
                "",
                "&eClick to browse");
    }

    private ItemStack quickBuyToggle(Player player) {
        boolean on = plugin.settings().isQuickBuy(player.getUniqueId());
        return named(on ? Material.LIME_DYE : Material.GRAY_DYE,
                "&eQuick buy: " + (on ? "&aON" : "&cOFF"),
                "&7When on, in an item list:",
                "&fLeft-click &7buys 1",
                "&fShift-click &7buys a stack",
                "&fRight-click &7opens the amount screen",
                "",
                "&eClick to turn " + (on ? "off" : "on"));
    }

    /** An item in a category list: the item itself plus its prices and what clicking does. */
    private ItemStack listing(ShopItem shopItem, Player player) {
        ItemStack stack = shopItem.createStack(1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        List<String> lore = new ArrayList<>();
        List<String> existing = meta.getLore();
        if (existing != null && !existing.isEmpty()) {
            lore.addAll(existing);
            lore.add(c(""));
        }
        lore.add(c("&7Buy: " + (shopItem.canBuy() ? "&a" + plugin.money(shopItem.getBuyPrice()) : "&8not for sale")));
        lore.add(c("&7Sell: " + (shopItem.canSell() ? "&c" + plugin.money(shopItem.getSellPrice()) : "&8can't be sold")));
        lore.add(c(""));
        if (plugin.settings().isQuickBuy(player.getUniqueId())) {
            lore.add(c("&eLeft-click: buy 1"));
            lore.add(c("&eShift-left-click: buy a stack"));
            lore.add(c("&eRight-click: choose an amount / sell"));
        } else {
            lore.add(c("&eClick to choose an amount"));
        }
        if (player.hasPermission(ADMIN)) {
            lore.add(c("&cShift-right-click: edit prices (admin)"));
        }

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private void renderItem(Inventory inv, Player player, Holder holder) {
        ShopItem item = holder.item;
        Economy economy = plugin.getEconomy();
        int have = TradeService.count(player.getInventory(), item);
        String balance = economy == null ? "?" : plugin.money(economy.getBalance(player));

        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }

        // The item being shown
        List<String> info = new ArrayList<>();
        info.add("&7Buy: " + (item.canBuy() ? "&a" + plugin.money(item.getBuyPrice()) + " &7each" : "&8not for sale"));
        info.add("&7Sell: " + (item.canSell() ? "&c" + plugin.money(item.getSellPrice()) + " &7each" : "&8can't be sold"));
        info.add("");
        info.add("&7You have: &f" + have);
        info.add("&7Balance: &f" + balance);
        inv.setItem(DISPLAY_SLOT, itemButton(item, 1, "&f" + ClassicShop.itemName(item.getTemplate()), info));

        // Row labels
        inv.setItem(BUY_LABEL_SLOT, named(Material.LIME_STAINED_GLASS_PANE, "&a&lBUY"));
        inv.setItem(SELL_LABEL_SLOT, named(Material.RED_STAINED_GLASS_PANE, "&c&lSELL"));

        for (int i = 0; i < AMOUNTS.length; i++) {
            int amount = AMOUNTS[i];

            if (item.canBuy()) {
                List<String> lore = new ArrayList<>();
                lore.add("&7Cost: &a" + plugin.money(Math.round(item.getBuyPrice() * amount * 100.0) / 100.0));
                lore.add("");
                lore.add("&eClick to buy");
                inv.setItem(BUY_SLOTS[i], itemButton(item, amount, "&aBuy x" + amount, lore));
            } else {
                inv.setItem(BUY_SLOTS[i], named(Material.BARRIER, "&cNot for sale"));
            }

            if (item.canSell()) {
                List<String> lore = new ArrayList<>();
                lore.add("&7You get: &a" + plugin.money(Math.round(item.getSellPrice() * amount * 100.0) / 100.0));
                if (have < amount) {
                    lore.add("&cYou don't have enough");
                } else {
                    lore.add("");
                    lore.add("&eClick to sell");
                }
                inv.setItem(SELL_SLOTS[i], itemButton(item, amount, "&cSell x" + amount, lore));
            } else {
                inv.setItem(SELL_SLOTS[i], named(Material.BARRIER, "&cCan't be sold"));
            }
        }

        if (item.canSell()) {
            double total = Math.round(item.getSellPrice() * have * 100.0) / 100.0;
            inv.setItem(SELL_ALL_SLOT, named(Material.HOPPER, "&cSell all",
                    "&7You have: &f" + have,
                    "&7You get: &a" + plugin.money(total),
                    "",
                    "&eClick to sell everything"));
        }

        inv.setItem(BACK_SLOT, named(Material.OAK_DOOR, "&cBack", "&7Return to the item list"));
        fillEmpty(inv, 0, inv.getSize());
    }

    private void renderEditor(Inventory inv, Holder holder) {
        ShopItem item = holder.item;

        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }

        List<String> info = new ArrayList<>();
        info.add("&7Buy: " + (item.canBuy() ? "&a" + plugin.money(item.getBuyPrice()) : "&8disabled"));
        info.add("&7Sell: " + (item.canSell() ? "&c" + plugin.money(item.getSellPrice()) : "&8disabled"));
        info.add("");
        info.add("&7Click the buttons below to change prices.");
        info.add("&7Shift-click changes them 100x faster.");
        info.add("&7Exact values: &f/shop addhanditem <price> <sell>");
        if (item.canBuy() && item.canSell() && item.getSellPrice() > item.getBuyPrice()) {
            info.add("");
            info.add("&cWarning: sell price is higher than buy");
            info.add("&cprice, players can make unlimited money!");
        }
        inv.setItem(EDIT_ITEM_SLOT, itemButton(item, 1, "&f" + ClassicShop.itemName(item.getTemplate()), info));

        renderPriceRow(inv, EDIT_BUY_ROW, item.getBuyPrice(), true);
        renderPriceRow(inv, EDIT_SELL_ROW, item.getSellPrice(), false);

        inv.setItem(EDIT_REMOVE_SLOT, named(Material.LAVA_BUCKET, "&4Remove from shop",
                "&7Shift-click to remove this item", "&7from the shop for good."));
        inv.setItem(BACK_SLOT, named(Material.OAK_DOOR, "&cBack", "&7Return to the item list"));
        fillEmpty(inv, 0, inv.getSize());
    }

    private void renderPriceRow(Inventory inv, int[] row, double price, boolean buy) {
        boolean enabled = price >= 0;
        for (int i = 0; i < row.length; i++) {
            if (i == 4) {
                inv.setItem(row[i], named(buy ? Material.EMERALD : Material.REDSTONE,
                        (buy ? "&aBuy price: " : "&cSell price: ") + (enabled ? "&f" + plugin.money(price) : "&8disabled"),
                        buy ? "&7What players pay per item" : "&7What players get per item",
                        "",
                        "&eClick to " + (enabled ? "disable" : "enable")));
            } else {
                double step = EDIT_STEPS[i];
                boolean plus = step > 0;
                String sign = plus ? "+" : "-";
                inv.setItem(row[i], named(plus ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                        (plus ? "&a" : "&c") + sign + formatStep(Math.abs(step)),
                        "&7Click: " + sign + formatStep(Math.abs(step)),
                        "&7Shift-click: " + sign + formatStep(Math.abs(step) * 100)));
            }
        }
    }

    private static String formatStep(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }

    private static String c(String text) {
        // "&r" resets the italic that item names and lore get by default.
        return ClassicShop.color("&r" + text);
    }

    private static List<String> coloured(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.add(c(line));
        }
        return out;
    }

    private static ItemStack named(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c(name));
            if (lore.length > 0) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(c(line));
                }
                meta.setLore(lines);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** The shop item shown as a button: stack size shows the amount, name and lore are replaced. */
    private static ItemStack itemButton(ShopItem item, int amount, String name, List<String> lore) {
        ItemStack stack = item.createStack(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c(name));
            meta.setLore(coloured(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void fillEmpty(Inventory inv, int from, int to) {
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = from; i < to; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    private static String title(String text) {
        String stripped = ChatColor.stripColor(ClassicShop.color(text));
        if (stripped == null) {
            stripped = "";
        }
        if (stripped.length() > 30) {
            stripped = stripped.substring(0, 30);
        }
        return ClassicShop.color("&8" + stripped);
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof Holder holder)) {
            return;
        }

        // Nothing can be moved in or out of a shop menu, from either inventory.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return; // click was in the player's own inventory
        }

        switch (holder.type) {
            case MAIN -> clickMain(player, holder, slot);
            case CATEGORY -> clickCategory(player, holder, slot, event.getClick());
            case ITEM -> clickItem(player, top, holder, slot);
            case EDIT -> clickEditor(player, top, holder, slot, event.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            viewers.remove(event.getPlayer().getUniqueId());
        }
    }

    private static boolean isPlainClick(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT
                || click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
    }

    private void toggleQuickBuy(Player player) {
        boolean now = plugin.settings().toggleQuickBuy(player.getUniqueId());
        plugin.send(player, now ? "quickbuy-on" : "quickbuy-off");
        plugin.play(player, Sound.UI_BUTTON_CLICK);
    }

    private void clickMain(Player player, Holder holder, int slot) {
        if (slot == holder.toggleSlot) {
            toggleQuickBuy(player);
            Bukkit.getScheduler().runTask(plugin, () -> openMain(player));
            return;
        }

        String id = holder.slotToCategory.get(slot);
        if (id == null) {
            return;
        }
        ShopCategory category = plugin.shop().getCategory(id);
        if (category == null) {
            return;
        }
        plugin.play(player, Sound.UI_BUTTON_CLICK);
        Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, category, 0));
    }

    private void clickCategory(Player player, Holder holder, int slot, ClickType click) {
        ShopCategory category = plugin.shop().getCategory(holder.categoryId);
        if (category == null) {
            Bukkit.getScheduler().runTask(plugin, player::closeInventory);
            return;
        }

        if (slot < PAGE_SIZE) {
            if (!isPlainClick(click)) {
                return;
            }
            int index = holder.page * PAGE_SIZE + slot;
            if (index >= category.getItems().size()) {
                return;
            }
            ShopItem item = category.getItems().get(index);

            // Operators: shift-right-click edits the price.
            if (click == ClickType.SHIFT_RIGHT && player.hasPermission(ADMIN)) {
                plugin.play(player, Sound.UI_BUTTON_CLICK);
                Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, category, item, holder.page));
                return;
            }

            // Quick buy: left-click buys 1, shift-left-click buys a stack.
            if (plugin.settings().isQuickBuy(player.getUniqueId())) {
                if (click == ClickType.LEFT) {
                    plugin.trade().buy(player, item, 1);
                    return;
                }
                if (click == ClickType.SHIFT_LEFT) {
                    plugin.trade().buy(player, item, item.getMaxStackSize());
                    return;
                }
            }

            plugin.play(player, Sound.UI_BUTTON_CLICK);
            Bukkit.getScheduler().runTask(plugin, () -> openItem(player, category, item, holder.page));
        } else if (slot == TOGGLE_SLOT_CATEGORY) {
            toggleQuickBuy(player);
            Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, category, holder.page));
        } else if (slot == PREV_SLOT && holder.page > 0) {
            plugin.play(player, Sound.UI_BUTTON_CLICK);
            Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, category, holder.page - 1));
        } else if (slot == NEXT_SLOT) {
            int pages = (category.getItems().size() + PAGE_SIZE - 1) / PAGE_SIZE;
            if (holder.page < pages - 1) {
                plugin.play(player, Sound.UI_BUTTON_CLICK);
                Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, category, holder.page + 1));
            }
        } else if (slot == BACK_SLOT) {
            plugin.play(player, Sound.UI_BUTTON_CLICK);
            Bukkit.getScheduler().runTask(plugin, () -> openMain(player));
        }
    }

    private void clickItem(Player player, Inventory top, Holder holder, int slot) {
        ShopCategory category = plugin.shop().getCategory(holder.categoryId);
        if (category == null || !plugin.shop().isListed(holder.item)) {
            plugin.send(player, "item-removed");
            Bukkit.getScheduler().runTask(plugin, player::closeInventory);
            return;
        }

        if (slot == BACK_SLOT) {
            plugin.play(player, Sound.UI_BUTTON_CLICK);
            Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, category, holder.page));
            return;
        }

        for (int i = 0; i < AMOUNTS.length; i++) {
            if (slot == BUY_SLOTS[i]) {
                plugin.trade().buy(player, holder.item, AMOUNTS[i]);
                renderItem(top, player, holder);
                return;
            }
            if (slot == SELL_SLOTS[i]) {
                plugin.trade().sell(player, holder.item, AMOUNTS[i]);
                renderItem(top, player, holder);
                return;
            }
        }

        if (slot == SELL_ALL_SLOT && holder.item.canSell()) {
            plugin.trade().sellAll(player, holder.item);
            renderItem(top, player, holder);
        }
    }

    private void clickEditor(Player player, Inventory top, Holder holder, int slot, ClickType click) {
        // Checked on every click, so losing the permission while the editor is open stops edits.
        if (!player.hasPermission(ADMIN)) {
            plugin.send(player, "no-permission");
            Bukkit.getScheduler().runTask(plugin, player::closeInventory);
            return;
        }

        ShopCategory category = plugin.shop().getCategory(holder.categoryId);
        if (category == null || !plugin.shop().isListed(holder.item)) {
            plugin.send(player, "item-removed");
            Bukkit.getScheduler().runTask(plugin, player::closeInventory);
            return;
        }
        if (!isPlainClick(click)) {
            return;
        }

        ShopItem item = holder.item;

        if (slot == BACK_SLOT) {
            plugin.play(player, Sound.UI_BUTTON_CLICK);
            Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, category, holder.page));
            return;
        }

        if (slot == EDIT_REMOVE_SLOT) {
            if (click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) {
                plugin.play(player, Sound.ENTITY_VILLAGER_NO);
                return;
            }
            plugin.shop().removeListing(item);
            plugin.send(player, "item-deleted",
                    "{item}", ClassicShop.itemName(item.getTemplate()),
                    "{category}", category.getId());
            Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, category, holder.page));
            return;
        }

        for (int i = 0; i < EDIT_BUY_ROW.length; i++) {
            if (slot == EDIT_BUY_ROW[i]) {
                editPrice(player, item, true, i, click);
                renderEditor(top, holder);
                return;
            }
            if (slot == EDIT_SELL_ROW[i]) {
                editPrice(player, item, false, i, click);
                renderEditor(top, holder);
                return;
            }
        }
    }

    private void editPrice(Player player, ShopItem item, boolean buy, int index, ClickType click) {
        double current = buy ? item.getBuyPrice() : item.getSellPrice();
        double other = buy ? item.getSellPrice() : item.getBuyPrice();
        double result;

        if (index == 4) {
            // Centre button: enable or disable this side.
            if (current >= 0) {
                if (other < 0) {
                    plugin.send(player, "edit-need-one-side");
                    plugin.play(player, Sound.ENTITY_VILLAGER_NO);
                    return;
                }
                result = -1;
            } else {
                result = 1;
            }
        } else {
            double delta = EDIT_STEPS[index] * (click.isShiftClick() ? 100 : 1);
            if (current < 0) {
                if (delta < 0) {
                    return; // can't lower a disabled price
                }
                current = 0;
            }
            result = Math.round((current + delta) * 100.0) / 100.0;
            result = Math.max(0, Math.min(MAX_PRICE, result));
        }

        if (buy) {
            item.setBuyPrice(result);
        } else {
            item.setSellPrice(result);
        }
        plugin.shop().save();
        plugin.play(player, Sound.UI_BUTTON_CLICK);
    }
}
