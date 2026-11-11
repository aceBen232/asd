package dev.classicshop;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class ShopCategory {

    private final String id;
    private String displayName;
    private Material icon;
    private final List<ShopItem> items = new ArrayList<>();

    public ShopCategory(String id, String displayName, Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public List<ShopItem> getItems() {
        return items;
    }
}
