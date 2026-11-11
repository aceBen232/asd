package dev.classicshop;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private static final String USE = "classicshop.use";
    private static final String ADMIN = "classicshop.admin";
    private static final double MAX_PRICE = 1_000_000_000_000_000.0;

    private final ClassicShop plugin;

    public ShopCommand(ClassicShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            openMain(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help", "?" -> sendHelp(sender);
            case "quickbuy" -> quickBuy(sender, args);
            case "addhanditem", "removehanditem", "edit", "addcategory", "removecategory", "reload" -> {
                if (!sender.hasPermission(ADMIN)) {
                    plugin.send(sender, "no-permission");
                    return true;
                }
                switch (sub) {
                    case "addhanditem" -> addHandItem(sender, args);
                    case "edit" -> editHandItem(sender);
                    case "removehanditem" -> removeHandItem(sender);
                    case "addcategory" -> addCategory(sender, args);
                    case "removecategory" -> removeCategory(sender, args);
                    default -> reload(sender);
                }
            }
            default -> openCategoryByName(sender, args[0]);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Player commands
    // ------------------------------------------------------------------

    private void openMain(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "players-only");
            return;
        }
        if (!player.hasPermission(USE)) {
            plugin.send(player, "no-permission");
            return;
        }
        plugin.gui().openMain(player);
    }

    private void openCategoryByName(CommandSender sender, String name) {
        ShopCategory category = plugin.shop().getCategory(name);
        if (category == null) {
            plugin.send(sender, "unknown-subcommand");
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "players-only");
            return;
        }
        if (!player.hasPermission(USE)) {
            plugin.send(player, "no-permission");
            return;
        }
        plugin.gui().openCategory(player, category, 0);
    }

    /** /shop quickbuy [on|off]: toggles (or sets) the player's quick buy setting. */
    private void quickBuy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "players-only");
            return;
        }
        if (!player.hasPermission(USE)) {
            plugin.send(player, "no-permission");
            return;
        }

        boolean now;
        if (args.length >= 2 && args[1].equalsIgnoreCase("on")) {
            now = true;
            plugin.settings().setQuickBuy(player.getUniqueId(), true);
        } else if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
            now = false;
            plugin.settings().setQuickBuy(player.getUniqueId(), false);
        } else if (args.length >= 2) {
            plugin.send(player, "usage-quickbuy");
            return;
        } else {
            now = plugin.settings().toggleQuickBuy(player.getUniqueId());
        }
        plugin.send(player, now ? "quickbuy-on" : "quickbuy-off");
    }

    /** /shop edit: opens the price editor for the shop item in the operator's hand. */
    private void editHandItem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "players-only");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            plugin.send(player, "hold-item");
            return;
        }

        ShopItem item = plugin.shop().findItem(hand);
        if (item == null) {
            plugin.send(player, "item-not-in-shop");
            return;
        }
        plugin.gui().openEditor(player, item);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ClassicShop.color("&8&m      &r &aClassicShop &8&m      "));
        sender.sendMessage(ClassicShop.color("&f/shop &7- open the shop"));
        sender.sendMessage(ClassicShop.color("&f/shop <category> &7- open a category directly"));
        sender.sendMessage(ClassicShop.color("&f/shop quickbuy [on|off] &7- left-click buys 1, shift-click buys a stack"));
        if (sender.hasPermission(ADMIN)) {
            sender.sendMessage(ClassicShop.color("&f/shop edit &7- edit the prices of the shop item in your hand"));
            sender.sendMessage(ClassicShop.color("  &7(or shift-right-click an item in the shop)"));
            sender.sendMessage(ClassicShop.color("&f/shop addhanditem <price> <sell price> [category]"));
            sender.sendMessage(ClassicShop.color("  &7Adds the item in your hand. Price = what players pay,"));
            sender.sendMessage(ClassicShop.color("  &7sell price = what players get. Use &f-1 &7to disable one side. 1k / 2.5m work too."));
            sender.sendMessage(ClassicShop.color("&f/shop removehanditem &7- remove the item in your hand"));
            sender.sendMessage(ClassicShop.color("&f/shop addcategory <id> [icon] [display name]"));
            sender.sendMessage(ClassicShop.color("&f/shop removecategory <id> [confirm]"));
            sender.sendMessage(ClassicShop.color("&f/shop reload"));
        }
    }

    // ------------------------------------------------------------------
    // Admin commands
    // ------------------------------------------------------------------

    private void addHandItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "players-only");
            return;
        }
        if (args.length < 3) {
            plugin.send(player, "usage-addhanditem");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            plugin.send(player, "hold-item");
            return;
        }

        Double buyPrice = parsePrice(args[1]);
        Double sellPrice = parsePrice(args[2]);
        if (buyPrice == null || sellPrice == null) {
            plugin.send(player, "invalid-price");
            return;
        }
        if (buyPrice < 0 && sellPrice < 0) {
            plugin.send(player, "both-disabled");
            return;
        }

        ShopCategory target;
        if (args.length >= 4) {
            target = plugin.shop().getCategory(args[3]);
            if (target == null) {
                plugin.send(player, "unknown-category", "{category}", args[3].toLowerCase(Locale.ROOT));
                return;
            }
        } else {
            ShopCategory existing = plugin.shop().findCategoryOf(hand);
            target = existing != null ? existing : plugin.shop().getDefaultCategory();
        }

        boolean updated = plugin.shop().addOrUpdate(target, hand, buyPrice, sellPrice);
        plugin.send(player, updated ? "item-updated" : "item-added",
                "{item}", ClassicShop.itemName(hand),
                "{category}", target.getId(),
                "{buy}", plugin.priceOrDisabled(buyPrice),
                "{sell}", plugin.priceOrDisabled(sellPrice));

        if (buyPrice >= 0 && sellPrice > buyPrice) {
            plugin.send(player, "warn-exploit");
        }
    }

    private void removeHandItem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "players-only");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            plugin.send(player, "hold-item");
            return;
        }

        ShopCategory from = plugin.shop().removeItem(hand);
        if (from == null) {
            plugin.send(player, "item-not-in-shop");
            return;
        }
        plugin.send(player, "item-deleted", "{item}", ClassicShop.itemName(hand), "{category}", from.getId());
    }

    private void addCategory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.send(sender, "usage-addcategory");
            return;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        if (!ShopManager.ID_PATTERN.matcher(id).matches()) {
            plugin.send(sender, "invalid-category-id");
            return;
        }
        if (plugin.shop().getCategory(id) != null) {
            plugin.send(sender, "category-exists", "{category}", id);
            return;
        }
        if (plugin.shop().getCategories().size() >= ShopGUI.maxCategories()) {
            plugin.send(sender, "category-limit", "{max}", String.valueOf(ShopGUI.maxCategories()));
            return;
        }

        Material icon;
        if (args.length >= 3) {
            icon = Material.matchMaterial(args[2]);
            if (icon == null || icon.isAir() || !icon.isItem()) {
                plugin.send(sender, "invalid-icon", "{icon}", args[2]);
                return;
            }
        } else if (sender instanceof Player player && !player.getInventory().getItemInMainHand().getType().isAir()) {
            icon = player.getInventory().getItemInMainHand().getType();
        } else {
            icon = Material.CHEST;
        }

        String displayName = args.length >= 4
                ? String.join(" ", Arrays.copyOfRange(args, 3, args.length))
                : "&a" + ClassicShop.prettify(id);

        plugin.shop().addCategory(id, displayName, icon);
        plugin.send(sender, "category-added", "{category}", id);
    }

    private void removeCategory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.send(sender, "usage-removecategory");
            return;
        }

        ShopCategory category = plugin.shop().getCategory(args[1]);
        if (category == null) {
            plugin.send(sender, "unknown-category", "{category}", args[1].toLowerCase(Locale.ROOT));
            return;
        }

        boolean confirmed = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
        if (!category.getItems().isEmpty() && !confirmed) {
            plugin.send(sender, "category-not-empty",
                    "{category}", category.getId(),
                    "{count}", String.valueOf(category.getItems().size()));
            return;
        }

        plugin.shop().removeCategory(category.getId());
        plugin.send(sender, "category-removed", "{category}", category.getId());
    }

    private void reload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.gui().closeAll();
        plugin.shop().load();
        plugin.send(sender, "reloaded");
    }

    // ------------------------------------------------------------------
    // Price parsing: "100", "1.5k", "2m", "1b", "$50", "-1"
    // ------------------------------------------------------------------

    static Double parsePrice(String input) {
        String text = input.trim().toLowerCase(Locale.ROOT).replace(",", "");
        if (text.startsWith("$")) {
            text = text.substring(1);
        }
        if (text.isEmpty()) {
            return null;
        }

        double multiplier = 1;
        char last = text.charAt(text.length() - 1);
        if (last == 'k') {
            multiplier = 1_000;
        } else if (last == 'm') {
            multiplier = 1_000_000;
        } else if (last == 'b') {
            multiplier = 1_000_000_000;
        }
        if (multiplier != 1) {
            text = text.substring(0, text.length() - 1);
        }

        // Only plain decimal numbers; rejects things Double.parseDouble would accept like "5f" or "NaN".
        if (!text.matches("-?\\d+(\\.\\d+)?")) {
            return null;
        }

        double value = Double.parseDouble(text) * multiplier;
        if (value == -1) {
            return -1.0;          // explicit "disabled"
        }
        if (value < 0 || value > MAX_PRICE) {
            return null;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);

        if (args.length == 1) {
            List<String> options = new ArrayList<>(plugin.shop().getCategoryIds());
            options.add("help");
            options.add("quickbuy");
            if (admin) {
                options.addAll(List.of("addhanditem", "removehanditem", "edit", "addcategory", "removecategory", "reload"));
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("quickbuy")) {
            return filter(List.of("on", "off"), args[1]);
        }
        if (!admin) {
            return List.of();
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "addhanditem":
                if (args.length == 2 || args.length == 3) {
                    return filter(List.of("-1", "1", "10", "100", "1000", "1k"), args[args.length - 1]);
                }
                if (args.length == 4) {
                    return filter(plugin.shop().getCategoryIds(), args[3]);
                }
                break;
            case "removecategory":
                if (args.length == 2) {
                    return filter(plugin.shop().getCategoryIds(), args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("confirm"), args[2]);
                }
                break;
            case "addcategory":
                if (args.length == 3) {
                    String prefix = args[2].toLowerCase(Locale.ROOT);
                    return Arrays.stream(Material.values())
                            .filter(m -> !m.name().startsWith("LEGACY_") && m.isItem())
                            .map(m -> m.name().toLowerCase(Locale.ROOT))
                            .filter(name -> name.startsWith(prefix))
                            .limit(40)
                            .collect(Collectors.toList());
                }
                break;
            default:
                break;
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }
}
