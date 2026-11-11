package dev.classicshop;

import org.bukkit.inventory.ItemStack;

/**
 * One item listed in the shop. The template is the exact item that was in the admin's hand
 * (so enchants, names, potion types etc. are kept), normalised to an amount of 1.
 */
public class ShopItem {

    private final ItemStack template;
    private double buyPrice;   // what a player pays per item; negative = can't be bought
    private double sellPrice;  // what a player receives per item; negative = can't be sold

    public ShopItem(ItemStack stack, double buyPrice, double sellPrice) {
        this.template = stack.clone();
        this.template.setAmount(1);
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    public ItemStack getTemplate() {
        return template.clone();
    }

    public ItemStack createStack(int amount) {
        ItemStack stack = template.clone();
        stack.setAmount(amount);
        return stack;
    }

    public int getMaxStackSize() {
        return Math.max(1, template.getMaxStackSize());
    }

    public boolean matches(ItemStack other) {
        return other != null && template.isSimilar(other);
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public void setBuyPrice(double buyPrice) {
        this.buyPrice = buyPrice;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(double sellPrice) {
        this.sellPrice = sellPrice;
    }

    public boolean canBuy() {
        return buyPrice >= 0;
    }

    public boolean canSell() {
        return sellPrice >= 0;
    }
}
