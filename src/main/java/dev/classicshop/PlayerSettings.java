package dev.classicshop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Per-player settings (currently just quick buy), saved in plugins/ClassicShop/playerdata.yml. */
public class PlayerSettings {

    private final ClassicShop plugin;
    private final File file;
    private final Map<UUID, Boolean> quickBuy = new HashMap<>();

    public PlayerSettings(ClassicShop plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playerdata.yml");
    }

    public void load() {
        quickBuy.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("quickbuy");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                quickBuy.put(UUID.fromString(key), section.getBoolean(key));
            } catch (IllegalArgumentException ignored) {
                // not a UUID, skip it
            }
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Boolean> entry : quickBuy.entrySet()) {
            yml.set("quickbuy." + entry.getKey(), entry.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save playerdata.yml", ex);
        }
    }

    public boolean isQuickBuy(UUID player) {
        Boolean value = quickBuy.get(player);
        return value != null ? value : plugin.getConfig().getBoolean("quickbuy-default", false);
    }

    public void setQuickBuy(UUID player, boolean enabled) {
        quickBuy.put(player, enabled);
        save();
    }

    /** @return the new state */
    public boolean toggleQuickBuy(UUID player) {
        boolean now = !isQuickBuy(player);
        setQuickBuy(player, now);
        return now;
    }
}
