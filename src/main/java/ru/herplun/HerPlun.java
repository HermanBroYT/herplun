package ru.herplun;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * HerPlun 1.2 — работает на Spigot/Paper 1.12.2 и новее.
 */
public class HerPlun extends JavaPlugin {

    static final String DEF_PREFIX = "&c[HerPlun] &f";
    static final String DEF_NO_PERM = "&c\"HerPlun\" У вас нет прав что прописать команду!";

    private Store store;
    private final Map<UUID, Check> checks = new HashMap<>();

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
        } catch (Exception ex) {
            getLogger().warning("Не удалось создать config.yml: " + ex.getMessage());
        }
        store = new Store(this);
        store.loadAll();

        Commands commands = new Commands(this);
        String[] names = {"ban", "unban", "timeban", "mute", "unmute", "help", "report",
                "check", "voice", "gm", "gamemode", "herplun"};
        for (String n : names) {
            PluginCommand c = getCommand(n);
            if (c != null) {
                c.setExecutor(commands);
                c.setTabCompleter(commands);
            }
        }

        Bukkit.getPluginManager().registerEvents(new EventListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AntiCheat(this), this);
        Bukkit.getPluginManager().registerEvents(new ReportMenu.Events(), this);

        // Раз в секунду проверяем, не истекло ли время проверки на читы
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Check ch : new ArrayList<>(checks.values())) {
                    if (now >= ch.endTime) {
                        checks.remove(ch.target);
                        ban(ch.targetName, "Время проверки на читы истекло", "HerPlun", 0);
                        notifyStaff(prefix() + "Игрок §e" + ch.targetName
                                + " §fзабанен: время проверки истекло.", "herplun.check.on");
                    }
                }
            }
        }, 20L, 20L);

        getLogger().info("HerPlun " + getDescription().getVersion() + " включён!");
    }

    @Override
    public void onDisable() {
        if (store != null) store.saveAll();
    }

    // ---------------------------------------------------------------- доступ

    Store store() {
        return store;
    }

    Map<UUID, Check> checks() {
        return checks;
    }

    // ---------------------------------------------------------------- конфиг

    String cfg(String path, String def) {
        String v = null;
        try {
            v = getConfig().getString(path, def);
        } catch (Exception ignored) {
        }
        return Util.color(v == null ? def : v);
    }

    int cfgInt(String path, int def) {
        try {
            return getConfig().getInt(path, def);
        } catch (Exception ex) {
            return def;
        }
    }

    double cfgDouble(String path, double def) {
        try {
            return getConfig().getDouble(path, def);
        } catch (Exception ex) {
            return def;
        }
    }

    boolean cfgBool(String path, boolean def) {
        try {
            return getConfig().getBoolean(path, def);
        } catch (Exception ex) {
            return def;
        }
    }

    String prefix() {
        return cfg("prefix", DEF_PREFIX);
    }

    // ---------------------------------------------------------------- сообщения

    void say(CommandSender s, String text) {
        s.sendMessage(prefix() + text);
    }

    boolean noPerm(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return false;
        s.sendMessage(cfg("messages.no-permission", DEF_NO_PERM));
        return true;
    }

    /** Сообщение всем онлайн-игрокам с правом + в консоль. */
    void notifyStaff(String msg, String perm) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(perm)) p.sendMessage(msg);
        }
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    String muteText(Store.MuteRecord m) {
        if (m == null) return "";
        if (m.until <= 0) {
            return cfg("messages.muted-permanent", "&cВы замучены навсегда и не можете писать в чат.");
        }
        return cfg("messages.muted-temp", "&cВы замучены. Осталось: &e{time}")
                .replace("{time}", Util.formatMs(m.until - System.currentTimeMillis()));
    }

    // ---------------------------------------------------------------- баны

    String realName(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return online.getName();
        OfflinePlayer off = Bukkit.getOfflinePlayer(input);
        return off.getName() != null ? off.getName() : input;
    }

    /** until = 0 — навсегда. Вызывать только из основного потока. */
    void ban(String name, String reason, String by, long until) {
        Date d = until > 0 ? new Date(until) : null;
        Bukkit.getBanList(BanList.Type.NAME).addBan(name, reason, d, by);
        store.bans.put(name.toLowerCase(Locale.ROOT),
                new Store.BanRecord(name, reason, by, System.currentTimeMillis(), until));
        store.saveBans();
        kick(name, until > 0
                ? "§cВы забанены до §e" + d + "\n§cПричина: §f" + reason
                : "§cВы забанены навсегда.\n§cПричина: §f" + reason);
    }

    void kick(String name, final String msg) {
        final Player p = Bukkit.getPlayerExact(name);
        if (p != null) {
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    p.kickPlayer(msg);
                }
            });
        }
    }
}
