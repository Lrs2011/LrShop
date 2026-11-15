package dev.lrs.lrshop;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Msg {

    private final LrShop plugin;
    private YamlConfiguration cfg = new YamlConfiguration();

    public Msg(LrShop plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        cfg = YamlConfiguration.loadConfiguration(f);
        InputStream in = plugin.getResource("messages.yml");
        if (in != null) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    private String replace(String s, Object... kv) {
        s = s.replace("{prefix}", cfg.getString("prefix", ""));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return s;
    }

    public String get(String key, Object... kv) {
        return Text.color(replace(cfg.getString(key, key), kv));
    }

    public List<String> list(String key, Object... kv) {
        List<String> out = new ArrayList<>();
        for (String s : cfg.getStringList(key)) {
            out.add(Text.color(replace(s, kv)));
        }
        return out;
    }

    public void send(CommandSender to, String key, Object... kv) {
        to.sendMessage(get(key, kv));
    }
}
