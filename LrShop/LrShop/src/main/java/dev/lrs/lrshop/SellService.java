package dev.lrs.lrshop;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SellService {

    private final LrShop plugin;

    public SellService(LrShop plugin) {
        this.plugin = plugin;
    }

    /** Returns the sell entry for the stack or null if it cannot be sold. */
    public ShopManager.ShopItem entry(ItemStack s) {
        if (s == null || s.getType().isAir()) {
            return null;
        }
        if (plugin.getConfig().getBoolean("sell.only-plain-items", true) && s.hasItemMeta()) {
            return null;
        }
        ShopManager.ShopItem si = plugin.shop().sellEntry(s.getType());
        return si != null && si.sellable() ? si : null;
    }

    private static void addTotal(Map<String, Double> totals, String cur, double v) {
        totals.merge(cur, v, Double::sum);
    }

    private static void roundTotals(Map<String, Double> totals) {
        totals.replaceAll((k, v) -> Text.round2(v));
    }

    // ---------------------------------------------------------------- buy

    private boolean hasSpace(Player p, Material mat, int amount) {
        int max = mat.getMaxStackSize();
        int free = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s == null || s.getType().isAir()) {
                free += max;
            } else if (s.getType() == mat && !s.hasItemMeta() && s.getAmount() < max) {
                free += max - s.getAmount();
            }
            if (free >= amount) {
                return true;
            }
        }
        return free >= amount;
    }

    public void buy(Player p, ShopManager.ShopItem si, int amount) {
        Msg msg = plugin.msg();
        EconomyBridge eco = plugin.eco();
        if (!si.buyable()) {
            msg.send(p, "not-buyable");
            return;
        }
        if (!eco.exists(si.currency())) {
            msg.send(p, "currency-missing", "currency", si.currency());
            return;
        }
        if (!hasSpace(p, si.material(), amount)) {
            msg.send(p, "no-space");
            return;
        }
        double cost = Text.round2(si.buy() * amount);
        if (!eco.withdraw(p.getUniqueId(), si.currency(), cost)) {
            msg.send(p, "not-enough-money", "price", eco.format(si.currency(), cost));
            return;
        }
        int max = si.material().getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, max);
            giveBack(p, si.createStack(n));
            left -= n;
        }
        msg.send(p, "bought", "amount", amount, "item", Text.pretty(si.material()),
                "price", eco.format(si.currency(), cost));
    }

    /** Buys one exact ItemStack (used by the random armor shop). */
    public boolean buyStack(Player p, ItemStack stack, double price, String currency) {
        Msg msg = plugin.msg();
        EconomyBridge eco = plugin.eco();
        if (!eco.exists(currency)) {
            msg.send(p, "currency-missing", "currency", currency);
            return false;
        }
        boolean space = false;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s == null || s.getType().isAir()) {
                space = true;
                break;
            }
        }
        if (!space) {
            msg.send(p, "no-space");
            return false;
        }
        double cost = Text.round2(price);
        if (!eco.withdraw(p.getUniqueId(), currency, cost)) {
            msg.send(p, "not-enough-money", "price", eco.format(currency, cost));
            return false;
        }
        giveBack(p, stack.clone());
        msg.send(p, "bought", "amount", stack.getAmount(), "item", Text.pretty(stack.getType()),
                "price", eco.format(currency, cost));
        return true;
    }

    // ---------------------------------------------------------------- sell

    /** amount <= 0 means the whole stack. */
    public void sellHand(Player p, int amount) {
        Msg msg = plugin.msg();
        EconomyBridge eco = plugin.eco();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            msg.send(p, "hold-item");
            return;
        }
        ShopManager.ShopItem si = entry(hand);
        if (si == null) {
            msg.send(p, "not-sellable");
            return;
        }
        int n = amount <= 0 ? hand.getAmount() : Math.min(amount, hand.getAmount());
        double total = Text.round2(si.sell() * n);
        if (!eco.deposit(p.getUniqueId(), si.currency(), total)) {
            msg.send(p, "deposit-failed");
            return;
        }
        int left = hand.getAmount() - n;
        if (left <= 0) {
            p.getInventory().setItemInMainHand(null);
        } else {
            ItemStack nh = hand.clone();
            nh.setAmount(left);
            p.getInventory().setItemInMainHand(nh);
        }
        msg.send(p, "sold-item", "amount", n, "item", Text.pretty(si.material()),
                "total", eco.format(si.currency(), total));
    }

    /** Sells items of one material from the player's inventory. limit <= 0 means all. */
    public void sellMaterial(Player p, ShopManager.ShopItem shopItem, int limit) {
        Msg msg = plugin.msg();
        EconomyBridge eco = plugin.eco();
        ShopManager.ShopItem si = plugin.shop().sellEntry(shopItem.material());
        if (si == null || !si.sellable()) {
            msg.send(p, "not-sellable");
            return;
        }
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getStorageContents();
        int remaining = limit <= 0 ? Integer.MAX_VALUE : limit;
        int sold = 0;
        int[] take = new int[c.length];
        for (int i = 0; i < c.length && remaining > 0; i++) {
            ItemStack s = c[i];
            ShopManager.ShopItem e = entry(s);
            if (e == null || s.getType() != si.material()) {
                continue;
            }
            int n = Math.min(remaining, s.getAmount());
            take[i] = n;
            sold += n;
            remaining -= n;
        }
        if (sold == 0) {
            msg.send(p, "nothing-to-sell");
            return;
        }
        double total = Text.round2(si.sell() * sold);
        if (!eco.deposit(p.getUniqueId(), si.currency(), total)) {
            msg.send(p, "deposit-failed");
            return;
        }
        for (int i = 0; i < c.length; i++) {
            if (take[i] > 0) {
                int left = c[i].getAmount() - take[i];
                if (left <= 0) {
                    inv.setItem(i, null);
                } else {
                    ItemStack ns = c[i].clone();
                    ns.setAmount(left);
                    inv.setItem(i, ns);
                }
            }
        }
        msg.send(p, "sold-item", "amount", sold, "item", Text.pretty(si.material()),
                "total", eco.format(si.currency(), total));
    }

    public void sellAll(Player p) {
        Msg msg = plugin.msg();
        EconomyBridge eco = plugin.eco();
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getStorageContents();
        Map<String, Double> totals = new LinkedHashMap<>();
        boolean[] mark = new boolean[c.length];
        int count = 0;
        for (int i = 0; i < c.length; i++) {
            ShopManager.ShopItem si = entry(c[i]);
            if (si == null) {
                continue;
            }
            addTotal(totals, si.currency(), si.sell() * c[i].getAmount());
            count += c[i].getAmount();
            mark[i] = true;
        }
        if (count == 0) {
            msg.send(p, "nothing-to-sell");
            return;
        }
        roundTotals(totals);
        if (!eco.depositAll(p.getUniqueId(), totals)) {
            msg.send(p, "deposit-failed");
            return;
        }
        for (int i = 0; i < c.length; i++) {
            if (mark[i]) {
                inv.setItem(i, null);
            }
        }
        msg.send(p, "sold", "items", count, "total", eco.formatTotals(totals));
    }

    /** Called when the sell GUI closes: sells what it can, returns the rest. */
    public void sellGui(Player p, Inventory gui) {
        Msg msg = plugin.msg();
        EconomyBridge eco = plugin.eco();
        ItemStack[] c = gui.getContents();
        Map<String, Double> totals = new LinkedHashMap<>();
        boolean[] mark = new boolean[c.length];
        int count = 0;
        boolean any = false;
        for (int i = 0; i < c.length; i++) {
            if (c[i] == null || c[i].getType().isAir()) {
                continue;
            }
            any = true;
            ShopManager.ShopItem si = entry(c[i]);
            if (si == null) {
                continue;
            }
            addTotal(totals, si.currency(), si.sell() * c[i].getAmount());
            count += c[i].getAmount();
            mark[i] = true;
        }
        roundTotals(totals);
        boolean ok = count > 0 && eco.depositAll(p.getUniqueId(), totals);
        for (int i = 0; i < c.length; i++) {
            ItemStack s = c[i];
            if (s == null || s.getType().isAir()) {
                continue;
            }
            if (ok && mark[i]) {
                continue;
            }
            giveBack(p, s);
        }
        gui.clear();
        if (ok) {
            msg.send(p, "sold", "items", count, "total", eco.formatTotals(totals));
        } else if (count > 0) {
            msg.send(p, "deposit-failed");
        } else if (any) {
            msg.send(p, "nothing-to-sell");
        }
    }

    private void giveBack(Player p, ItemStack s) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(s);
        for (ItemStack l : left.values()) {
            p.getWorld().dropItem(p.getLocation(), l);
        }
    }
}
