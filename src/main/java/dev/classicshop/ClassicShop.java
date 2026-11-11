package dev.classicshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * ClassicShop - a category based /shop menu with buy and sell screens,
 * backed by any Vault economy.
 */
public final class ClassicShop extends JavaPlugin {

    private ShopManager shopManager;
    private PlayerSettings settings;
    private TradeService tradeService;
    private ShopGUI gui;
    private SellGUI sellGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        shopManager = new ShopManager(this);
        shopManager.load();
        settings = new PlayerSettings(this);
        settings.load();
        tradeService = new TradeService(this);
        gui = new ShopGUI(this);
        getServer().getPluginManager().registerEvents(gui, this);

        sellGui = new SellGUI(this);
        getServer().getPluginManager().registerEvents(sellGui, this);
        PluginCommand sellCommand = getCommand("sell");
        if (sellCommand != null) {
            sellCommand.setExecutor(new SellCommand(this));
        }

        ShopCommand command = new ShopCommand(this);
        PluginCommand shopCommand = getCommand("shop");
        if (shopCommand != null) {
            shopCommand.setExecutor(command);
            shopCommand.setTabCompleter(command);
        }

        // Economy plugins can register their service after we enable,
        // so only check once the server has finished loading.
        getServer().getScheduler().runTask(this, () -> {
            if (getEconomy() == null) {
                getLogger().warning("No Vault economy provider found. Install an economy plugin "
                        + "(e.g. EssentialsX) or buying and selling will not work.");
            }
        });
    }

    @Override
    public void onDisable() {
        // Close open shop menus so nobody can click items out of a menu that is no longer protected.
        if (gui != null) {
            gui.closeAll();
        }
        // Hand back anything still sitting in an open /sell menu.
        if (sellGui != null) {
            sellGui.shutdown();
        }
    }

    public ShopManager shop() {
        return shopManager;
    }

    public PlayerSettings settings() {
        return settings;
    }

    public TradeService trade() {
        return tradeService;
    }

    public ShopGUI gui() {
        return gui;
    }

    public SellGUI sellGui() {
        return sellGui;
    }

    /** Looks the economy up each time so it keeps working if the provider is reloaded. */
    public Economy getEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public static String prettify(String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("[_\\-\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public static String itemName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return prettify(stack.getType().name());
    }

    public String money(double amount) {
        DecimalFormat df = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return getConfig().getString("currency-symbol", "$") + df.format(amount);
    }

    /** Formats a price, or "disabled" for the -1 "not available" marker. */
    public String priceOrDisabled(double price) {
        return price < 0 ? "disabled" : money(price);
    }

    public String msg(String key, String... replacements) {
        String raw = getConfig().getString("messages." + key);
        if (raw == null) {
            raw = "&cMissing message: " + key;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return color(getConfig().getString("messages.prefix", "") + raw);
    }

    public void send(CommandSender to, String key, String... replacements) {
        to.sendMessage(msg(key, replacements));
    }

    public void play(Player player, Sound sound) {
        if (getConfig().getBoolean("sounds", true)) {
            player.playSound(player.getLocation(), sound, 1f, 1f);
        }
    }
}
