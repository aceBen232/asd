package dev.classicshop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SellCommand implements CommandExecutor {

    private final ClassicShop plugin;

    public SellCommand(ClassicShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("classicshop.sell")) {
            plugin.send(player, "no-permission");
            return true;
        }
        plugin.sellGui().open(player);
        return true;
    }
}
