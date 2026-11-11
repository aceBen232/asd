package dev.classicshop;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** All the actual buying and selling: money checks, inventory checks, giving/taking items. */
public class TradeService {

    /** Slots 0-35 of a player inventory are the hotbar and main storage (no armor/offhand). */
    private static final int STORAGE_SLOTS = 36;

    private final ClassicShop plugin;

    public TradeService(ClassicShop plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Inventory helpers
    // ------------------------------------------------------------------

    public static int count(Inventory inv, ShopItem item) {
        int total = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && item.matches(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** How many more of this item fit into the player's storage. */
    public static int roomFor(Inventory inv, ShopItem item) {
        int max = item.getMaxStackSize();
        int room = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                room += max;
            } else if (item.matches(stack)) {
                room += Math.max(0, max - stack.getAmount());
            }
        }
        return room;
    }

    private static void remove(Inventory inv, ShopItem item, int amount) {
        int left = amount;
        for (int i = 0; i < STORAGE_SLOTS && left > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || !item.matches(stack)) {
                continue;
            }
            if (stack.getAmount() <= left) {
                left -= stack.getAmount();
                inv.setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - left);
                inv.setItem(i, stack);
                left = 0;
            }
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------
    // Buy / sell
    // ------------------------------------------------------------------

    public boolean buy(Player player, ShopItem item, int amount) {
        if (!item.canBuy()) {
            fail(player, "not-buyable");
            return false;
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            fail(player, "no-economy");
            return false;
        }

        double total = round2(item.getBuyPrice() * amount);
        if (!economy.has(player, total)) {
            fail(player, "not-enough-money", "{price}", plugin.money(total));
            return false;
        }

        int room = roomFor(player.getInventory(), item);
        if (room < amount) {
            fail(player, "no-space", "{room}", String.valueOf(room));
            return false;
        }

        EconomyResponse response = economy.withdrawPlayer(player, total);
        if (!response.transactionSuccess()) {
            fail(player, "transaction-failed");
            return false;
        }

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.createStack(amount));
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }

        plugin.send(player, "bought",
                "{amount}", String.valueOf(amount),
                "{item}", ClassicShop.itemName(item.getTemplate()),
                "{price}", plugin.money(total));
        plugin.play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        return true;
    }

    public boolean sell(Player player, ShopItem item, int amount) {
        if (!item.canSell()) {
            fail(player, "not-sellable");
            return false;
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            fail(player, "no-economy");
            return false;
        }

        int have = count(player.getInventory(), item);
        if (have < amount) {
            fail(player, "not-enough-items", "{amount}", String.valueOf(amount), "{have}", String.valueOf(have));
            return false;
        }

        double total = round2(item.getSellPrice() * amount);

        remove(player.getInventory(), item, amount);
        EconomyResponse response = economy.depositPlayer(player, total);
        if (!response.transactionSuccess()) {
            // Payment failed, hand the items back.
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.createStack(amount));
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
            fail(player, "transaction-failed");
            return false;
        }

        plugin.send(player, "sold",
                "{amount}", String.valueOf(amount),
                "{item}", ClassicShop.itemName(item.getTemplate()),
                "{price}", plugin.money(total));
        plugin.play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        return true;
    }

    public boolean sellAll(Player player, ShopItem item) {
        int have = count(player.getInventory(), item);
        if (have <= 0) {
            fail(player, "nothing-to-sell");
            return false;
        }
        return sell(player, item, have);
    }

    private void fail(Player player, String key, String... replacements) {
        plugin.send(player, key, replacements);
        plugin.play(player, Sound.ENTITY_VILLAGER_NO);
    }
}
