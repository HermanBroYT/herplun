package ru.herplun;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"deprecation", "unused"})
public class HerPlun extends JavaPlugin implements Listener, TabExecutor {

    private static final String PREFIX = "§c[HerPlun] §f";
    private static final String NO_PERM = "§c\"HerPlun\" У вас нет прав что прописать команду!";
    private static final Pattern TIME = Pattern.compile("(\\d+)([smhdw])");

    /** Активная проверка. */
    private static class Check {
        final UUID target;
        final String targetName;
        final UUID moderator;
        final String moderatorName;
        long endTime;

        Check(UUID target, String targetName, UUID moderator, String moderatorName, long endTime) {
            this.target = target;
            this.targetName = targetName;
            this.moderator = moderator;
            this.moderatorName = moderatorName;
            this.endTime = endTime;
        }
    }

    // ключ — UUID проверяемого игрока
    private final Map<UUID, Check> checks = new HashMap<>();

    @Override
    public void onEnable() {
        for (String c : new String[]{"ban", "timeban", "help", "report", "check"}) {
            if (getCommand(c) != null) getCommand(c).setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);

        // Раз в секунду проверяем, не истекло ли время проверки
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            for (Check ch : new ArrayList<>(checks.values())) {
                if (now >= ch.endTime) {
                    checks.remove(ch.target);
                    banPermanent(ch.targetName, "Время проверки на читы истекло", "HerPlun");
                    Bukkit.broadcast(PREFIX + "Игрок §e" + ch.targetName
                            + " §fзабанен: время проверки истекло.", "herplun.check.on");
                }
            }
        }, 20L, 20L);

        getLogger().info("HerPlun включён!");
    }

    // ---------------------------------------------------------------- команды

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "ban":     return cmdBan(s, a);
            case "timeban": return cmdTimeBan(s, a);
            case "help":    return cmdHelp(s);
            case "report":  return cmdReport(s, a);
            case "check":   return cmdCheck(s, a);
        }
        return false;
    }

    private boolean noPerm(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return false;
        s.sendMessage(NO_PERM);
        return true;
    }

    private boolean cmdBan(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.ban")) return true;
        if (a.length < 1) {
            s.sendMessage(PREFIX + "Использование: /ban <игрок> [причина]");
            return true;
        }
        String reason = a.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)) : "Нарушение правил";
        String target = realName(a[0]);
        banPermanent(target, reason, s.getName());
        s.sendMessage(PREFIX + "Игрок §e" + target + " §fзабанен навсегда. Причина: §e" + reason);
        return true;
    }

    private boolean cmdTimeBan(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.timeban")) return true;
        if (a.length < 2) {
            s.sendMessage(PREFIX + "Использование: /timeban <игрок> <время> [причина]  (пример: 1d2h30m)");
            return true;
        }
        long ms = parseTime(a[1]);
        if (ms <= 0) {
            s.sendMessage(PREFIX + "Неверное время. Пример: §e30m§f, §e12h§f, §e7d§f, §e1d2h30m");
            return true;
        }
        String reason = a.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)) : "Нарушение правил";
        String target = realName(a[0]);
        Date until = new Date(System.currentTimeMillis() + ms);
        Bukkit.getBanList(BanList.Type.NAME).addBan(target, reason, until, s.getName());
        kick(target, "§cВы забанены до §e" + until + "\n§cПричина: §f" + reason);
        s.sendMessage(PREFIX + "Игрок §e" + target + " §fзабанен на §e" + a[1] + "§f. Причина: §e" + reason);
        return true;
    }

    private boolean cmdHelp(CommandSender s) {
        if (noPerm(s, "herplun.help")) return true;
        s.sendMessage("§c§l— HerPlun: помощь —");
        s.sendMessage("§e/ban <игрок> [причина] §7— бан навсегда");
        s.sendMessage("§e/timeban <игрок> <время> [причина] §7— временный бан");
        s.sendMessage("§e/report <игрок> <причина> §7— пожаловаться на игрока");
        s.sendMessage("§e/check on <игрок> <время> §7— вызвать на проверку читов");
        s.sendMessage("§e/check off [игрок] §7— завершить проверку");
        s.sendMessage("§e/check tp §7— телепорт к проверяемому");
        s.sendMessage("§e/check time <время> §7— продлить время проверки");
        s.sendMessage("§7Время: s, m, h, d, w (например 1h30m)");
        return true;
    }

    private boolean cmdReport(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.report")) return true;
        if (a.length < 2) {
            s.sendMessage(PREFIX + "Использование: /report <игрок> <причина>");
            return true;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
        String msg = PREFIX + "§eЖалоба §fот §a" + s.getName() + " §fна §c" + a[0] + "§f: " + reason;
        Bukkit.broadcast(msg, "herplun.check.on");
        getLogger().info("REPORT " + s.getName() + " -> " + a[0] + ": " + reason);
        s.sendMessage(PREFIX + "Ваша жалоба отправлена администрации.");
        return true;
    }

    private boolean cmdCheck(CommandSender s, String[] a) {
        if (a.length < 1) {
            s.sendMessage(PREFIX + "Использование: /check <on|off|tp|time>");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "on":   return checkOn(s, a);
            case "off":  return checkOff(s, a);
            case "tp":   return checkTp(s);
            case "time": return checkTime(s, a);
            default:
                s.sendMessage(PREFIX + "Использование: /check <on|off|tp|time>");
                return true;
        }
    }

    private boolean checkOn(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.check.on")) return true;
        if (!(s instanceof Player)) {
            s.sendMessage(PREFIX + "Эту команду можно использовать только в игре.");
            return true;
        }
        if (a.length < 3) {
            s.sendMessage(PREFIX + "Использование: /check on <игрок> <время>  (пример: 5m)");
            return true;
        }
        Player mod = (Player) s;
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            s.sendMessage(PREFIX + "Игрок не найден или не в сети.");
            return true;
        }
        long ms = parseTime(a[2]);
        if (ms <= 0) {
            s.sendMessage(PREFIX + "Неверное время. Пример: §e5m§f, §e1h§f, §e90s");
            return true;
        }
        if (checks.containsKey(target.getUniqueId())) {
            s.sendMessage(PREFIX + "Этот игрок уже на проверке.");
            return true;
        }
        checks.put(target.getUniqueId(), new Check(target.getUniqueId(), target.getName(),
                mod.getUniqueId(), mod.getName(), System.currentTimeMillis() + ms));

        target.sendMessage("§c§l══════════════════════════");
        target.sendMessage("§c§lВас вызвали на проверку на читы!");
        target.sendMessage("§fМодератор: §e" + mod.getName());
        target.sendMessage("§fУ вас есть §e" + a[2] + "§f, чтобы пройти проверку.");
        target.sendMessage("§cВыход из игры или отказ = бан навсегда!");
        target.sendMessage("§c§l══════════════════════════");
        target.sendTitle("§c§lПРОВЕРКА НА ЧИТЫ", "§fНапишите модератору в чат", 10, 100, 20);

        s.sendMessage(PREFIX + "Игрок §e" + target.getName() + " §fвызван на проверку на §e" + a[2]
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
            s.sendMessage(PREFIX + "Активная проверка не найдена. Используйте: /check off <игрок>");
            return true;
        }
        checks.remove(ch.target);
        Player t = Bukkit.getPlayer(ch.target);
        if (t != null) t.sendMessage(PREFIX + "§aПроверка завершена. Спасибо!");
        s.sendMessage(PREFIX + "Проверка игрока §e" + ch.targetName + " §fзавершена.");
        return true;
    }

    private boolean checkTp(CommandSender s) {
        if (noPerm(s, "herplun.check.tp")) return true;
        if (!(s instanceof Player)) {
            s.sendMessage(PREFIX + "Эту команду можно использовать только в игре.");
            return true;
        }
        Player mod = (Player) s;
        Check ch = findByModerator(mod.getUniqueId());
        if (ch == null) {
            s.sendMessage(PREFIX + "У вас нет активной проверки.");
            return true;
        }
        Player t = Bukkit.getPlayer(ch.target);
        if (t == null) {
            s.sendMessage(PREFIX + "Игрок не в сети.");
            return true;
        }
        mod.teleport(t.getLocation());
        s.sendMessage(PREFIX + "Вы телепортированы к §e" + t.getName() + "§f.");
        return true;
    }

    private boolean checkTime(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.check.time")) return true;
        if (!(s instanceof Player)) {
            s.sendMessage(PREFIX + "Эту команду можно использовать только в игре.");
            return true;
        }
        if (a.length < 2) {
            s.sendMessage(PREFIX + "Использование: /check time <время>  (пример: 5m)");
            return true;
        }
        Check ch = findByModerator(((Player) s).getUniqueId());
        if (ch == null) {
            s.sendMessage(PREFIX + "У вас нет активной проверки.");
            return true;
        }
        long ms = parseTime(a[1]);
        if (ms <= 0) {
            s.sendMessage(PREFIX + "Неверное время. Пример: §e5m§f, §e1h");
            return true;
        }
        ch.endTime += ms;
        long left = Math.max(0, (ch.endTime - System.currentTimeMillis()) / 1000);
        s.sendMessage(PREFIX + "Проверка продлена на §e" + a[1] + "§f. Осталось: §e" + formatSec(left));
        Player t = Bukkit.getPlayer(ch.target);
        if (t != null) t.sendMessage(PREFIX + "Время проверки продлено. Осталось: §e" + formatSec(left));
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        if (frozen(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(PREFIX + "Во время проверки команды запрещены.");
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
        Bukkit.broadcast(PREFIX + "Игрок §e" + ch.targetName
                + " §fвышел во время проверки и забанен навсегда.", "herplun.check.on");
    }

    // ---------------------------------------------------------------- утилиты

    private Check findByModerator(UUID mod) {
        for (Check c : checks.values()) if (c.moderator.equals(mod)) return c;
        return null;
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

    private void kick(String name, String msg) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) Bukkit.getScheduler().runTask(this, () -> p.kickPlayer(msg));
    }

    /** "1d2h30m10s" -> миллисекунды, либо -1 при ошибке. */
    private long parseTime(String in) {
        Matcher m = TIME.matcher(in.toLowerCase(Locale.ROOT));
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) return -1;
            end = m.end();
            long n = Long.parseLong(m.group(1));
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

    // ---------------------------------------------------------------- tab

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        if (n.equals("check")) {
            if (a.length == 1) {
                for (String x : new String[]{"on", "off", "tp", "time"})
                    if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
                return out;
            }
            if (a.length == 2 && (a[0].equalsIgnoreCase("on") || a[0].equalsIgnoreCase("off")))
                return players(a[1]);
            return out;
        }
        if (a.length == 1 && (n.equals("ban") || n.equals("timeban") || n.equals("report")))
            return players(a[0]);
        return out;
    }

    private List<String> players(String prefix) {
        List<String> r = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) r.add(p.getName());
        return r;
    }
}
