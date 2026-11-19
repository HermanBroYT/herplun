package ru.herplun;

import org.bukkit.ChatColor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Вспомогательные функции. */
final class Util {

    static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_.\\-]{1,36}");
    private static final Pattern TIME = Pattern.compile("(\\d+)([smhdw])");

    private Util() {
    }

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    /** & -> цветовые коды, поддерживаются и HEX-цвета вида &#RRGGBB. */
    static String color(String s) {
        Matcher m = HEX.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            StringBuilder r = new StringBuilder("\u00a7x");
            for (char c : m.group(1).toCharArray()) r.append('\u00a7').append(c);
            m.appendReplacement(sb, Matcher.quoteReplacement(r.toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    static String join(String[] a, int from) {
        return String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }

    static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    static String date(long ms) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(ms));
    }

    /** "1d2h30m10s" -> миллисекунды, либо -1 при ошибке. */
    static long parseTime(String in) {
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
            if (n > 100000) return -1;
            switch (m.group(2)) {
                case "s": total += n * 1000L; break;
                case "m": total += n * 60_000L; break;
                case "h": total += n * 3_600_000L; break;
                case "d": total += n * 86_400_000L; break;
                case "w": total += n * 604_800_000L; break;
                default: return -1;
            }
        }
        return end == in.length() ? total : -1;
    }

    static String formatMs(long ms) {
        long sec = Math.max(0, ms / 1000);
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

    static List<String> filter(String prefix, String... options) {
        List<String> r = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) r.add(o);
        return r;
    }
}
