package ru.herplun;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Загрузка плагинов через HerPlun: папка plugins/HerPlun/plugins/.
 * Плагины загружаются при старте HerPlun в правильном порядке (с учётом depend/softdepend)
 * и выключаются вместе с ним.
 */
final class PluginHost {

    static final class Entry {
        final File file;
        String name;
        String version = "?";
        final List<String> depend = new ArrayList<>();
        final List<String> softdepend = new ArrayList<>();
        String status = "не обработан";
        boolean ok;

        Entry(File file) {
            this.file = file;
        }

        String title() {
            return name != null ? name : file.getName();
        }
    }

    private final HerPlun plugin;
    private final File dir;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Plugin> loaded = new ArrayList<>();

    PluginHost(HerPlun plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "plugins");
    }

    // ------------------------------------------------------------ загрузка

    void loadAll() {
        entries.clear();
        loaded.clear();
        dir.mkdirs();
        if (!plugin.cfgBool("plugins.enabled", true)) {
            plugin.getLogger().info("Загрузка плагинов через HerPlun отключена в config.yml.");
            return;
        }
        File[] jars = dir.listFiles();
        if (jars == null) return;
        Arrays.sort(jars);

        PluginManager pm = Bukkit.getPluginManager();
        Map<String, Entry> byName = new LinkedHashMap<>();
        for (File f : jars) {
            if (!f.isFile() || !f.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
            Entry e = new Entry(f);
            entries.add(e);
            try {
                readDescription(e);
            } catch (Exception ex) {
                e.status = "ошибка: " + ex.getMessage();
                plugin.getLogger().warning("[Плагины] " + f.getName() + " — " + e.status);
                continue;
            }
            if (pm.getPlugin(e.name) != null) {
                e.status = "пропущен: плагин уже загружен сервером (из папки plugins)";
                plugin.getLogger().warning("[Плагины] " + e.name + " — " + e.status);
                continue;
            }
            String key = e.name.toLowerCase(Locale.ROOT);
            if (byName.containsKey(key)) {
                e.status = "пропущен: такой плагин уже есть в этой папке";
                continue;
            }
            byName.put(key, e);
        }

        List<Entry> order = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> done = new HashSet<>();
        for (Entry e : byName.values()) visit(e, byName, visiting, done, order);

        for (Entry e : order) {
            String missing = null;
            for (String d : e.depend) {
                if (pm.getPlugin(d) == null) {
                    missing = d;
                    break;
                }
            }
            if (missing != null) {
                e.status = "не загружен: нужна зависимость " + missing;
                plugin.getLogger().warning("[Плагины] " + e.name + " — " + e.status);
                continue;
            }
            try {
                Plugin p = pm.loadPlugin(e.file);
                if (p == null) {
                    e.status = "не удалось загрузить";
                    plugin.getLogger().warning("[Плагины] " + e.name + " — " + e.status);
                    continue;
                }
                loaded.add(p);
                pm.enablePlugin(p);
                if (p.isEnabled()) {
                    e.ok = true;
                    e.status = "загружен и включён";
                    plugin.getLogger().info("[Плагины] Загружен: " + e.name + " " + e.version);
                } else {
                    e.status = "загружен, но не включился (смотрите ошибку выше в консоли)";
                    plugin.getLogger().warning("[Плагины] " + e.name + " — " + e.status);
                }
            } catch (Throwable t) {
                e.status = "ошибка: " + t;
                plugin.getLogger().warning("[Плагины] " + e.title() + " — " + e.status);
            }
        }
    }

    private void visit(Entry e, Map<String, Entry> byName, Set<String> visiting, Set<String> done, List<Entry> order) {
        String key = e.name.toLowerCase(Locale.ROOT);
        if (done.contains(key) || visiting.contains(key)) return;
        visiting.add(key);
        List<String> deps = new ArrayList<>(e.depend);
        deps.addAll(e.softdepend);
        for (String d : deps) {
            Entry de = byName.get(d.toLowerCase(Locale.ROOT));
            if (de != null && de != e) visit(de, byName, visiting, done, order);
        }
        visiting.remove(key);
        done.add(key);
        order.add(e);
    }

    private void readDescription(Entry e) throws Exception {
        try (ZipFile zf = new ZipFile(e.file)) {
            ZipEntry ze = zf.getEntry("plugin.yml");
            if (ze == null) {
                if (zf.getEntry("paper-plugin.yml") != null) {
                    throw new Exception("формат paper-plugin.yml — такие плагины кладите в обычную папку plugins");
                }
                throw new Exception("в файле нет plugin.yml — это не плагин");
            }
            try (InputStreamReader r = new InputStreamReader(zf.getInputStream(ze), StandardCharsets.UTF_8)) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(r);
                String name = y.getString("name");
                if (name == null || name.trim().isEmpty()) throw new Exception("в plugin.yml не указано имя");
                e.name = name.trim();
                Object v = y.get("version");
                e.version = v == null ? "?" : String.valueOf(v);
                e.depend.addAll(y.getStringList("depend"));
                e.softdepend.addAll(y.getStringList("softdepend"));
            }
        }
    }

    // ------------------------------------------------------------ выключение

    /** Выключает плагины, загруженные через HerPlun (в обратном порядке). */
    void disableAll() {
        List<Plugin> rev = new ArrayList<>(loaded);
        Collections.reverse(rev);
        for (Plugin p : rev) {
            try {
                if (p.isEnabled()) Bukkit.getPluginManager().disablePlugin(p);
            } catch (Throwable t) {
                plugin.getLogger().warning("[Плагины] Не удалось выключить " + p.getName() + ": " + t);
            }
        }
        loaded.clear();
    }

    // ------------------------------------------------------------ список

    void list(CommandSender s) {
        plugin.say(s, "Плагины через HerPlun. Папка: §eplugins/HerPlun/plugins/");
        if (entries.isEmpty()) {
            s.sendMessage("§7Папка пуста. Остановите сервер, положите .jar-файлы плагинов в эту папку и запустите сервер.");
            return;
        }
        for (Entry e : entries) {
            String ver = e.name != null ? " §7" + e.version : "";
            s.sendMessage((e.ok ? "§a✔ " : "§c✘ ") + "§f" + e.title() + ver + " §8— §7" + e.status);
        }
    }

    String folderPath() {
        return "plugins/HerPlun/plugins/";
    }
}
