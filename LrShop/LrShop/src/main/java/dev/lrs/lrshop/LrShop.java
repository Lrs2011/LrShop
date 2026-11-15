package dev.lrs.lrshop;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class LrShop extends JavaPlugin {

    private Msg msg;
    private EconomyBridge eco;
    private ShopManager shop;
    private SellService sell;
    private Gui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "shop.yml").exists()) {
            saveResource("shop.yml", false);
        }
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }

        msg = new Msg(this);
        msg.load();

        eco = new EconomyBridge(this);
        if (!eco.hook()) {
            getLogger().severe("LrEconomy not found or incompatible. Disabling LrShop.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        shop = new ShopManager(this);
        shop.load();
        sell = new SellService(this);
        gui = new Gui(this);
        getServer().getPluginManager().registerEvents(gui, this);

        Commands cmds = new Commands(this);
        for (String n : new String[]{"shop", "sell", "sellall", "sellgui", "worth", "lrshop"}) {
            PluginCommand pc = getCommand(n);
            if (pc != null) {
                pc.setExecutor(cmds);
                pc.setTabCompleter(cmds);
            }
        }
    }

    @Override
    public void onDisable() {
        if (gui == null) {
            return;
        }
        for (Player p : getServer().getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Gui.SellHolder) {
                p.closeInventory();
            }
        }
    }

    public void reloadAll() {
        reloadConfig();
        msg.load();
        shop.load();
    }

    public Msg msg() {
        return msg;
    }

    public EconomyBridge eco() {
        return eco;
    }

    public ShopManager shop() {
        return shop;
    }

    public SellService sell() {
        return sell;
    }

    public Gui gui() {
        return gui;
    }
}
