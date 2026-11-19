package ru.herplun;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /herplun dm ... и /deluxemenus ... */
final class DmCommands {

    private final HerPlun plugin;

    DmCommands(HerPlun plugin) {
        this.plugin = plugin;
    }

    /** a — аргументы после "dm". */
    boolean handle(CommandSender s, String[] a) {
        if (a.length == 0) return help(s);
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload": return reload(s);
            case "list":   return list(s);
            case "open":   return open(s, a);
            default:       return help(s);
        }
    }

    private void line(CommandSender s, String perm, String text) {
        if (s.hasPermission(perm)) s.sendMessage(text);
    }

    private boolean help(CommandSender s) {
        if (plugin.noPerm(s, "herplun.dm.help")) return true;
        s.sendMessage("§c§lHerPlun — меню (как DeluxeMenus)");
        line(s, "herplun.dm.help", "§e/herplun dm help §7— список команд меню");
        line(s, "herplun.dm.list", "§e/herplun dm list §7— доступные вам меню");
        line(s, "herplun.dm.open", "§e/herplun dm open <меню> §7— открыть меню себе");
        line(s, "herplun.dm.open.others", "§e/herplun dm open <меню> <игрок> §7— открыть меню другому игроку");
        line(s, "herplun.dm.reload", "§e/herplun dm reload §7— обновить меню из папки menus");
        s.sendMessage("§7Вместо §f/herplun dm §7можно писать §f/deluxemenus §7(или §f/dm§7).");
        return true;
    }

    private boolean reload(CommandSender s) {
        if (plugin.noPerm(s, "herplun.dm.reload")) return true;
        List<String> errors = plugin.menus().load();
        plugin.say(s, "§aМеню обновлены. Загружено: §e" + plugin.menus().count());
        for (int i = 0; i < Math.min(5, errors.size()); i++) s.sendMessage("§c - " + errors.get(i));
        if (errors.size() > 5) s.sendMessage("§c... и ещё " + (errors.size() - 5) + " (см. консоль)");
        return true;
    }

    private boolean list(CommandSender s) {
        if (plugin.noPerm(s, "herplun.dm.list")) return true;
        List<MenuManager.Menu> list = plugin.menus().accessible(s);
        if (list.isEmpty()) {
            plugin.say(s, "У вас нет доступных меню.");
            return true;
        }
        if (s instanceof Player) {
            List<BaseComponent> all = new ArrayList<>();
            addAll(all, TextComponent.fromLegacyText(plugin.prefix() + "Доступные меню (" + list.size() + "): "), null);
            for (int i = 0; i < list.size(); i++) {
                String name = list.get(i).name;
                addAll(all, TextComponent.fromLegacyText("§e" + name), "/herplun dm open " + name);
                if (i < list.size() - 1) addAll(all, TextComponent.fromLegacyText("§7, "), null);
            }
            ((Player) s).spigot().sendMessage(all.toArray(new BaseComponent[0]));
            s.sendMessage("§7Нажмите на название, чтобы открыть меню.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (MenuManager.Menu m : list) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(m.name);
            }
            plugin.say(s, "Меню (" + list.size() + "): " + sb);
        }
        return true;
    }

    private void addAll(List<BaseComponent> out, BaseComponent[] parts, String clickCommand) {
        for (BaseComponent c : parts) {
            if (clickCommand != null) c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, clickCommand));
            out.add(c);
        }
    }

    private boolean open(CommandSender s, String[] a) {
        boolean others = a.length >= 3;
        if (plugin.noPerm(s, others ? "herplun.dm.open.others" : "herplun.dm.open")) return true;
        if (a.length < 2) {
            plugin.say(s, "Использование: /herplun dm open <меню> [игрок]");
            return true;
        }
        MenuManager.Menu m = plugin.menus().get(a[1]);
        if (m == null) {
            plugin.say(s, "Меню §e" + a[1] + " §fне найдено. Список: §e/herplun dm list");
            return true;
        }
        if (others) {
            Player t = Bukkit.getPlayer(a[2]);
            if (t == null) {
                plugin.say(s, "Игрок не найден или не в сети.");
                return true;
            }
            plugin.menus().open(t, m, false);
            plugin.say(s, "Меню §e" + m.name + " §fоткрыто для §e" + t.getName() + "§f.");
        } else {
            if (!(s instanceof Player)) {
                plugin.say(s, "Из консоли укажите игрока: /herplun dm open <меню> <игрок>");
                return true;
            }
            plugin.menus().open((Player) s, m, true);
        }
        return true;
    }

    List<String> tab(CommandSender s, String[] a) {
        List<String> r = new ArrayList<>();
        if (a.length == 1) return Util.filter(a[0], "reload", "open", "list", "help");
        if (a[0].equalsIgnoreCase("open")) {
            if (a.length == 2) {
                for (MenuManager.Menu m : plugin.menus().accessible(s)) {
                    if (m.name.startsWith(a[1].toLowerCase(Locale.ROOT))) r.add(m.name);
                }
            } else if (a.length == 3 && s.hasPermission("herplun.dm.open.others")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[2].toLowerCase(Locale.ROOT))) r.add(p.getName());
                }
            }
        }
        return r;
    }
}
