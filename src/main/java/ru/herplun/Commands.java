package ru.herplun;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Все команды HerPlun. */
final class Commands implements TabExecutor {

    private static final String DEF_VOICE = "https://modrinth.com/plugin/simple-voice-chat";

    private final HerPlun plugin;
    private final Store store;
    private final Map<UUID, Long> reportCooldown = new HashMap<>();
    private final DmCommands dm;
    private final Map<String, Long> pendingStop = new HashMap<>();
    private static final long STOP_CONFIRM_MS = 30_000L;

    Commands(HerPlun plugin) {
        this.plugin = plugin;
        this.store = plugin.store();
        this.dm = new DmCommands(plugin);
    }

    private void say(CommandSender s, String text) {
        plugin.say(s, text);
    }

    private boolean noPerm(CommandSender s, String perm) {
        return plugin.noPerm(s, perm);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        try {
            switch (name) {
                case "ban":      return cmdBan(s, a);
                case "unban":    return cmdUnban(s, a);
                case "timeban":  return cmdTimeBan(s, a);
                case "mute":     return cmdMute(s, a);
                case "unmute":   return cmdUnmute(s, a);
                case "help":     return cmdHelp(s);
                case "report":   return cmdReport(s, a);
                case "check":    return cmdCheck(s, a);
                case "voice":    return cmdVoice(s);
                case "gm":
                case "gamemode": return cmdGamemode(s, name, a);
                case "herplun":  return cmdHerplun(s, a);
                case "deluxemenus": return dm.handle(s, a);
                default:         return false;
            }
        } catch (Exception ex) {
            say(s, "§cПроизошла ошибка. Подробности в консоли сервера.");
            plugin.getLogger().warning("Ошибка в команде /" + name + ": " + ex);
            ex.printStackTrace();
            return true;
        }
    }

    // ================================================================ баны

    private boolean cmdBan(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.ban")) return true;
        if (a.length < 1) {
            say(s, "Использование: /ban <игрок> [причина]");
            return true;
        }
        String reason = a.length > 1 ? Util.join(a, 1) : "Нарушение правил";
        String target = plugin.realName(a[0]);
        plugin.ban(target, reason, s.getName(), 0);
        say(s, "Игрок §e" + target + " §fзабанен навсегда. Причина: §e" + reason);
        return true;
    }

    private boolean cmdUnban(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.unban")) return true;
        if (a.length < 1) {
            say(s, "Использование: /unban <игрок>");
            return true;
        }
        String target = plugin.realName(a[0]);
        BanList list = Bukkit.getBanList(BanList.Type.NAME);
        boolean known = store.bans.containsKey(target.toLowerCase(Locale.ROOT));
        if (!list.isBanned(target) && !known) {
            say(s, "Игрок §e" + target + " §fне находится в бане.");
            return true;
        }
        list.pardon(target);
        store.bans.remove(target.toLowerCase(Locale.ROOT));
        store.saveBans();
        say(s, "Игрок §e" + target + " §fразбанен.");
        return true;
    }

    private boolean cmdTimeBan(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.timeban")) return true;
        if (a.length < 2) {
            say(s, "Использование: /timeban <игрок> <время> [причина]  (пример: 1d2h30m)");
            return true;
        }
        long ms = Util.parseTime(a[1]);
        if (ms <= 0) {
            say(s, "Неверное время. Пример: §e30m§f, §e12h§f, §e7d§f, §e1d2h30m");
            return true;
        }
        String reason = a.length > 2 ? Util.join(a, 2) : "Нарушение правил";
        String target = plugin.realName(a[0]);
        plugin.ban(target, reason, s.getName(), System.currentTimeMillis() + ms);
        say(s, "Игрок §e" + target + " §fзабанен на §e" + a[1] + "§f. Причина: §e" + reason);
        return true;
    }

    // ================================================================ муты

