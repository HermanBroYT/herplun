package ru.herplun;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/** Подстановка переменных. Если стоит PlaceholderAPI — используется и он. */
final class Placeholders {

    private static Method papi;

    private Placeholders() {
    }

    static String apply(Player p, String s) {
        if (s == null) return "";
        String r = s.replace("{player}", p.getName())
                .replace("%player_name%", p.getName())
                .replace("%player_displayname%", p.getDisplayName())
                .replace("%player_world%", p.getWorld().getName())
                .replace("%server_online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%server_max%", String.valueOf(Bukkit.getMaxPlayers()));
        return papi(p, r);
    }

    private static String papi(Player p, String s) {
        if (s.indexOf('%') < 0) return s;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return s;
        try {
            if (papi == null) {
                Class<?> c = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                papi = c.getMethod("setPlaceholders", Player.class, String.class);
            }
            Object o = papi.invoke(null, p, s);
            return o == null ? s : o.toString();
        } catch (Throwable t) {
            return s;
        }
    }
}
