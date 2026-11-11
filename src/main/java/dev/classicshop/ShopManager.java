package dev.classicshop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** Holds every category and item, and reads/writes plugins/ClassicShop/shop.yml. */
public class ShopManager {

    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,24}");

    private final ClassicShop plugin;
    private final File file;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();

    public ShopManager(ClassicShop plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop.yml");
    }

    // ------------------------------------------------------------------
    // Load / save
    // ------------------------------------------------------------------

    public void load() {
        categories.clear();

        if (!file.exists()) {
            createDefaults();
            save();
            return;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("categories");
        if (root == null) {
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            Material icon = Material.matchMaterial(section.getString("icon", "CHEST"));
            if (icon == null || icon.isAir()) {
                icon = Material.CHEST;
            }

            ShopCategory category = new ShopCategory(
                    id.toLowerCase(Locale.ROOT),
                    section.getString("name", "&a" + ClassicShop.prettify(id)),
                    icon);

            ConfigurationSection itemSection = section.getConfigurationSection("items");
            if (itemSection != null) {
                for (String key : itemSection.getKeys(false)) {
                    ConfigurationSection entry = itemSection.getConfigurationSection(key);
                    if (entry == null) {
                        continue;
                    }
                    ItemStack stack = entry.getItemStack("item");
                    if (stack == null || stack.getType().isAir()) {
                        plugin.getLogger().warning("Skipping unreadable item " + id + "." + key + " in shop.yml");
                        continue;
                    }
                    category.getItems().add(new ShopItem(stack, entry.getDouble("buy", -1), entry.getDouble("sell", -1)));
                }
            }

            categories.put(category.getId(), category);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();

        for (ShopCategory category : categories.values()) {
            String base = "categories." + category.getId();
            yml.set(base + ".name", category.getDisplayName());
            yml.set(base + ".icon", category.getIcon().name());

            int index = 0;
            for (ShopItem item : category.getItems()) {
                String path = base + ".items." + index++;
                yml.set(path + ".item", item.getTemplate());
                yml.set(path + ".buy", item.getBuyPrice());
                yml.set(path + ".sell", item.getSellPrice());
            }
        }

        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save shop.yml", ex);
        }
    }

    private void createDefaults() {
        categories.put("blocks", new ShopCategory("blocks", "&aBlocks", Material.GRASS_BLOCK));
        categories.put("ores", new ShopCategory("ores", "&bOres", Material.DIAMOND));
        categories.put("tools", new ShopCategory("tools", "&6Tools & Weapons", Material.DIAMOND_PICKAXE));
        categories.put("food", new ShopCategory("food", "&cFood", Material.COOKED_BEEF));
        categories.put("misc", new ShopCategory("misc", "&dMisc", Material.ENDER_PEARL));
    }

    // ------------------------------------------------------------------
    // Categories
    // ------------------------------------------------------------------

    public Collection<ShopCategory> getCategories() {
        return Collections.unmodifiableCollection(categories.values());
    }

    public List<String> getCategoryIds() {
        return new ArrayList<>(categories.keySet());
    }

    public ShopCategory getCategory(String id) {
        return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT));
    }

    /** The category used when /shop addhanditem is run without a category. */
    public ShopCategory getDefaultCategory() {
        String id = plugin.getConfig().getString("default-category", "misc").toLowerCase(Locale.ROOT);
        ShopCategory category = categories.get(id);
        if (category == null) {
            category = new ShopCategory(id, "&a" + ClassicShop.prettify(id), Material.CHEST);
            categories.put(id, category);
        }
        return category;
    }

    public ShopCategory addCategory(String id, String displayName, Material icon) {
        ShopCategory category = new ShopCategory(id, displayName, icon);
        categories.put(id, category);
        save();
        return category;
    }

    public ShopCategory removeCategory(String id) {
        ShopCategory removed = categories.remove(id.toLowerCase(Locale.ROOT));
        if (removed != null) {
            save();
        }
        return removed;
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    public ShopCategory findCategoryOf(ItemStack stack) {
        for (ShopCategory category : categories.values()) {
            for (ShopItem item : category.getItems()) {
                if (item.matches(stack)) {
                    return category;
                }
            }
        }
        return null;
    }

    private ShopItem findItem(ShopCategory category, ItemStack stack) {
        for (ShopItem item : category.getItems()) {
            if (item.matches(stack)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Adds the item to the target category, or updates its prices if it is already listed
     * (moving it if it was in another category).
     *
     * @return true if an existing listing was updated, false if a new one was added
     */
    public boolean addOrUpdate(ShopCategory target, ItemStack stack, double buyPrice, double sellPrice) {
        ShopCategory existingCategory = findCategoryOf(stack);
        boolean updated = existingCategory != null;

        if (updated) {
            ShopItem existing = findItem(existingCategory, stack);
            if (existingCategory == target) {
                existing.setBuyPrice(buyPrice);
                existing.setSellPrice(sellPrice);
                save();
                return true;
            }
            existingCategory.getItems().remove(existing);
        }

        target.getItems().add(new ShopItem(stack, buyPrice, sellPrice));
        save();
        return updated;
    }

    /** @return the category the item was removed from, or null if it wasn't in the shop */
    public ShopCategory removeItem(ItemStack stack) {
        ShopCategory category = findCategoryOf(stack);
        if (category == null) {
            return null;
        }
        ShopItem item = findItem(category, stack);
        category.getItems().remove(item);
        save();
        return category;
    }

    /** The shop listing for this exact item (same enchants, name, etc.), or null. Used by /sell too. */
    public ShopItem findItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        for (ShopCategory category : categories.values()) {
            ShopItem item = findItem(category, stack);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    public ShopCategory categoryOf(ShopItem item) {
        for (ShopCategory category : categories.values()) {
            if (category.getItems().contains(item)) {
                return category;
            }
        }
        return null;
    }

    /** Removes this exact listing. @return true if it was in the shop. */
    public boolean removeListing(ShopItem item) {
        ShopCategory category = categoryOf(item);
        if (category == null) {
            return false;
        }
        category.getItems().remove(item);
        save();
        return true;
    }

    /** True if this exact listing is still in the shop (an admin may have removed it while a menu was open). */
    public boolean isListed(ShopItem item) {
        for (ShopCategory category : categories.values()) {
            if (category.getItems().contains(item)) {
                return true;
            }
        }
        return false;
    }
}
