package dev.classicshop;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The /sell menu. The top five rows are free space: put items in, and anything with a sell price
 * is sold when the menu is closed (or when the Sell button is clicked). Items that can't be sold
 * are always handed back. The bottom row is locked.
 */
public class SellGUI implements Listener {

    private static final int DEPOSIT_SIZE = 45;   // slots 0-44
    private static final int CANCEL_SLOT = 45;
    private static final int SELL_SLOT = 49;
    private static final int SIZE = 54;

    public static final class SellHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ClassicShop plugin;
    /** Players who currently have an active (not yet settled) sell menu. */
    private final Map<UUID, Inventory> sessions = new HashMap<>();

    public SellGUI(ClassicShop plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------------

    public void open(Player player) {
        if (plugin.getConfig().getBoolean("sell.block-creative", true) && player.getGameMode() == GameMode.CREATIVE) {
            plugin.send(player, "sell-creative-blocked");
            return;
        }

        SellHolder holder = new SellHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ClassicShop.color(plugin.getConfig().getString("sell.title", "&8Sell Items")));
        holder.inventory = inv;

        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = DEPOSIT_SIZE; i < SIZE; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(CANCEL_SLOT, named(Material.BARRIER, "&cCancel",
                "&7Close the menu and take all your", "&7items back without selling."));
        refresh(inv);

        player.openInventory(inv);
        // Registered after opening: if another menu was open its close event fires first.
        sessions.put(player.getUniqueId(), inv);
    }

    /** Called when the plugin disables: settle every open menu by returning the items. */
    public void shutdown() {
        for (Map.Entry<UUID, Inventory> entry : new ArrayList<>(sessions.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                settle(player, entry.getValue(), false);
                player.closeInventory();
            }
        }
        sessions.clear();
    }

    // ------------------------------------------------------------------
    // Settling (selling / returning)
    // ------------------------------------------------------------------

    /**
     * Ends a menu. If it was still active and {@code sell} is true, sellable items are sold.
     * Whatever is left in the deposit area is always returned, so nothing can get stuck in a
     * menu that has already been settled.
     */
    private void settle(Player player, Inventory inv, boolean sell) {
        boolean active = sessions.remove(player.getUniqueId()) != null;

        if (active && sell) {
            sellItems(player, inv);
        }

        int returned = 0;
        for (int i = 0; i < DEPOSIT_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            inv.setItem(i, null);
            giveBack(player, stack);
            returned += stack.getAmount();
        }
        if (active && returned > 0 && sell) {
            plugin.send(player, "sell-returned", "{count}", String.valueOf(returned));
        }
    }

    private void sellItems(Player player, Inventory inv) {
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            plugin.send(player, "no-economy");
            return;
        }

        double total = 0;
        int count = 0;
        List<ItemStack> taken = new ArrayList<>();

        for (int i = 0; i < DEPOSIT_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            double price = priceOf(stack);
            if (price < 0) {
                continue;
            }
            total += price * stack.getAmount();
            count += stack.getAmount();
            taken.add(stack);
            inv.setItem(i, null);
        }

        if (count == 0) {
            plugin.send(player, "sell-nothing-sellable");
            plugin.play(player, Sound.ENTITY_VILLAGER_NO);
            return;
        }

        total = Math.round(total * 100.0) / 100.0;
        EconomyResponse response = economy.depositPlayer(player, total);
        if (!response.transactionSuccess()) {
            for (ItemStack stack : taken) {
                giveBack(player, stack);
            }
            plugin.send(player, "transaction-failed");
            plugin.play(player, Sound.ENTITY_VILLAGER_NO);
            return;
        }

        plugin.send(player, "sell-sold", "{count}", String.valueOf(count), "{price}", plugin.money(total));
        plugin.play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
    }

    /**
     * Price paid per item for this stack, or -1 if it can't be sold.
     * With sell.flat-sell-price set (above 0) every item sells for that amount, listed in the shop or not;
     * otherwise the item's sell price from the shop is used.
     */
    private double priceOf(ItemStack stack) {
        double flat = plugin.getConfig().getDouble("sell.flat-sell-price", -1);
        if (flat > 0) {
            return flat;
        }
        ShopItem listing = plugin.shop().findItem(stack);
        return listing != null && listing.canSell() ? listing.getSellPrice() : -1;
    }

    private static void giveBack(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    // ------------------------------------------------------------------
    // The Sell button (shows a live total)
    // ------------------------------------------------------------------

    private void refresh(Inventory inv) {
        double total = 0;
        int sellable = 0;
        int unsellable = 0;

        for (int i = 0; i < DEPOSIT_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            double price = priceOf(stack);
            if (price >= 0) {
                total += price * stack.getAmount();
                sellable += stack.getAmount();
            } else {
                unsellable += stack.getAmount();
            }
        }
        total = Math.round(total * 100.0) / 100.0;

        boolean sellOnClose = plugin.getConfig().getBoolean("sell.sell-on-close", true);
        List<String> lore = new ArrayList<>();
        lore.add("&7Items to sell: &f" + sellable);
        lore.add("&7You will receive: &a" + plugin.money(total));
        if (unsellable > 0) {
            lore.add("&cCan't be sold (returned): &f" + unsellable);
        }
        lore.add("");
        lore.add("&eClick to sell now");
        lore.add(sellOnClose ? "&7Closing this menu also sells." : "&7Closing this menu returns your items.");

        inv.setItem(SELL_SLOT, named(Material.EMERALD_BLOCK, "&a&lSell", lore.toArray(new String[0])));
    }

    private static ItemStack named(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            // "&r" resets the italic that item names and lore get by default.
            meta.setDisplayName(ClassicShop.color("&r" + name));
            if (lore.length > 0) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(ClassicShop.color("&r" + line));
                }
                meta.setLore(lines);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof SellHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        // Inventory contents only change after the event, so update the total a tick later.
        Bukkit.getScheduler().runTask(plugin, () -> refresh(top));

        // Gathering items to the cursor could pull things out of the locked row.
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getRawSlot();
        if (slot >= DEPOSIT_SIZE && slot < SIZE) {
            // Locked bottom row: buttons only, nothing can be moved.
            event.setCancelled(true);
            if (slot == SELL_SLOT) {
                settle(player, top, true);
                Bukkit.getScheduler().runTask(plugin, player::closeInventory);
            } else if (slot == CANCEL_SLOT) {
                settle(player, top, false);
                Bukkit.getScheduler().runTask(plugin, player::closeInventory);
            }
        }
        // Anything else (the free area and the player's own inventory) is allowed.
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof SellHolder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot >= DEPOSIT_SIZE && slot < SIZE) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> refresh(top));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof SellHolder)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            settle(player, top, plugin.getConfig().getBoolean("sell.sell-on-close", true));
        }
    }
}
