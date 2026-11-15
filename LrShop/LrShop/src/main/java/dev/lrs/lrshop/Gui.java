package dev.lrs.lrshop;

import com.google.common.collect.HashMultimap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class Gui implements Listener {

    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 53;

    public static final class ShopHolder implements InventoryHolder {
        final ShopManager.Category category;
        final int page;
        Inventory inv;
        final Map<Integer, ShopManager.Category> cats = new HashMap<>();
        final Map<Integer, ShopManager.ShopItem> items = new HashMap<>();

        ShopHolder(ShopManager.Category category, int page) {
            this.category = category;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public static final class SellHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public static final class QuantityHolder implements InventoryHolder {
        final ShopManager.ShopItem item;
        final ShopManager.Category category;
        final int page;
        int qty = 1;
        Inventory inv;

        QuantityHolder(ShopManager.ShopItem item, ShopManager.Category category, int page) {
            this.item = item;
            this.category = category;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public record Offer(ItemStack give, double price) {
    }

    public static final class ArmorHolder implements InventoryHolder {
        final ShopManager.Category category;
        final Map<Integer, Offer> offers = new HashMap<>();
        Inventory inv;

        ArmorHolder(ShopManager.Category category) {
            this.category = category;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final String[] ARMOR_TIERS = {"LEATHER", "CHAINMAIL", "IRON", "GOLDEN", "DIAMOND", "NETHERITE"};
    private static final String[] ARMOR_PIECES = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
    private static final double[] ARMOR_BASE = {2, 2, 3, 3, 4, 5};
    private static final int A_BALANCE = 17;
    private static final int A_REROLL = 35;
    private static final int A_BACK = 53;
    private static final int[] A_DECOR = {0, 9, 18, 27, 36, 45, 8, 26, 44};
    private static final String[] TOOL_TIERS = {"WOODEN", "STONE", "IRON", "GOLDEN", "DIAMOND", "NETHERITE"};
    private static final List<String> COMMON_KEYS = List.of("unbreaking", "mending");
    private static final List<String> SWORD_KEYS = List.of("sharpness", "smite", "bane_of_arthropods", "knockback",
            "fire_aspect", "looting", "sweeping_edge", "unbreaking", "mending");
    private static final List<String> PICK_KEYS = List.of("efficiency", "fortune", "silk_touch", "unbreaking", "mending");
    private static final List<String> TRIDENT_KEYS = List.of("loyalty", "riptide", "channeling", "impaling",
            "unbreaking", "mending");
    private static final List<String> MACE_KEYS = List.of("density", "breach", "wind_burst", "smite",
            "bane_of_arthropods", "fire_aspect", "unbreaking", "mending");

    private static final int Q_MINUS64 = 10;
    private static final int Q_MINUS10 = 11;
    private static final int Q_MINUS1 = 12;
    private static final int Q_ITEM = 13;
    private static final int Q_PLUS1 = 14;
    private static final int Q_PLUS10 = 15;
    private static final int Q_PLUS64 = 16;
    private static final int Q_CANCEL = 18;
    private static final int Q_CONFIRM = 22;

    private final LrShop plugin;

    public Gui(LrShop plugin) {
        this.plugin = plugin;
    }

    private ItemStack simple(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(name));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.setAttributeModifiers(HashMultimap.<Attribute, AttributeModifier>create());
            if (lore != null && !lore.isEmpty()) {
                List<net.kyori.adventure.text.Component> l = new ArrayList<>();
                for (String s : lore) {
                    l.add(Text.c(s));
                }
                meta.lore(l);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    // ---------------------------------------------------------------- menus

    public void openMain(Player p) {
        ShopManager shop = plugin.shop();
        ShopHolder h = new ShopHolder(null, 0);
        Inventory inv = Bukkit.createInventory(h, shop.menuSize(), Text.c(shop.menuTitle()));
        h.inv = inv;
        if (shop.filler()) {
            ItemStack f = simple(shop.fillerMaterial(), " ", null);
            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(i, f);
            }
        }
        for (ShopManager.Category c : shop.categories()) {
            if (c.slot < 0 || c.slot >= inv.getSize()) {
                continue;
            }
            List<String> lore = new ArrayList<>();
            for (String l : c.lore) {
                lore.add(l);
            }
            lore.add("");
            lore.add(plugin.msg().get("gui.category-hint"));
            inv.setItem(c.slot, simple(c.icon, c.name, lore));
            h.cats.put(c.slot, c);
        }
        p.openInventory(inv);
    }

    public void openCategory(Player p, ShopManager.Category cat, int page) {
        Msg msg = plugin.msg();
        EconomyBridge eco = plugin.eco();
        int pages = Math.max(1, (int) Math.ceil(cat.items.size() / (double) PER_PAGE));
        page = Math.max(0, Math.min(page, pages - 1));
        ShopHolder h = new ShopHolder(cat, page);
        Inventory inv = Bukkit.createInventory(h, 54, Text.c(msg.get("gui.category-title",
                "category", cat.name, "page", page + 1, "pages", pages)));
        h.inv = inv;

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < cat.items.size(); i++) {
            ShopManager.ShopItem si = cat.items.get(start + i);
            ItemStack it = si.createStack(1);
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                if (si.buyable()) {
                    lore.add(Text.c(msg.get("gui.buy-line", "price", eco.format(si.currency(), si.buy()))));
                }
                if (si.sellable()) {
                    lore.add(Text.c(msg.get("gui.sell-line", "price", eco.format(si.currency(), si.sell()))));
                }
                lore.add(Text.c(""));
                if (si.buyable()) {
                    lore.add(Text.c(msg.get("gui.hint-buy")));
                }
                if (si.sellable()) {
                    lore.add(Text.c(msg.get("gui.hint-sell")));
                    lore.add(Text.c(msg.get("gui.hint-sell-shift")));
                }
                meta.lore(lore);
                it.setItemMeta(meta);
            }
            inv.setItem(i, it);
            h.items.put(i, si);
        }

        ItemStack filler = simple(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = PER_PAGE; i < 54; i++) {
            inv.setItem(i, filler);
        }
        if (page > 0) {
            inv.setItem(SLOT_PREV, simple(Material.ARROW, msg.get("gui.previous"), null));
        }
        inv.setItem(SLOT_BACK, simple(Material.BARRIER, msg.get("gui.back"), null));
        if (page < pages - 1) {
            inv.setItem(SLOT_NEXT, simple(Material.ARROW, msg.get("gui.next"), null));
        }
        p.openInventory(inv);
    }

    public void openQuantity(Player p, ShopManager.ShopItem si, ShopManager.Category cat, int page) {
        QuantityHolder h = new QuantityHolder(si, cat, page);
        Inventory inv = Bukkit.createInventory(h, 27, Text.c(plugin.msg().get("gui.qty-title",
                "item", Text.pretty(si.material()))));
        h.inv = inv;
        renderQuantity(h);
        p.openInventory(inv);
    }

    private int maxQty() {
        return Math.max(1, plugin.getConfig().getInt("gui.max-quantity", 2304));
    }

    private void renderQuantity(QuantityHolder h) {
        Msg msg = plugin.msg();
        EconomyBridge eco = plugin.eco();
        Inventory inv = h.inv;
        ItemStack filler = simple(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        double total = Text.round2(h.item.buy() * h.qty);
        ItemStack center = h.item.createStack(Math.max(1, Math.min(h.qty, h.item.material().getMaxStackSize())));
        ItemMeta cm = center.getItemMeta();
        if (cm != null) {
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Text.c(msg.get("gui.qty-info-amount", "amount", h.qty)));
            lore.add(Text.c(msg.get("gui.qty-info-total", "price", eco.format(h.item.currency(), total))));
            cm.lore(lore);
            center.setItemMeta(cm);
        }
        inv.setItem(Q_ITEM, center);

        inv.setItem(Q_MINUS64, glass(Material.RED_STAINED_GLASS_PANE, "gui.qty-minus", 64));
        inv.setItem(Q_MINUS10, glass(Material.RED_STAINED_GLASS_PANE, "gui.qty-minus", 10));
        inv.setItem(Q_MINUS1, glass(Material.RED_STAINED_GLASS_PANE, "gui.qty-minus", 1));
        inv.setItem(Q_PLUS1, glass(Material.LIME_STAINED_GLASS_PANE, "gui.qty-plus", 1));
        inv.setItem(Q_PLUS10, glass(Material.LIME_STAINED_GLASS_PANE, "gui.qty-plus", 10));
        inv.setItem(Q_PLUS64, glass(Material.LIME_STAINED_GLASS_PANE, "gui.qty-plus", 64));

        List<String> info = new ArrayList<>();
        info.add(msg.get("gui.qty-info-amount", "amount", h.qty));
        info.add(msg.get("gui.qty-info-total", "price", eco.format(h.item.currency(), total)));
        inv.setItem(Q_CONFIRM, simple(Material.EMERALD, msg.get("gui.qty-confirm"), info));
        inv.setItem(Q_CANCEL, simple(Material.BARRIER, msg.get("gui.qty-cancel"), null));
    }

    private ItemStack glass(Material m, String key, int amount) {
        ItemStack it = simple(m, plugin.msg().get(key, "amount", amount), null);
        it.setAmount(Math.max(1, Math.min(64, amount)));
        return it;
    }

    private void clickQuantity(Player p, QuantityHolder h, int slot) {
        int delta = switch (slot) {
            case Q_MINUS64 -> -64;
            case Q_MINUS10 -> -10;
            case Q_MINUS1 -> -1;
            case Q_PLUS1 -> 1;
            case Q_PLUS10 -> 10;
            case Q_PLUS64 -> 64;
            default -> 0;
        };
        if (delta != 0) {
            int max = h.item.material().getMaxStackSize() == 1 ? Math.min(36, maxQty()) : maxQty();
            int nq = Math.max(1, Math.min(max, h.qty + delta));
            if (h.qty + delta > max) {
                plugin.msg().send(p, "gui.qty-max", "max", max);
            }
            h.qty = nq;
            renderQuantity(h);
            return;
        }
        if (slot == Q_CONFIRM) {
            plugin.sell().buy(p, h.item, h.qty);
            if (h.category != null) {
                openCategory(p, h.category, h.page);
            } else {
                p.closeInventory();
            }
        } else if (slot == Q_CANCEL) {
            if (h.category != null) {
                openCategory(p, h.category, h.page);
            } else {
                p.closeInventory();
            }
        }
    }

    public void open(Player p, ShopManager.Category c) {
        if ("random-armor".equals(c.type)) {
            openArmor(p, c);
        } else {
            openCategory(p, c, 0);
        }
    }

    // ---------------------------------------------------------------- random armor

    private Enchantment ench(String key) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
    }

    private Offer rollOffer(Material m, double base, List<String> keys, double min, double max,
                            int noChance, int maxEnch, ThreadLocalRandom rnd) {
        List<String> pool = new ArrayList<>(keys);
        Collections.shuffle(pool, rnd);
        int wanted = rnd.nextInt(100) < noChance ? 0 : 1 + rnd.nextInt(maxEnch);
        ItemStack give = new ItemStack(m);
        ItemMeta meta = give.getItemMeta();
        List<Enchantment> chosen = new ArrayList<>();
        for (String k : pool) {
            if (chosen.size() >= wanted) {
                break;
            }
            Enchantment e = ench(k);
            if (e == null) {
                continue;
            }
            boolean conflict = false;
            for (Enchantment x : chosen) {
                if (e.conflictsWith(x)) {
                    conflict = true;
                    break;
                }
            }
            if (conflict) {
                continue;
            }
            meta.addEnchant(e, 1 + rnd.nextInt(Math.max(1, e.getMaxLevel())), true);
            chosen.add(e);
        }
        give.setItemMeta(meta);
        int n = chosen.size();
        double extra = n >= 4 ? 3 : n == 3 ? 2 : n == 2 ? 1 : 0;
        double price = Text.round2(Math.max(min, Math.min(max, base + extra)));
        return new Offer(give, price);
    }

    private static double tierPrice(ConfigurationSection s, String key, double def) {
        return s == null ? def : s.getDouble("tier-prices." + key, def);
    }

    private static int slotAt(int row, int col) {
        return row * 9 + 1 + col;
    }

    private void rollOffers(ArmorHolder h) {
        ConfigurationSection s = h.category.settings;
        double min = s == null ? 2 : s.getDouble("min-price", 2);
        double max = s == null ? 7 : s.getDouble("max-price", 7);
        int noChance = s == null ? 25 : s.getInt("no-enchant-chance", 25);
        int maxEnch = Math.max(1, s == null ? 4 : s.getInt("max-enchants", 4));
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        h.offers.clear();

        // row 0: swords (+ trident), row 1: pickaxes (+ mace)
        for (int c = 0; c < TOOL_TIERS.length; c++) {
            String tier = TOOL_TIERS[c];
            double base = tierPrice(s, tier.toLowerCase(java.util.Locale.ROOT), ARMOR_BASE[c]);
            h.offers.put(slotAt(0, c), rollOffer(Material.valueOf(tier + "_SWORD"), base, SWORD_KEYS,
                    min, max, noChance, maxEnch, rnd));
            h.offers.put(slotAt(1, c), rollOffer(Material.valueOf(tier + "_PICKAXE"), base, PICK_KEYS,
                    min, max, noChance, maxEnch, rnd));
        }
        h.offers.put(slotAt(0, 6), rollOffer(Material.TRIDENT, tierPrice(s, "trident", 4), TRIDENT_KEYS,
                min, max, noChance, maxEnch, rnd));
        h.offers.put(slotAt(1, 6), rollOffer(Material.MACE, tierPrice(s, "mace", 5), MACE_KEYS,
                min, max, noChance, maxEnch, rnd));

        // rows 2-5: armor
        for (int r = 0; r < ARMOR_PIECES.length; r++) {
            for (int c = 0; c < ARMOR_TIERS.length; c++) {
                Material m = Material.valueOf(ARMOR_TIERS[c] + "_" + ARMOR_PIECES[r]);
                double base = tierPrice(s, ARMOR_TIERS[c].toLowerCase(java.util.Locale.ROOT), ARMOR_BASE[c]);
                List<String> keys = new ArrayList<>(List.of("protection", "fire_protection", "blast_protection",
                        "projectile_protection", "thorns"));
                keys.addAll(COMMON_KEYS);
                switch (ARMOR_PIECES[r]) {
                    case "HELMET" -> keys.addAll(List.of("respiration", "aqua_affinity"));
                    case "LEGGINGS" -> keys.add("swift_sneak");
                    case "BOOTS" -> keys.addAll(List.of("feather_falling", "depth_strider", "soul_speed", "frost_walker"));
                    default -> {
                    }
                }
                h.offers.put(slotAt(2 + r, c), rollOffer(m, base, keys, min, max, noChance, maxEnch, rnd));
            }
        }
    }

    private void renderArmor(Player p, ArmorHolder h) {
        Msg msg = plugin.msg();
        EconomyBridge eco = plugin.eco();
        String cur = h.category.currency;
        Inventory inv = h.inv;
        inv.clear();

        ItemStack yellow = simple(Material.YELLOW_STAINED_GLASS_PANE, " ", null);
        for (int s : A_DECOR) {
            inv.setItem(s, yellow);
        }

        for (Map.Entry<Integer, Offer> en : h.offers.entrySet()) {
            Offer o = en.getValue();
            ItemStack show = o.give().clone();
            ItemMeta meta = show.getItemMeta();
            if (meta != null) {
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Text.c(""));
                lore.add(Text.c(msg.get("gui.buy-line", "price", eco.format(cur, o.price()))));
                lore.add(Text.c(msg.get("gui.armor-hint-buy")));
                meta.lore(lore);
                show.setItemMeta(meta);
            }
            inv.setItem(en.getKey(), show);
        }

        double bal = eco.balance(p.getUniqueId(), cur);
        inv.setItem(A_BALANCE, simple(Material.EMERALD,
                msg.get("gui.armor-balance", "balance", eco.format(cur, bal)), null));
        inv.setItem(A_BACK, simple(Material.BARRIER, msg.get("gui.back"), null));

        double cost = h.category.settings == null ? 0 : h.category.settings.getDouble("reroll-cost", 0);
        List<String> rl = new ArrayList<>();
        rl.add(cost > 0 ? msg.get("gui.armor-reroll-cost", "price", eco.format(cur, cost))
                : msg.get("gui.armor-reroll-free"));
        inv.setItem(A_REROLL, simple(Material.GOLD_INGOT, msg.get("gui.armor-reroll"), rl));
    }

    public void openArmor(Player p, ShopManager.Category cat) {
        ArmorHolder h = new ArmorHolder(cat);
        Inventory inv = Bukkit.createInventory(h, 54, Text.c(plugin.msg().get("gui.armor-title")));
        h.inv = inv;
        rollOffers(h);
        renderArmor(p, h);
        p.openInventory(inv);
    }

    private void clickArmor(Player p, ArmorHolder h, int slot) {
        if (slot == A_BACK) {
            openMain(p);
            return;
        }
        if (slot == A_REROLL) {
            double cost = h.category.settings == null ? 0 : h.category.settings.getDouble("reroll-cost", 0);
            if (cost > 0 && !plugin.eco().withdraw(p.getUniqueId(), h.category.currency, cost)) {
                plugin.msg().send(p, "not-enough-money", "price", plugin.eco().format(h.category.currency, cost));
                return;
            }
            rollOffers(h);
            renderArmor(p, h);
            return;
        }
        Offer o = h.offers.get(slot);
        if (o == null) {
            return;
        }
        if (plugin.sell().buyStack(p, o.give(), o.price(), h.category.currency)) {
            renderArmor(p, h);
        }
    }

    public void openSellGui(Player p) {
        SellHolder h = new SellHolder();
        Inventory inv = Bukkit.createInventory(h, 54, Text.c(plugin.msg().get("gui.sellgui-title")));
        h.inv = inv;
        p.openInventory(inv);
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof ArmorHolder ah) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player ap && e.getClickedInventory() == top) {
                clickArmor(ap, ah, e.getRawSlot());
            }
            return;
        }
        if (top.getHolder() instanceof QuantityHolder qh) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player qp && e.getClickedInventory() == top) {
                clickQuantity(qp, qh, e.getRawSlot());
            }
            return;
        }
        if (!(top.getHolder() instanceof ShopHolder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (e.getClickedInventory() == null || e.getClickedInventory() != top) {
            return;
        }
        int slot = e.getRawSlot();

        if (h.category == null) {
            ShopManager.Category c = h.cats.get(slot);
            if (c != null) {
                open(p, c);
            }
            return;
        }

        if (slot == SLOT_PREV && h.page > 0) {
            openCategory(p, h.category, h.page - 1);
            return;
        }
        if (slot == SLOT_NEXT) {
            openCategory(p, h.category, h.page + 1);
            return;
        }
        if (slot == SLOT_BACK) {
            openMain(p);
            return;
        }

        ShopManager.ShopItem si = h.items.get(slot);
        if (si == null) {
            return;
        }
        ClickType t = e.getClick();
        switch (t) {
            case LEFT, SHIFT_LEFT -> {
                if (si.buyable()) {
                    openQuantity(p, si, h.category, h.page);
                } else {
                    plugin.msg().send(p, "not-buyable");
                }
            }
            case RIGHT -> plugin.sell().sellMaterial(p, si, 1);
            case SHIFT_RIGHT -> plugin.sell().sellMaterial(p, si, 0);
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder th = e.getView().getTopInventory().getHolder();
        if (th instanceof ShopHolder || th instanceof QuantityHolder || th instanceof ArmorHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof SellHolder && e.getPlayer() instanceof Player p) {
            plugin.sell().sellGui(p, e.getInventory());
        }
    }
}
