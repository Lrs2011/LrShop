package dev.lrs.lrshop;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Commands implements CommandExecutor, TabCompleter {

    private final LrShop plugin;

    public Commands(LrShop plugin) {
        this.plugin = plugin;
    }

    private boolean perm(CommandSender s, String node) {
        if (s.hasPermission(node)) {
            return true;
        }
        plugin.msg().send(s, "no-permission");
        return false;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) {
            return p;
        }
        plugin.msg().send(s, "player-only");
        return null;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        Msg msg = plugin.msg();
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "shop" -> {
                if (!perm(s, "lrshop.shop")) {
                    return true;
                }
                Player p = player(s);
                if (p == null) {
                    return true;
                }
                if (a.length >= 1) {
                    ShopManager.Category c = plugin.shop().category(a[0]);
                    if (c == null) {
                        msg.send(s, "unknown-category", "category", a[0]);
                        return true;
                    }
                    plugin.gui().open(p, c);
                } else {
                    plugin.gui().openMain(p);
                }
            }
            case "sell" -> {
                if (!perm(s, "lrshop.sell")) {
                    return true;
                }
                Player p = player(s);
                if (p == null) {
                    return true;
                }
                if (a.length == 0) {
                    plugin.sell().sellHand(p, 0);
                    return true;
                }
                String sub = a[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "hand" -> {
                        int amount = 0;
                        if (a.length >= 2) {
                            amount = parseInt(a[1]);
                            if (amount <= 0) {
                                msg.send(s, "usage-sell");
                                return true;
                            }
                        }
                        plugin.sell().sellHand(p, amount);
                    }
                    case "all" -> {
                        if (perm(s, "lrshop.sellall")) {
                            plugin.sell().sellAll(p);
                        }
                    }
                    case "gui" -> {
                        if (perm(s, "lrshop.sellgui")) {
                            plugin.gui().openSellGui(p);
                        }
                    }
                    default -> {
                        int amount = parseInt(sub);
                        if (amount > 0) {
                            plugin.sell().sellHand(p, amount);
                        } else {
                            msg.send(s, "usage-sell");
                        }
                    }
                }
            }
            case "sellall" -> {
                if (!perm(s, "lrshop.sellall")) {
                    return true;
                }
                Player p = player(s);
                if (p != null) {
                    plugin.sell().sellAll(p);
                }
            }
            case "sellgui" -> {
                if (!perm(s, "lrshop.sellgui")) {
                    return true;
                }
                Player p = player(s);
                if (p != null) {
                    plugin.gui().openSellGui(p);
                }
            }
            case "worth" -> {
                if (!perm(s, "lrshop.worth")) {
                    return true;
                }
                Player p = player(s);
                if (p == null) {
                    return true;
                }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    msg.send(s, "hold-item");
                    return true;
                }
                Material m = hand.getType();
                ShopManager.ShopItem b = plugin.shop().buyEntry(m);
                ShopManager.ShopItem sl = plugin.shop().sellEntry(m);
                String none = msg.get("worth-none");
                msg.send(s, "worth", "item", Text.pretty(m),
                        "buy", b == null ? none : plugin.eco().format(b.currency(), b.buy()),
                        "sell", sl == null ? none : plugin.eco().format(sl.currency(), sl.sell()));
            }
            case "lrshop" -> {
                if (a.length >= 1 && a[0].equalsIgnoreCase("reload")) {
                    if (!perm(s, "lrshop.admin")) {
                        return true;
                    }
                    plugin.reloadAll();
                    msg.send(s, "reloaded");
                } else {
                    msg.send(s, "usage-lrshop");
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (a.length != 1) {
            return out;
        }
        String prefix = a[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        switch (name) {
            case "shop" -> plugin.shop().categories().forEach(c -> options.add(c.id));
            case "sell" -> {
                options.add("hand");
                options.add("all");
                options.add("gui");
            }
            case "lrshop" -> options.add("reload");
            default -> {
            }
        }
        for (String o : options) {
            if (o.startsWith(prefix)) {
                out.add(o);
            }
        }
        return out;
    }
}
