package dev.lrs.lrshop;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Talks to LrEconomy through reflection so no LrEconomy jar is needed to compile.
 */
public final class EconomyBridge {

    private final LrShop plugin;
    private Object economy;
    private Method getCurrency;
    private Method getBalance;
    private Method deposit;
    private Method withdraw;

    public EconomyBridge(LrShop plugin) {
        this.plugin = plugin;
    }

    public boolean hook() {
        Plugin p = Bukkit.getPluginManager().getPlugin("LrEconomy");
        if (p == null) {
            return false;
        }
        try {
            economy = p.getClass().getMethod("economy").invoke(p);
            Class<?> ec = economy.getClass();
            getCurrency = ec.getMethod("getCurrency", String.class);
            for (Method m : ec.getMethods()) {
                String n = m.getName();
                int pc = m.getParameterCount();
                if (n.equals("getBalance") && pc == 2) {
                    getBalance = m;
                } else if (n.equals("deposit") && pc == 3) {
                    deposit = m;
                } else if (n.equals("withdraw") && pc == 3) {
                    withdraw = m;
                }
            }
            return getBalance != null && deposit != null && withdraw != null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not hook LrEconomy", e);
            return false;
        }
    }

    private Object currency(String id) {
        try {
            return getCurrency.invoke(economy, id);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean exists(String id) {
        return currency(id) != null;
    }

    public double balance(UUID uuid, String id) {
        Object c = currency(id);
        if (c == null) {
            return 0.0;
        }
        try {
            return (Double) getBalance.invoke(economy, uuid, c);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public boolean deposit(UUID uuid, String id, double amount) {
        Object c = currency(id);
        if (c == null) {
            return false;
        }
        try {
            return (Boolean) deposit.invoke(economy, uuid, c, amount);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Deposit failed", e);
            return false;
        }
    }

    public boolean withdraw(UUID uuid, String id, double amount) {
        Object c = currency(id);
        if (c == null) {
            return false;
        }
        try {
            return (Boolean) withdraw.invoke(economy, uuid, c, amount);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Withdraw failed", e);
            return false;
        }
    }

    /** Deposits several currencies; rolls back if one fails. */
    public boolean depositAll(UUID uuid, Map<String, Double> totals) {
        List<Map.Entry<String, Double>> done = new ArrayList<>();
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            if (deposit(uuid, e.getKey(), e.getValue())) {
                done.add(e);
            } else {
                for (Map.Entry<String, Double> d : done) {
                    withdraw(uuid, d.getKey(), d.getValue());
                }
                return false;
            }
        }
        return true;
    }

    public String format(String id, double amount) {
        String fmt = plugin.getConfig().getString("formats." + id, "{amount}");
        return Text.color(fmt.replace("{amount}", Text.num(amount)));
    }

    public String formatTotals(Map<String, Double> totals) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            parts.add(format(e.getKey(), e.getValue()));
        }
        return String.join("&7, &f", parts);
    }
}
