package ru.herplun;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Действия меню (как в DeluxeMenus):
 * [player] команда   — игрок вводит команду
 * [console] команда  — консоль выполняет команду
 * [message] текст    — сообщение игроку
 * [broadcast] текст  — сообщение всем
 * [chat] текст       — игрок пишет в чат
 * [close]            — закрыть меню
 * [open] меню        — открыть другое меню
 * [sound] ключ [громкость] [тон] — звук (например block.chest.open)
 * [title] заголовок;подзаголовок
 * Команду пишите без "/" или с одним "/". Для WorldEdit: //pos1 = команда "/pos1".
 */
final class Actions {

    private Actions() {
    }

    static void run(HerPlun plugin, Player p, List<String> actions) {
        if (actions == null) return;
        for (String raw : actions) {
            if (raw == null || raw.trim().isEmpty()) continue;
            try {
                runOne(plugin, p, Placeholders.apply(p, raw).trim());
            } catch (Exception ex) {
                plugin.getLogger().warning("Ошибка действия меню '" + raw + "': " + ex);
            }
        }
    }

    private static String stripSlash(String c) {
        return c.startsWith("/") ? c.substring(1) : c;
    }

    private static float num(String s, float def) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static void runOne(HerPlun plugin, Player p, String line) {
        String type = "player";
        String arg = line;
        if (line.startsWith("[")) {
            int end = line.indexOf(']');
            if (end > 0) {
                type = line.substring(1, end).trim().toLowerCase(Locale.ROOT);
                arg = line.substring(end + 1).trim();
            }
        }
        switch (type) {
            case "player":
                if (arg.isEmpty()) break;
                if (WorldTools.missingFor(arg)) {
                    WorldTools.sendMissing(plugin, p);
                    break;
                }
                p.performCommand(stripSlash(arg));
                break;
            case "console":
                if (!arg.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(arg));
                break;
            case "message":
                p.sendMessage(Util.color(arg));
                break;
            case "broadcast":
                Bukkit.broadcastMessage(Util.color(arg));
                break;
            case "chat":
                p.chat(arg);
                break;
            case "close":
                p.closeInventory();
                break;
            case "open": {
                MenuManager.Menu m = plugin.menus().get(arg);
                if (m == null) {
                    plugin.getLogger().warning("Действие [open]: меню '" + arg + "' не найдено.");
                } else {
                    plugin.menus().open(p, m, true);
                }
                break;
            }
            case "sound": {
                String[] sp = arg.split("\\s+");
                if (sp[0].isEmpty()) break;
                String key = sp[0].toLowerCase(Locale.ROOT);
                if (!key.contains(".")) key = key.replace('_', '.');
                float vol = sp.length > 1 ? num(sp[1], 1f) : 1f;
                float pitch = sp.length > 2 ? num(sp[2], 1f) : 1f;
                p.playSound(p.getLocation(), key, vol, pitch);
                break;
            }
            case "title": {
                String[] t = arg.split(";", 2);
                p.sendTitle(Util.color(t[0]), t.length > 1 ? Util.color(t[1]) : "", 10, 60, 10);
                break;
            }
            default:
                plugin.getLogger().warning("Неизвестное действие меню: [" + type + "]");
        }
    }
}
