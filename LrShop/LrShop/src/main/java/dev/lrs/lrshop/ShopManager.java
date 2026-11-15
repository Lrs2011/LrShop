package dev.lrs.lrshop;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShopManager {

    public record ShopItem(Material material, double buy, double sell, String currency,
                           Map<Enchantment, Integer> enchants) {
        public boolean buyable() {
            return buy >= 0;
        }

        public ItemStack createStack(int amount) {
            ItemStack it = new ItemStack(material, amount);
            if (enchants != null && !enchants.isEmpty()) {
                ItemMeta meta = it.getItemMeta();
                if (meta != null) {
                    for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                        meta.addEnchant(e.getKey(), e.getValue(), true);
                    }
                    it.setItemMeta(meta);
                }
            }
            return it;
        }

        public boolean sellable() {
            return sell >= 0;
        }
    }

    public static final class Category {
        public final String id;
        public final String name;
        public final Material icon;
        public final int slot;
        public final List<String> lore;
        public final String type;
        public final String currency;
        public final ConfigurationSection settings;
        public final List<ShopItem> items = new ArrayList<>();

        Category(String id, String name, Material icon, int slot, List<String> lore,
                 String type, String currency, ConfigurationSection settings) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.slot = slot;
            this.lore = lore;
            this.type = type;
            this.currency = currency;
            this.settings = settings;
        }
    }

    private final LrShop plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<Material, ShopItem> sellMap = new EnumMap<>(Material.class);
    private final Map<Material, ShopItem> buyMap = new EnumMap<>(Material.class);
    private String menuTitle = "&8Tienda";
    private int menuSize = 27;
    private boolean filler = true;
    private Material fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;

    public ShopManager(LrShop plugin) {
        this.plugin = plugin;
    }

    private static double num(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    public void load() {
        categories.clear();
        sellMap.clear();
        buyMap.clear();
        File f = new File(plugin.getDataFolder(), "shop.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String defCur = plugin.getConfig().getString("default-currency", "dinero").toLowerCase(Locale.ROOT);

        menuTitle = y.getString("menu.title", "&8Tienda");
        int size = y.getInt("menu.size", 27);
        menuSize = Math.max(9, Math.min(54, (size / 9) * 9));
        filler = y.getBoolean("menu.filler", true);
        Material fm = Material.matchMaterial(y.getString("menu.filler-material", "GRAY_STAINED_GLASS_PANE"));
        fillerMaterial = fm == null ? Material.GRAY_STAINED_GLASS_PANE : fm;

        ConfigurationSection root = y.getConfigurationSection("categories");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection cs = root.getConfigurationSection(id);
                if (cs == null) {
                    continue;
                }
                Material icon = Material.matchMaterial(cs.getString("icon", "CHEST"));
                if (icon == null) {
                    icon = Material.CHEST;
                }
                String catCur = cs.getString("currency", defCur).toLowerCase(Locale.ROOT);
                Category cat = new Category(id.toLowerCase(Locale.ROOT), cs.getString("name", id), icon,
                        cs.getInt("slot", 0), cs.getStringList("lore"),
                        cs.getString("type", "normal").toLowerCase(Locale.ROOT), catCur,
                        cs.getConfigurationSection("random"));
                for (Map<?, ?> m : cs.getMapList("items")) {
                    Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
                    if (mat == null || !mat.isItem()) {
                        plugin.getLogger().warning("Invalid material in category " + id + ": " + m.get("material"));
                        continue;
                    }
                    String cur = m.get("currency") != null ? String.valueOf(m.get("currency")).toLowerCase(Locale.ROOT) : catCur;
                    Map<Enchantment, Integer> ench = new LinkedHashMap<>();
                    if (m.get("enchants") instanceof Map<?, ?> em) {
                        for (Map.Entry<?, ?> ee : em.entrySet()) {
                            String key = String.valueOf(ee.getKey()).toLowerCase(Locale.ROOT);
                            NamespacedKey nk = NamespacedKey.minecraft(key);
                            Enchantment en = Registry.ENCHANTMENT.get(nk);
                            if (en == null || !(ee.getValue() instanceof Number lvl)) {
                                plugin.getLogger().warning("Invalid enchant in category " + id + ": " + ee.getKey());
                                continue;
                            }
                            ench.put(en, lvl.intValue());
                        }
                    }
                    ShopItem si = new ShopItem(mat, num(m.get("buy"), -1), num(m.get("sell"), -1), cur, ench);
                    cat.items.add(si);
                    if (si.sellable()) {
                        sellMap.putIfAbsent(mat, si);
                    }
                    if (si.buyable()) {
                        buyMap.putIfAbsent(mat, si);
                    }
                }
                categories.put(cat.id, cat);
            }
        }

        for (Map<?, ?> m : y.getMapList("sell-only")) {
            Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
            if (mat == null || !mat.isItem()) {
                plugin.getLogger().warning("Invalid sell-only material: " + m.get("material"));
                continue;
            }
            String cur = m.get("currency") != null ? String.valueOf(m.get("currency")).toLowerCase(Locale.ROOT) : defCur;
            double sell = num(m.get("sell"), -1);
            if (sell >= 0) {
                sellMap.putIfAbsent(mat, new ShopItem(mat, -1, sell, cur, Map.of()));
            }
        }
        plugin.getLogger().info("Loaded " + categories.size() + " categories, " + sellMap.size() + " sellable items.");
    }

    public Collection<Category> categories() {
        return categories.values();
    }

    public Category category(String id) {
        return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT));
    }

    public ShopItem sellEntry(Material m) {
        return sellMap.get(m);
    }

    public ShopItem buyEntry(Material m) {
        return buyMap.get(m);
    }

    public String menuTitle() {
        return menuTitle;
    }

    public int menuSize() {
        return menuSize;
    }

    public boolean filler() {
        return filler;
    }

    public Material fillerMaterial() {
        return fillerMaterial;
    }
}
