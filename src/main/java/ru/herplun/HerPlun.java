package ru.herplun;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HerPlun 1.1 — работает на Spigot/Paper 1.12.2 и новее.
 */
public class HerPlun extends JavaPlugin implements Listener, TabExecutor {

    private static final String DEF_PREFIX = "&c[HerPlun] &f";
    private static final String DEF_NO_PERM = "&c\"HerPlun\" У вас нет прав что прописать команду!";
    private static final String DEF_VOICE = "https://modrinth.com/plugin/simple-voice-chat";
    private static final List<String> DEF_BLOCKED = Arrays.asList(
            "msg", "tell", "w", "whisper", "m", "t", "pm", "r", "reply", "me", "say");

    private static final Pattern TIME = Pattern.compile("(\\d+)([smhdw])");
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_.\\-]{1,36}");

    /** Активная проверка на читы. */
    private static class Check {
        final UUID target;
        final String targetName;
        final UUID moderator;
        long endTime;

        Check(UUID target, String targetName, UUID moderator, long endTime) {
            this.target = target;
            this.targetName = targetName;
            this.moderator = moderator;
            this.endTime = endTime;
        }
    }

    /** Мут. until = 0 — навсегда. */
    private static class Mute {
        final String name;
        final long until;
        final String by;

        Mute(String name, long until, String by) {
            this.name = name;
            this.until = until;
            this.by = by;
        }
    }

    private final Map<UUID, Check> checks = new HashMap<>();
    private final Map<String, Mute> mutes = new ConcurrentHashMap<>();
    private File mutesFile;

    // ---------------------------------------------------------------- старт

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
        } catch (Exception ex) {
            getLogger().warning("Не удалось создать config.yml: " + ex.getMessage());
        }
        mutesFile = new File(getDataFolder(), "mutes.yml");
        loadMutes();

        String[] cmds = {"ban", "unban", "timeban", "mute", "unmute", "help",
                "report", "check", "voice", "herplun"};
        for (String c : cmds) {
            if (getCommand(c) != null) getCommand(c).setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);

        // Раз в секунду проверяем, не истекло ли время проверки
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Check ch : new ArrayList<>(checks.values())) {
                    if (now >= ch.endTime) {
                        checks.remove(ch.target);
                        banPermanent(ch.targetName, "Время проверки на читы истекло", "HerPlun");
                        Bukkit.broadcast(prefix() + "Игрок §e" + ch.targetName
                                + " §fзабанен: время проверки истекло.", "herplun.check.on");
                    }
                }
            }
        }, 20L, 20L);

        getLogger().info("HerPlun " + getDescription().getVersion() + " включён!");
    }

    @Override
    public void onDisable() {
        saveMutes();
    }

    // ---------------------------------------------------------------- конфиг

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String cfg(String path, String def) {
        String v = null;
        try {
            v = getConfig().getString(path, def);
        } catch (Exception ignored) {
        }
        return color(v == null ? def : v);
    }

    private String prefix() {
        return cfg("prefix", DEF_PREFIX);
    }

    private void say(CommandSender s, String text) {
        s.sendMessage(prefix() + text);
    }

    private boolean noPerm(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return false;
        s.sendMessage(cfg("messages.no-permission", DEF_NO_PERM));
        return true;
    }

    // ---------------------------------------------------------------- команды

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        try {
            switch (name) {
                case "ban":     return cmdBan(s, a);
                case "unban":   return cmdUnban(s, a);
                case "timeban": return cmdTimeBan(s, a);
                case "mute":    return cmdMute(s, a);
                case "unmute":  return cmdUnmute(s, a);
                case "help":    return cmdHelp(s);
                case "report":  return cmdReport(s, a);
                case "check":   return cmdCheck(s, a);
                case "voice":   return cmdVoice(s);
                case "herplun": return cmdHerplun(s, a);
            }
        } catch (Exception ex) {
            say(s, "§cПроизошла ошибка. Смотрите консоль сервера.");
            getLogger().warning("Ошибка в команде /" + name + ": " + ex);
        }
        return false;
    }

    private boolean cmdBan(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.ban")) return true;
        if (a.length < 1) {
            say(s, "Использование: /ban <игрок> [причина]");
            return true;
        }
        String reason = a.length > 1 ? join(a, 1) : "Нарушение правил";
        String target = realName(a[0]);
        banPermanent(target, reason, s.getName());
        say(s, "Игрок §e" + target + " §fзабанен навсегда. Причина: §e" + reason);
        return true;
    }

    private boolean cmdUnban(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.unban")) return true;
        if (a.length < 1) {
            say(s, "Использование: /unban <игрок>");
            return true;
        }
        String target = realName(a[0]);
        BanList list = Bukkit.getBanList(BanList.Type.NAME);
        if (!list.isBanned(target)) {
            say(s, "Игрок §e" + target + " §fне находится в бане.");
            return true;
        }
        list.pardon(target);
        say(s, "Игрок §e" + target + " §fразбанен.");
        return true;
    }

    private boolean cmdTimeBan(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.timeban")) return true;
        if (a.length < 2) {
            say(s, "Использование: /timeban <игрок> <время> [причина]  (пример: 1d2h30m)");
            return true;
        }
        long ms = parseTime(a[1]);
        if (ms <= 0) {
            say(s, "Неверное время. Пример: §e30m§f, §e12h§f, §e7d§f, §e1d2h30m");
            return true;
        }
        String reason = a.length > 2 ? join(a, 2) : "Нарушение правил";
        String target = realName(a[0]);
        Date until = new Date(System.currentTimeMillis() + ms);
        Bukkit.getBanList(BanList.Type.NAME).addBan(target, reason, until, s.getName());
        kick(target, "§cВы забанены до §e" + until + "\n§cПричина: §f" + reason);
        say(s, "Игрок §e" + target + " §fзабанен на §e" + a[1] + "§f. Причина: §e" + reason);
        return true;
    }

    private boolean cmdMute(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.mute")) return true;
        if (a.length < 1) {
            say(s, "Использование: /mute <игрок> [время]  (без времени — навсегда)");
            return true;
        }
        String target = realName(a[0]);
        long until = 0;
        String shown = "навсегда";
        if (a.length >= 2) {
            long ms = parseTime(a[1]);
            if (ms <= 0) {
                say(s, "Неверное время. Пример: §e30m§f, §e2h§f, §e1d");
                return true;
            }
            until = System.currentTimeMillis() + ms;
            shown = "на " + a[1];
        }
        mutes.put(target.toLowerCase(Locale.ROOT), new Mute(target, until, s.getName()));
        saveMutes();
        say(s, "Игрок §e" + target + " §fзамучен §e" + shown + "§f.");
        Player p = Bukkit.getPlayerExact(target);
        if (p != null) p.sendMessage(prefix() + muteText(mutes.get(target.toLowerCase(Locale.ROOT))));
        return true;
    }

    private boolean cmdUnmute(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.unmute")) return true;
        if (a.length < 1) {
            say(s, "Использование: /unmute <игрок>");
            return true;
        }
        String target = realName(a[0]);
        if (mutes.remove(target.toLowerCase(Locale.ROOT)) == null) {
            say(s, "Игрок §e" + target + " §fне в муте.");
            return true;
        }
        saveMutes();
        say(s, "С игрока §e" + target + " §fснят мут.");
        Player p = Bukkit.getPlayerExact(target);
        if (p != null) p.sendMessage(prefix() + "§aМут снят. Вы снова можете писать в чат.");
        return true;
    }

    private boolean cmdHelp(CommandSender s) {
        if (noPerm(s, "herplun.help")) return true;
        s.sendMessage("§c§l— HerPlun " + getDescription().getVersion() + ": помощь —");
        s.sendMessage("§e/ban <игрок> [причина] §7— бан навсегда");
        s.sendMessage("§e/unban <игрок> §7— разбан");
        s.sendMessage("§e/timeban <игрок> <время> [причина] §7— временный бан");
        s.sendMessage("§e/mute <игрок> [время] §7— мут (без времени — навсегда)");
        s.sendMessage("§e/unmute <игрок> §7— снять мут");
        s.sendMessage("§e/report <игрок> <причина> §7— пожаловаться на игрока");
        s.sendMessage("§e/voice §7— ссылка на голосовой чат");
        s.sendMessage("§e/check on <игрок> <время> §7— вызвать на проверку читов");
        s.sendMessage("§e/check off [игрок] §7— завершить проверку");
        s.sendMessage("§e/check tp §7— телепорт к проверяемому");
        s.sendMessage("§e/check time <время> §7— продлить проверку");
        s.sendMessage("§e/herplun privilege set|addtemp|remove ... §7— привилегии");
        s.sendMessage("§7Время: s, m, h, d, w (например 1h30m)");
        return true;
    }

    private boolean cmdReport(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.report")) return true;
        if (a.length < 2) {
            say(s, "Использование: /report <игрок> <причина>");
            return true;
        }
        String reason = join(a, 1);
        Bukkit.broadcast(prefix() + "§eЖалоба §fот §a" + s.getName() + " §fна §c" + a[0]
                + "§f: " + reason, "herplun.check.on");
        getLogger().info("REPORT " + s.getName() + " -> " + a[0] + ": " + reason);
        say(s, "Ваша жалоба отправлена администрации.");
        return true;
    }

    private boolean cmdVoice(CommandSender s) {
        if (noPerm(s, "herplun.voice")) return true;
        String link = DEF_VOICE;
        try {
            link = getConfig().getString("voice.link", DEF_VOICE);
        } catch (Exception ignored) {
        }
        if (link == null || !(link.startsWith("https://") || link.startsWith("http://"))) {
            say(s, "§cСсылка в config.yml указана неверно (нужна http:// или https://).");
            return true;
        }
        String text = cfg("voice.message", "&aСкачать голосовой чат: &b{link}").replace("{link}", link);
        if (s instanceof Player) {
            BaseComponent[] parts = TextComponent.fromLegacyText(text);
            for (BaseComponent c : parts) {
                c.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link));
            }
            ((Player) s).spigot().sendMessage(parts);
        } else {
            s.sendMessage(text);
        }
        return true;
    }

    private boolean cmdHerplun(CommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("privilege")) {
            s.sendMessage("§c§lHerPlun §fv" + getDescription().getVersion());
            s.sendMessage("§e/herplun privilege set <игрок> <привилегия> §7— выдать навсегда");
            s.sendMessage("§e/herplun privilege addtemp <игрок> <привилегия> <время> §7— выдать на время");
            s.sendMessage("§e/herplun privilege remove <игрок> <привилегия> §7— снять");
            return true;
        }
        if (noPerm(s, "herplun.privilege")) return true;
        if (a.length < 4) {
            say(s, "Использование: /herplun privilege <set|addtemp|remove> <игрок> <привилегия> [время]");
            return true;
        }
        String sub = a[1].toLowerCase(Locale.ROOT);
        String player = a[2];
        String group = a[3];
        if (!SAFE.matcher(player).matches() || !SAFE.matcher(group).matches()) {
            say(s, "§cНедопустимые символы в нике или названии привилегии.");
            return true;
        }
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            say(s, "§cДля привилегий нужен установленный плагин LuckPerms.");
            return true;
        }
        String lp;
        switch (sub) {
            case "set":
                lp = "lp user " + player + " parent set " + group;
                say(s, "Привилегия §e" + group + " §fвыдана игроку §e" + player + " §fнавсегда.");
                break;
            case "addtemp":
                if (a.length < 5 || parseTime(a[4]) <= 0 || !SAFE.matcher(a[4]).matches()) {
                    say(s, "Использование: /herplun privilege addtemp <игрок> <привилегия> <время>  (пример: 30d)");
                    return true;
                }
                lp = "lp user " + player + " parent addtemp " + group + " " + a[4];
                say(s, "Привилегия §e" + group + " §fвыдана игроку §e" + player + " §fна §e" + a[4] + "§f.");
                break;
            case "remove":
                lp = "lp user " + player + " parent remove " + group;
                say(s, "Привилегия §e" + group + " §fснята у игрока §e" + player + "§f.");
                break;
            default:
                say(s, "Использование: /herplun privilege <set|addtemp|remove> ...");
                return true;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), lp);
        return true;
    }

    // ---------------------------------------------------------------- /check

    private boolean cmdCheck(CommandSender s, String[] a) {
        if (a.length < 1) {
            say(s, "Использование: /check <on|off|tp|time>");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "on":   return checkOn(s, a);
            case "off":  return checkOff(s, a);
            case "tp":   return checkTp(s);
            case "time": return checkTime(s, a);
            default:
                say(s, "Использование: /check <on|off|tp|time>");
                return true;
        }
    }

    private boolean checkOn(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.check.on")) return true;
        if (!(s instanceof Player)) {
            say(s, "Эту команду можно использовать только в игре.");
            return true;
        }
        if (a.length < 3) {
            say(s, "Использование: /check on <игрок> <время>  (пример: 5m)");
            return true;
        }
        Player mod = (Player) s;
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            say(s, "Игрок не найден или не в сети.");
            return true;
        }
        long ms = parseTime(a[2]);
        if (ms <= 0) {
            say(s, "Неверное время. Пример: §e5m§f, §e1h§f, §e90s");
            return true;
        }
        if (checks.containsKey(target.getUniqueId())) {
            say(s, "Этот игрок уже на проверке.");
            return true;
        }
        checks.put(target.getUniqueId(), new Check(target.getUniqueId(), target.getName(),
                mod.getUniqueId(), System.currentTimeMillis() + ms));

        target.sendMessage("§c§l══════════════════════════");
        target.sendMessage("§c§lВас вызвали на проверку на читы!");
        target.sendMessage("§fМодератор: §e" + mod.getName());
        target.sendMessage("§fУ вас есть §e" + a[2] + "§f, чтобы пройти проверку.");
        target.sendMessage("§cВыход из игры или отказ = бан навсегда!");
        target.sendMessage("§c§l══════════════════════════");
        target.sendTitle("§c§lПРОВЕРКА НА ЧИТЫ", "§fНапишите модератору в чат", 10, 100, 20);

        say(s, "Игрок §e" + target.getName() + " §fвызван на проверку на §e" + a[2]
                + "§f. Если время истечёт — он будет забанен навсегда.");
        return true;
    }

    private boolean checkOff(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.check.off")) return true;
        Check ch = null;
        if (a.length >= 2) {
            Player t = Bukkit.getPlayerExact(a[1]);
            if (t != null) ch = checks.get(t.getUniqueId());
        } else if (s instanceof Player) {
            ch = findByModerator(((Player) s).getUniqueId());
        }
        if (ch == null) {
            say(s, "Активная проверка не найдена. Используйте: /check off <игрок>");
            return true;
        }
        checks.remove(ch.target);
        Player t = Bukkit.getPlayer(ch.target);
        if (t != null) t.sendMessage(prefix() + "§aПроверка завершена. Спасибо!");
        say(s, "Проверка игрока §e" + ch.targetName + " §fзавершена.");
        return true;
    }

    private boolean checkTp(CommandSender s) {
        if (noPerm(s, "herplun.check.tp")) return true;
        if (!(s instanceof Player)) {
            say(s, "Эту команду можно использовать только в игре.");
            return true;
        }
        Player mod = (Player) s;
        Check ch = findByModerator(mod.getUniqueId());
        if (ch == null) {
            say(s, "У вас нет активной проверки.");
            return true;
        }
        Player t = Bukkit.getPlayer(ch.target);
        if (t == null) {
            say(s, "Игрок не в сети.");
            return true;
        }
        mod.teleport(t.getLocation());
        say(s, "Вы телепортированы к §e" + t.getName() + "§f.");
        return true;
    }

    private boolean checkTime(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.check.time")) return true;
        if (!(s instanceof Player)) {
            say(s, "Эту команду можно использовать только в игре.");
            return true;
        }
        if (a.length < 2) {
            say(s, "Использование: /check time <время>  (пример: 5m)");
            return true;
        }
        Check ch = findByModerator(((Player) s).getUniqueId());
        if (ch == null) {
            say(s, "У вас нет активной проверки.");
            return true;
        }
        long ms = parseTime(a[1]);
        if (ms <= 0) {
            say(s, "Неверное время. Пример: §e5m§f, §e1h");
            return true;
        }
        ch.endTime += ms;
        long left = Math.max(0, (ch.endTime - System.currentTimeMillis()) / 1000);
        say(s, "Проверка продлена на §e" + a[1] + "§f. Осталось: §e" + formatSec(left));
        Player t = Bukkit.getPlayer(ch.target);
        if (t != null) t.sendMessage(prefix() + "Время проверки продлено. Осталось: §e" + formatSec(left));
        return true;
    }

    // ---------------------------------------------------------------- события

    private boolean frozen(Player p) {
        return checks.containsKey(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!frozen(e.getPlayer())) return;
        if (e.getTo() == null) return;
        if (e.getFrom().getX() != e.getTo().getX()
                || e.getFrom().getY() != e.getTo().getY()
                || e.getFrom().getZ() != e.getTo().getZ()) {
            e.setTo(e.getFrom().clone().setDirection(e.getTo().getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Mute m = getMute(e.getPlayer().getName());
        if (m == null) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(prefix() + muteText(m));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (frozen(p)) {
            e.setCancelled(true);
            p.sendMessage(prefix() + "Во время проверки команды запрещены.");
            return;
        }
        Mute m = getMute(p.getName());
        if (m == null) return;
        String label = e.getMessage().trim();
        if (label.startsWith("/")) label = label.substring(1);
        label = label.split(" ")[0].toLowerCase(Locale.ROOT);
        int i = label.indexOf(':');
        if (i >= 0) label = label.substring(i + 1);
        List<String> blocked = getConfig().isList("mute.blocked-commands")
                ? getConfig().getStringList("mute.blocked-commands") : DEF_BLOCKED;
        for (String b : blocked) {
            if (b != null && b.equalsIgnoreCase(label)) {
                e.setCancelled(true);
                p.sendMessage(prefix() + muteText(m));
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && frozen((Player) e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (frozen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (frozen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (frozen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (frozen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Check ch = checks.remove(e.getPlayer().getUniqueId());
        if (ch == null) return;
        banPermanent(ch.targetName, "Выход во время проверки на читы", "HerPlun");
        Bukkit.broadcast(prefix() + "Игрок §e" + ch.targetName
                + " §fвышел во время проверки и забанен навсегда.", "herplun.check.on");
    }

    // ---------------------------------------------------------------- муты

    private Mute getMute(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Mute m = mutes.get(key);
        if (m == null) return null;
        if (m.until > 0 && System.currentTimeMillis() >= m.until) {
            mutes.remove(key);
            saveMutes();
            return null;
        }
        return m;
    }

    private String muteText(Mute m) {
        if (m == null) return "";
        if (m.until <= 0) {
            return cfg("messages.muted-permanent", "&cВы замучены навсегда и не можете писать в чат.");
        }
        long left = Math.max(0, m.until - System.currentTimeMillis());
        return cfg("messages.muted-temp", "&cВы замучены. Осталось: &e{time}")
                .replace("{time}", formatMs(left));
    }

    private synchronized void loadMutes() {
        mutes.clear();
        try {
            if (mutesFile == null || !mutesFile.exists()) return;
            YamlConfiguration y = YamlConfiguration.loadConfiguration(mutesFile);
            ConfigurationSection sec = y.getConfigurationSection("mutes");
            if (sec == null) return;
            for (String key : sec.getKeys(false)) {
                String n = sec.getString(key + ".name", key);
                long until = sec.getLong(key + ".until", 0L);
                String by = sec.getString(key + ".by", "?");
                mutes.put(key.toLowerCase(Locale.ROOT), new Mute(n, until, by));
            }
        } catch (Exception ex) {
            getLogger().warning("Не удалось прочитать mutes.yml: " + ex.getMessage());
        }
    }

    private synchronized void saveMutes() {
        try {
            YamlConfiguration y = new YamlConfiguration();
            for (Map.Entry<String, Mute> en : mutes.entrySet()) {
                String k = "mutes." + en.getKey();
                y.set(k + ".name", en.getValue().name);
                y.set(k + ".until", en.getValue().until);
                y.set(k + ".by", en.getValue().by);
            }
            y.save(mutesFile);
        } catch (Exception ex) {
            getLogger().warning("Не удалось сохранить mutes.yml: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- утилиты

    private Check findByModerator(UUID mod) {
        for (Check c : checks.values()) if (c.moderator.equals(mod)) return c;
        return null;
    }

    private String join(String[] a, int from) {
        return String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }

    private String realName(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return online.getName();
        OfflinePlayer off = Bukkit.getOfflinePlayer(input);
        return off.getName() != null ? off.getName() : input;
    }

    private void banPermanent(String name, String reason, String source) {
        Bukkit.getBanList(BanList.Type.NAME).addBan(name, reason, null, source);
        kick(name, "§cВы забанены навсегда.\n§cПричина: §f" + reason);
    }

    private void kick(String name, final String msg) {
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

    /** "1d2h30m10s" -> миллисекунды, либо -1 при ошибке. */
    private long parseTime(String in) {
        Matcher m = TIME.matcher(in.toLowerCase(Locale.ROOT));
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) return -1;
            end = m.end();
            long n;
            try {
                n = Long.parseLong(m.group(1));
            } catch (NumberFormatException ex) {
                return -1;
            }
            switch (m.group(2)) {
                case "s": total += n * 1000L; break;
                case "m": total += n * 60_000L; break;
                case "h": total += n * 3_600_000L; break;
                case "d": total += n * 86_400_000L; break;
                case "w": total += n * 604_800_000L; break;
            }
        }
        return end == in.length() ? total : -1;
    }

    private String formatSec(long sec) {
        return (sec / 60) + " мин " + (sec % 60) + " сек";
    }

    private String formatMs(long ms) {
        long sec = ms / 1000;
        long d = sec / 86400;
        long h = (sec % 86400) / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(" д ");
        if (h > 0) sb.append(h).append(" ч ");
        if (m > 0) sb.append(m).append(" мин ");
        if (s > 0 || sb.length() == 0) sb.append(s).append(" сек");
        return sb.toString().trim();
    }

    // ---------------------------------------------------------------- tab

    private List<String> filter(String prefix, String... options) {
        List<String> r = new ArrayList<>();
        for (String o : options) if (o.startsWith(prefix.toLowerCase(Locale.ROOT))) r.add(o);
        return r;
    }

    private List<String> players(String prefix) {
        List<String> r = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                r.add(p.getName());
        return r;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        if (n.equals("check")) {
            if (a.length == 1) return filter(a[0], "on", "off", "tp", "time");
            if (a.length == 2 && (a[0].equalsIgnoreCase("on") || a[0].equalsIgnoreCase("off")))
                return players(a[1]);
            return new ArrayList<>();
        }
        if (n.equals("herplun")) {
            if (a.length == 1) return filter(a[0], "privilege");
            if (a.length == 2) return filter(a[1], "set", "addtemp", "remove");
            if (a.length == 3) return players(a[2]);
            return new ArrayList<>();
        }
        if (a.length == 1 && (n.equals("ban") || n.equals("unban") || n.equals("timeban")
                || n.equals("mute") || n.equals("unmute") || n.equals("report")))
            return players(a[0]);
        return new ArrayList<>();
    }
}
