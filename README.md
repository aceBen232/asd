# ClassicShop

One Paper plugin, one jar (`ClassicShop.jar`), with two commands:

- **`/shop`**: a category-based shop in the style of the old DonutSMP shop, with quick buy and an in-game price editor for operators.
- **`/sell`**: a menu where you drop items in and get paid for them.

## Requirements

- Paper 1.21.11 (built against the 1.21.11 API; `api-version` is `1.21`, so other 1.21.x servers load it too) and Java 21
- [Vault](https://www.spigotmc.org/resources/vault.34315/) plus any economy plugin that hooks into it (EssentialsX, CMI, etc.)

## Getting the jar

The jar is not prebuilt, you build it from this source in one of two ways.

**Option A: on GitHub, nothing to install**

1. Create a free GitHub repository and upload everything in this folder (including the hidden `.github` folder).
2. Open the repository's **Actions** tab, pick **Build plugin**, click **Run workflow**.
3. When it finishes, download the **ClassicShop-jar** artifact from the run. It contains `ClassicShop.jar`.

**Option B: on your own computer** (JDK 21 and Maven installed)

```
mvn package
```

You get `target/ClassicShop.jar`.

Put the jar in your server's `plugins/` folder together with Vault, then restart. The `paper-api` version in `pom.xml` is already set to `1.21.11-R0.1-SNAPSHOT`. If your server runs a different 1.21.x, change it before building.

## /shop

1. `/shop` opens a menu of categories (Blocks, Ores, Tools & Weapons, Food, Misc by default).
2. Click a category to see its items and prices.
3. Click an item to pick an amount: **Buy** x1 / x8 / x16 / x32 / x64, **Sell** x1 / x8 / x16 / x32 / x64, or **Sell all**.

### Quick buy

`/shop quickbuy` toggles quick buy for you (or `/shop quickbuy on` / `off`). There is also a toggle button in the shop menus. It is saved per player. While it is on, in an item list:

- **Left-click** buys 1
- **Shift-left-click** buys a full stack
- **Right-click** opens the amount screen (where you can also sell)

Set `quickbuy-default: true` in config.yml to turn it on for players who haven't chosen yet.

### Commands

| Command | What it does | Permission |
|---|---|---|
| `/shop` | Open the shop | `classicshop.use` (default: everyone) |
| `/shop <category>` | Open a category directly | `classicshop.use` |
| `/shop quickbuy [on\|off]` | Toggle quick buy | `classicshop.use` |
| `/sell` | Open the sell menu | `classicshop.sell` (default: everyone) |
| `/shop addhanditem <price> <sell price> [category]` | Add (or update) the item in your hand | `classicshop.admin` (default: op) |
| `/shop edit` | Open the price editor for the shop item in your hand | `classicshop.admin` |
| `/shop removehanditem` | Remove the item in your hand from the shop | `classicshop.admin` |
| `/shop addcategory <id> [icon] [display name]` | Create a category | `classicshop.admin` |
| `/shop removecategory <id> [confirm]` | Delete a category | `classicshop.admin` |
| `/shop reload` | Reload config.yml and shop.yml | `classicshop.admin` |

### Editing prices (operators)

Anyone with `classicshop.admin` (ops by default) can change prices without commands:

- **Shift-right-click** an item in a shop category, or hold it and run `/shop edit`.
- The editor has a buy row and a sell row. Click `-1000 / -100 / -10 / -1 / +1 / +10 / +100 / +1000` to change the price, **shift-click** to change it 100x faster. The middle button turns buying or selling on or off for that item.
- **Shift-click the lava bucket** to remove the item from the shop.
- For an exact value use `/shop addhanditem <price> <sell price>` while holding the item. It also accepts `-1` to disable a side, and `k`/`m`/`b` (`1.5k` = 1,500, `2m` = 2,000,000).

Price is what a player pays to buy one item, sell price is what they get for selling one back. You get a warning if the sell price is higher than the buy price, because players could buy and resell for unlimited money.

Example:

```
/shop addhanditem 50 10 blocks
/shop addhanditem 2.5k 1k tools
/shop addcategory weapons DIAMOND_SWORD &cWeapons
```

## /sell

`/sell` opens a menu. Put items in the top area and anything with a sell price is sold when you close the menu, or when you click the green **Sell** button, which also shows the live total. Items with no sell price are handed back. The **Cancel** button closes the menu and returns everything without selling.

- Permission: `classicshop.sell` (default: everyone)
- **Flat price:** `sell.flat-sell-price: 1` in config.yml (the default) makes every item sell for $1 each in `/sell`, whether or not it's in the shop. Change the number to pay something else, or set it to `0` to turn it off.
- With the flat price off, `/sell` uses each item's sell price from the shop, so editing a price in the shop editor changes what `/sell` pays, and an item with no shop listing (or with selling disabled) can't be sold.
- The flat price only affects `/sell`. The sell buttons inside `/shop` still use each item's own sell price.
- `sell.sell-on-close: false` makes closing the menu return your items instead, so players must click Sell.
- Creative-mode players can't use `/sell` (config: `sell.block-creative`).
- The plugin does not ship DonutSMP's actual prices. Set them yourself with the commands above.

## Files

- `plugins/ClassicShop/config.yml`: currency symbol, menu title, quick buy default, sell settings, sounds, all messages.
- `plugins/ClassicShop/shop.yml`: categories and items (managed by the commands/editor; you can also edit it and run `/shop reload`).
- `plugins/ClassicShop/playerdata.yml`: each player's quick buy setting.

## Notes

- Up to 28 categories fit in the main menu.
- Selling in `/shop` counts items in the hotbar and main inventory (not armor or offhand), and the item must match the shop item exactly (same enchants, name, etc.). Damaged tools won't match an undamaged shop listing.
- With the flat price on, shulker boxes sell for $1 like anything else and their contents are lost.
- Items sitting in an open `/sell` menu exist only in that menu. They are returned if the menu closes, the player leaves or the plugin is disabled, but a hard server crash while someone has it open would lose them, like any chest-style menu.