    private boolean cmdMute(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.mute")) return true;
        if (a.length < 1) {
            say(s, "Использование: /mute <игрок> [время]  (без времени — навсегда)");
            return true;
        }
        String target = plugin.realName(a[0]);
        long until = 0;
        String shown = "навсегда";
        if (a.length >= 2) {
            long ms = Util.parseTime(a[1]);
            if (ms <= 0) {
                say(s, "Неверное время. Пример: §e30m§f, §e2h§f, §e1d");
                return true;
            }
            until = System.currentTimeMillis() + ms;
            shown = "на " + a[1];
        }
        Store.MuteRecord rec = new Store.MuteRecord(target, s.getName(), System.currentTimeMillis(), until);
        store.mutes.put(target.toLowerCase(Locale.ROOT), rec);
        store.saveMutes();
        say(s, "Игрок §e" + target + " §fзамучен §e" + shown + "§f.");
        Player p = Bukkit.getPlayerExact(target);
        if (p != null) p.sendMessage(plugin.prefix() + plugin.muteText(rec));
        return true;
    }

    private boolean cmdUnmute(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.unmute")) return true;
        if (a.length < 1) {
            say(s, "Использование: /unmute <игрок>");
            return true;
        }
        String target = plugin.realName(a[0]);
        if (store.mutes.remove(target.toLowerCase(Locale.ROOT)) == null) {
            say(s, "Игрок §e" + target + " §fне в муте.");
            return true;
        }
        store.saveMutes();
        say(s, "С игрока §e" + target + " §fснят мут.");
        Player p = Bukkit.getPlayerExact(target);
        if (p != null) say(p, "§aМут снят. Вы снова можете писать в чат.");
        return true;
    }

    // ================================================================ помощь

    private void line(CommandSender s, String perm, String text) {
        if (s.hasPermission(perm)) s.sendMessage(text);
    }

    private boolean cmdHelp(CommandSender s) {
        if (noPerm(s, "herplun.help")) return true;
        s.sendMessage("§c§l— HerPlun " + plugin.getDescription().getVersion() + ": помощь —");
        line(s, "herplun.ban", "§e/ban <игрок> [причина] §7— бан навсегда");
        line(s, "herplun.unban", "§e/unban <игрок> §7— разбан");
        line(s, "herplun.timeban", "§e/timeban <игрок> <время> [причина] §7— временный бан");
        line(s, "herplun.mute", "§e/mute <игрок> [время] §7— мут (без времени — навсегда)");
        line(s, "herplun.unmute", "§e/unmute <игрок> §7— снять мут");
        line(s, "herplun.report", "§e/report <игрок> <причина> §7— пожаловаться");
        line(s, "herplun.report", "§e/report cancel <номер> §7— отменить свою жалобу");
        line(s, "herplun.open", "§e/report open §7— меню жалоб");
        line(s, "herplun.voice", "§e/voice §7— ссылка на голосовой чат");
        line(s, "herplun.check.on", "§e/check on <игрок> <время> §7— вызвать на проверку");
        line(s, "herplun.check.off", "§e/check off [игрок] §7— завершить проверку");
        line(s, "herplun.check.tp", "§e/check tp §7— телепорт к проверяемому");
        line(s, "herplun.check.time", "§e/check time <время> §7— продлить проверку");
        line(s, "herplun.gm", "§e/gm <режим> [игрок] §7— режим игры");
        line(s, "herplun.gamemode", "§e/gamemode <режим> [игрок] §7— режим игры");
        line(s, "herplun.privilege", "§e/herplun privilege set|addtemp|remove ... §7— привилегии");
        line(s, "herplun.technical", "§e/herplun technical on|off §7— технические работы");
        line(s, "herplun.dm.help", "§e/herplun dm help §7— меню (или /deluxemenus)");
        line(s, "herplun.axe", "§e/herplun axe §7— топорик WorldEdit/WorldGuard");
        line(s, "herplun.plugins", "§e/herplun plugins §7— плагины через HerPlun");
        line(s, "herplun.stopserver", "§e/herplun stopserver §7— остановить сервер");
        line(s, "herplun.axe", "§e/herplun we §7— команды WorldEdit и WorldGuard");
        s.sendMessage("§7Время: s, m, h, d, w (например 1h30m)");
        return true;
    }

    // ================================================================ жалобы

    private boolean cmdReport(CommandSender s, String[] a) {
        if (a.length >= 1 && a[0].equalsIgnoreCase("open")) {
            if (noPerm(s, "herplun.open")) return true;
            if (!(s instanceof Player)) {
                say(s, "Меню можно открыть только в игре.");
                return true;
            }
            ReportMenu.open((Player) s);
            return true;
        }

        if (noPerm(s, "herplun.report")) return true;
        if (!(s instanceof Player)) {
            say(s, "Жалобы можно подавать только в игре.");
            return true;
        }
        Player p = (Player) s;

        if (a.length >= 1 && a[0].equalsIgnoreCase("cancel")) {
            if (a.length < 2) {
                say(s, "Использование: /report cancel <номер>");
                return true;
            }
            int id;
            try {
                id = Integer.parseInt(a[1].replace("#", ""));
            } catch (NumberFormatException ex) {
                say(s, "Номер жалобы должен быть числом.");
                return true;
            }
            Store.Report r = store.reports.get(id);
            if (r == null || !r.reporterUuid.equals(p.getUniqueId().toString())) {
                say(s, "У вас нет жалобы с таким номером.");
                return true;
            }
            store.removeReport(id);
            say(s, "Жалоба §e#" + id + " §fотменена.");
            return true;
        }

        if (a.length < 2) {
            say(s, "Использование: /report <игрок> <причина>  |  /report open  |  /report cancel <номер>");
            return true;
        }
        if (a[0].equalsIgnoreCase(p.getName())) {
            say(s, "Нельзя пожаловаться на самого себя.");
            return true;
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(a[0]);
        if (Bukkit.getPlayerExact(a[0]) == null && !off.hasPlayedBefore()) {
            say(s, "Игрок §e" + a[0] + " §fне найден на сервере.");
            return true;
        }

        long now = System.currentTimeMillis();
        long cooldown = plugin.cfgInt("report.cooldown-seconds", 30) * 1000L;
        Long last = reportCooldown.get(p.getUniqueId());
        if (last != null && now - last < cooldown) {
            say(s, "Подождите §e" + Util.formatMs(cooldown - (now - last)) + " §fперед следующей жалобой.");
            return true;
        }
        int max = plugin.cfgInt("report.max-per-player", 5);
        if (store.countReports(p.getUniqueId().toString()) >= max) {
            say(s, "У вас уже §e" + max + " §fоткрытых жалоб. Отмените одну: §e/report open");
            return true;
        }

        String target = plugin.realName(a[0]);
        String reason = Util.shorten(Util.join(a, 1), 200);
        Store.Report r = store.addReport(p.getUniqueId().toString(), p.getName(), target, reason);
        reportCooldown.put(p.getUniqueId(), now);

        plugin.notifyStaff(plugin.prefix() + "§eЖалоба #" + r.id + " §fот §a" + p.getName()
                + " §fна §c" + target + "§f: " + reason + " §7(/report open)", "herplun.check.on");
        say(s, "Жалоба §e#" + r.id + " §fотправлена. Отменить: §e/report open §fили §e/report cancel " + r.id);
        return true;
    }

    // ================================================================ голосовой чат

    private boolean cmdVoice(CommandSender s) {
        if (noPerm(s, "herplun.voice")) return true;
        String link = DEF_VOICE;
        try {
            link = plugin.getConfig().getString("voice.link", DEF_VOICE);
        } catch (Exception ignored) {
        }
        if (link == null || !(link.startsWith("https://") || link.startsWith("http://"))) {
            say(s, "§cСсылка в config.yml указана неверно (нужна http:// или https://).");
            return true;
        }
        String text = plugin.cfg("voice.message", "&aСкачать голосовой чат: &b{link}").replace("{link}", link);
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

    // ================================================================ /gm /gamemode

    private GameMode parseMode(String in) {
        switch (in.toLowerCase(Locale.ROOT)) {
            case "0": case "s": case "survival": case "survive":
                return GameMode.SURVIVAL;
            case "1": case "c": case "creative": case "create":
                return GameMode.CREATIVE;
            case "2": case "a": case "adventure":
                return GameMode.ADVENTURE;
            case "3": case "sp": case "spectator":
                return GameMode.SPECTATOR;
            default:
                return null;
        }
    }

    private String modeName(GameMode m) {
        switch (m) {
            case SURVIVAL:  return "выживание";
            case CREATIVE:  return "творческий";
            case ADVENTURE: return "приключение";
            case SPECTATOR: return "наблюдатель";
            default:        return m.name();
        }
    }

    private boolean cmdGamemode(CommandSender s, String label, String[] a) {
        String perm = label.equals("gm") ? "herplun.gm" : "herplun.gamemode";
        if (noPerm(s, perm)) return true;
        if (a.length < 1) {
            say(s, "Использование: /" + label + " <режим> [игрок]  (0/s, 1/c, 2/a, 3/sp)");
            return true;
        }
        GameMode mode = parseMode(a[0]);
        if (mode == null) {
            say(s, "Неизвестный режим. Доступно: §e0/s§f, §e1/c§f, §e2/a§f, §e3/sp");
            return true;
        }
        Player target;
        if (a.length >= 2) {
            target = Bukkit.getPlayer(a[1]);
            if (target == null) {
                say(s, "Игрок не найден или не в сети.");
                return true;
            }
        } else if (s instanceof Player) {
            target = (Player) s;
        } else {
            say(s, "Из консоли укажите игрока: /" + label + " <режим> <игрок>");
            return true;
        }
        target.setGameMode(mode);
        String name = modeName(mode);
        if (s instanceof Player && target.equals(s)) {
            say(s, "Ваш режим игры: §e" + name + "§f.");
        } else {
            say(s, "Режим игры игрока §e" + target.getName() + "§f: §e" + name + "§f.");
            say(target, "Ваш режим игры изменён на §e" + name + "§f.");
        }
        return true;
    }

    // ================================================================ /herplun

    private boolean cmdHerplun(CommandSender s, String[] a) {
        if (a.length == 0) return herplunHelp(s);
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "privilege": return privilege(s, a);
            case "technical": return technical(s, a);
            case "reload":    return reload(s);
            case "dm":        return dm.handle(s, Arrays.copyOfRange(a, 1, a.length));
            case "stopserver": return stopServer(s, a);
            case "plugins":   return pluginsCmd(s);
            case "axe":
            case "wand":      return axe(s);
            case "we":
            case "wg":        return weHelp(s);
            default:          return herplunHelp(s);
        }
    }

    private boolean herplunHelp(CommandSender s) {
        s.sendMessage("§c§lHerPlun §fv" + plugin.getDescription().getVersion());
        s.sendMessage("§e/herplun privilege set <игрок> <привилегия> §7— выдать навсегда");
        s.sendMessage("§e/herplun privilege addtemp <игрок> <привилегия> <время> §7— выдать на время");
        s.sendMessage("§e/herplun privilege remove <игрок> <привилегия> §7— снять");
        s.sendMessage("§e/herplun technical <on|off> §7— технические работы");
        s.sendMessage("§e/herplun dm help §7— меню (как DeluxeMenus)");
        s.sendMessage("§e/herplun plugins §7— плагины, вложенные через HerPlun");
        s.sendMessage("§e/herplun stopserver §7— остановить сервер (с подтверждением)");
        s.sendMessage("§e/herplun axe §7— топорик для WorldEdit/WorldGuard");
        s.sendMessage("§e/herplun we §7— команды WorldEdit и WorldGuard");
        s.sendMessage("§e/herplun reload §7— перезагрузить настройки (только OP)");
        return true;
    }

    private boolean reload(CommandSender s) {
        if (!(s.isOp() || s.hasPermission("herplun.technical"))) {
            s.sendMessage(plugin.cfg("messages.no-permission", HerPlun.DEF_NO_PERM));
            return true;
        }
        plugin.reloadConfig();
        store.loadAll();
        say(s, "§aНастройки и файлы данных перезагружены.");
        return true;
    }

    private boolean technical(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.technical")) return true;
        if (a.length < 2 || !(a[1].equalsIgnoreCase("on") || a[1].equalsIgnoreCase("off"))) {
            say(s, "Использование: /herplun technical <on|off>");
            return true;
        }
        if (a[1].equalsIgnoreCase("on")) {
            if (store.isTechnical()) {
                say(s, "Технические работы уже включены.");
                return true;
            }
            store.setTechnical(true);
            String msg = plugin.cfg("messages.technical-kick",
                    "&cНа сервере проводятся технические работы.\n&fЗаходить могут только администраторы.");
            int kicked = 0;
            for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                if (!p.isOp() && !p.equals(s)) {
                    p.kickPlayer(msg);
                    kicked++;
                }
            }
            say(s, "§cТехнические работы ВКЛЮЧЕНЫ. §fЗаходить могут только OP. Выкинуто игроков: §e" + kicked);
        } else {
            if (!store.isTechnical()) {
                say(s, "Технические работы и так выключены.");
                return true;
            }
            store.setTechnical(false);
            say(s, "§aТехнические работы ВЫКЛЮЧЕНЫ. §fИгроки снова могут заходить.");
        }
        return true;
    }

    private boolean privilege(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.privilege")) return true;
        if (a.length < 4) {
            say(s, "Использование: /herplun privilege <set|addtemp|remove> <игрок> <привилегия> [время]");
            return true;
        }
        String sub = a[1].toLowerCase(Locale.ROOT);
        String player = a[2];
        String group = a[3];
        if (!Util.SAFE.matcher(player).matches() || !Util.SAFE.matcher(group).matches()) {
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
                if (a.length < 5 || Util.parseTime(a[4]) <= 0 || !Util.SAFE.matcher(a[4]).matches()) {
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

    // ================================================================ плагины и остановка сервера

    private boolean pluginsCmd(CommandSender s) {
        if (noPerm(s, "herplun.plugins")) return true;
        plugin.host().list(s);
        return true;
    }

    private boolean stopServer(CommandSender s, String[] a) {
        if (noPerm(s, "herplun.stopserver")) return true;
        String key = s instanceof Player ? ((Player) s).getUniqueId().toString() : "CONSOLE";
        String sub = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "";

        if (sub.equals("cancel")) {
            if (pendingStop.remove(key) != null) say(s, "§aОтключение сервера отменено.");
            else say(s, "Нет активного запроса на отключение сервера.");
            return true;
        }
        if (sub.equals("confirm")) {
            Long t = pendingStop.remove(key);
            if (t == null || System.currentTimeMillis() - t > STOP_CONFIRM_MS) {
                say(s, "§cЗапрос не найден или устарел. Введите §e/herplun stopserver §cещё раз.");
                return true;
            }
            plugin.getLogger().warning("Сервер останавливается командой /herplun stopserver (" + s.getName() + ")");
            say(s, "§cСервер отключается...");
            plugin.stopServer();
            return true;
        }

        // первый запрос — только предупреждение и кнопки
        pendingStop.put(key, System.currentTimeMillis());
        s.sendMessage("§c§l⚠ " + plugin.cfg("messages.server-stop-confirm",
                "&cВы точно хотите отключить сервер, игроки могут автоматически выйти из сервера!"));
        if (s instanceof Player) {
            List<BaseComponent> parts = new ArrayList<>();
            parts.addAll(Arrays.asList(Util.clickable("§a§l[Отменить]", "/herplun stopserver cancel")));
            parts.addAll(Arrays.asList(TextComponent.fromLegacyText("   ")));
            parts.addAll(Arrays.asList(Util.clickable("§c§l[Подтвердить]", "/herplun stopserver confirm")));
            ((Player) s).spigot().sendMessage(parts.toArray(new BaseComponent[0]));
        } else {
            s.sendMessage("Подтвердить: /herplun stopserver confirm   |   Отменить: /herplun stopserver cancel");
        }
        s.sendMessage("§7Подтвердите в течение 30 секунд.");
        return true;
    }

    // ================================================================ WorldEdit / WorldGuard

    private boolean axe(CommandSender s) {
        if (noPerm(s, "herplun.axe")) return true;
        if (!(s instanceof Player)) {
            say(s, "Эту команду можно использовать только в игре.");
            return true;
        }
        if (!WorldTools.hasWorldEdit()) {
            WorldTools.sendMissing(plugin, s);
            return true;
        }
        Player p = (Player) s;
        Map<Integer, org.bukkit.inventory.ItemStack> left = p.getInventory().addItem(WorldTools.axe());
        if (!left.isEmpty()) {
            for (org.bukkit.inventory.ItemStack it : left.values()) p.getWorld().dropItem(p.getLocation(), it);
            say(s, "Инвентарь полон — топорик выпал под ноги.");
        } else {
            say(s, "Вы получили §6топорик§f. §7ЛКМ — точка 1, ПКМ — точка 2.");
        }
        return true;
    }

    private boolean weHelp(CommandSender s) {
        if (noPerm(s, "herplun.axe")) return true;
        if (!WorldTools.hasWorldEdit() || !WorldTools.hasWorldGuard()) WorldTools.sendMissing(plugin, s);
        WorldTools.help(s);
        return true;
    }

    // ================================================================ /check

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

    private Check findByModerator(UUID mod) {
        for (Check c : plugin.checks().values()) if (c.moderator.equals(mod)) return c;
        return null;
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
        long ms = Util.parseTime(a[2]);
        if (ms <= 0) {
            say(s, "Неверное время. Пример: §e5m§f, §e1h§f, §e90s");
            return true;
        }
        if (plugin.checks().containsKey(target.getUniqueId())) {
            say(s, "Этот игрок уже на проверке.");
            return true;
        }
        plugin.checks().put(target.getUniqueId(), new Check(target.getUniqueId(), target.getName(),
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
            if (t != null) ch = plugin.checks().get(t.getUniqueId());
        } else if (s instanceof Player) {
            ch = findByModerator(((Player) s).getUniqueId());
        }
        if (ch == null) {
            say(s, "Активная проверка не найдена. Используйте: /check off <игрок>");
            return true;
        }
        plugin.checks().remove(ch.target);
        Player t = Bukkit.getPlayer(ch.target);
        if (t != null) say(t, "§aПроверка завершена. Спасибо!");
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
        long ms = Util.parseTime(a[1]);
        if (ms <= 0) {
            say(s, "Неверное время. Пример: §e5m§f, §e1h");
            return true;
        }
        ch.endTime += ms;
        String left = Util.formatMs(ch.endTime - System.currentTimeMillis());
        say(s, "Проверка продлена на §e" + a[1] + "§f. Осталось: §e" + left);
        Player t = Bukkit.getPlayer(ch.target);
        if (t != null) say(t, "Время проверки продлено. Осталось: §e" + left);
        return true;
    }

    // ================================================================ tab

    private List<String> players(String prefix) {
        List<String> r = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) r.add(p.getName());
        }
        return r;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> none = new ArrayList<>();
        switch (n) {
            case "check":
                if (a.length == 1) return Util.filter(a[0], "on", "off", "tp", "time");
                if (a.length == 2 && (a[0].equalsIgnoreCase("on") || a[0].equalsIgnoreCase("off")))
                    return players(a[1]);
                return none;
            case "herplun":
                if (a.length == 1) return Util.filter(a[0], "privilege", "technical", "reload", "dm", "axe", "we", "plugins", "stopserver");
                if (a.length == 2 && a[0].equalsIgnoreCase("stopserver")) return Util.filter(a[1], "confirm", "cancel");
                if (a.length >= 2 && a[0].equalsIgnoreCase("dm")) return dm.tab(s, Arrays.copyOfRange(a, 1, a.length));
                if (a.length == 2 && a[0].equalsIgnoreCase("privilege"))
                    return Util.filter(a[1], "set", "addtemp", "remove");
                if (a.length == 2 && a[0].equalsIgnoreCase("technical"))
                    return Util.filter(a[1], "on", "off");
                if (a.length == 3 && a[0].equalsIgnoreCase("privilege")) return players(a[2]);
                return none;
            case "deluxemenus":
                return dm.tab(s, a);
            case "gm":
            case "gamemode":
                if (a.length == 1) return Util.filter(a[0], "survival", "creative", "adventure", "spectator");
                if (a.length == 2) return players(a[1]);
                return none;
            case "report":
                if (a.length == 1) {
                    List<String> r = Util.filter(a[0], "open", "cancel");
                    r.addAll(players(a[0]));
                    return r;
                }
                return none;
            case "ban": case "unban": case "timeban": case "mute": case "unmute":
                return a.length == 1 ? players(a[0]) : none;
            default:
                return none;
        }
    }
}
