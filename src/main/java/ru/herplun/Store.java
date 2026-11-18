package ru.herplun;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Хранилище данных. Файлы в plugins/HerPlun/:
 * bans.yml (баны), mutes.yml (муты), reports.yml (жалобы), state.yml (состояние).
 */
final class Store {

    static final class BanRecord {
        final String name;
        final String reason;
        final String by;
        final long time;
        final long until; // 0 = навсегда

        BanRecord(String name, String reason, String by, long time, long until) {
            this.name = name;
            this.reason = reason;
            this.by = by;
            this.time = time;
            this.until = until;
        }
    }

    static final class MuteRecord {
        final String name;
        final String by;
        final long time;
        final long until; // 0 = навсегда

        MuteRecord(String name, String by, long time, long until) {
            this.name = name;
            this.by = by;
            this.time = time;
            this.until = until;
        }
    }

    static final class Report {
        final int id;
        final String reporterUuid;
        final String reporterName;
        final String target;
        final String reason;
        final long time;

        Report(int id, String reporterUuid, String reporterName, String target, String reason, long time) {
            this.id = id;
            this.reporterUuid = reporterUuid;
            this.reporterName = reporterName;
            this.target = target;
            this.reason = reason;
            this.time = time;
        }
    }

    final Map<String, BanRecord> bans = new ConcurrentHashMap<>();
    final Map<String, MuteRecord> mutes = new ConcurrentHashMap<>();
    final Map<Integer, Report> reports = new ConcurrentSkipListMap<>();

    private final HerPlun plugin;
    private final File bansFile;
    private final File mutesFile;
    private final File reportsFile;
    private final File stateFile;
    private int nextReportId = 1;
    private volatile boolean technical;

    Store(HerPlun plugin) {
        this.plugin = plugin;
        File dir = plugin.getDataFolder();
        this.bansFile = new File(dir, "bans.yml");
        this.mutesFile = new File(dir, "mutes.yml");
        this.reportsFile = new File(dir, "reports.yml");
        this.stateFile = new File(dir, "state.yml");
    }

    // ------------------------------------------------------------ загрузка

    void loadAll() {
        plugin.getDataFolder().mkdirs();
        try {
            loadBans();
            loadMutes();
            loadReports();
            loadState();
        } catch (Exception ex) {
            plugin.getLogger().warning("Ошибка чтения файлов данных: " + ex);
        }
    }

    private void loadBans() {
        bans.clear();
        ConfigurationSection sec = YamlConfiguration.loadConfiguration(bansFile).getConfigurationSection("bans");
        if (sec == null) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection b = sec.getConfigurationSection(key);
            if (b == null) continue;
            long until = b.getLong("until", 0L);
            if (until > 0 && until <= now) {
                changed = true;
                continue;
            }
            bans.put(key.toLowerCase(Locale.ROOT), new BanRecord(b.getString("name", key),
                    b.getString("reason", "?"), b.getString("by", "?"), b.getLong("time", 0L), until));
        }
        if (changed) saveBans();
    }

    private void loadMutes() {
        mutes.clear();
        ConfigurationSection sec = YamlConfiguration.loadConfiguration(mutesFile).getConfigurationSection("mutes");
        if (sec == null) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection m = sec.getConfigurationSection(key);
            if (m == null) continue;
            long until = m.getLong("until", 0L);
            if (until > 0 && until <= now) {
                changed = true;
                continue;
            }
            mutes.put(key.toLowerCase(Locale.ROOT), new MuteRecord(m.getString("name", key),
                    m.getString("by", "?"), m.getLong("time", 0L), until));
        }
        if (changed) saveMutes();
    }

    private void loadReports() {
        reports.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(reportsFile);
        nextReportId = Math.max(1, y.getInt("next-id", 1));
        ConfigurationSection sec = y.getConfigurationSection("reports");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection r = sec.getConfigurationSection(key);
            if (r == null) continue;
            int id;
            try {
                id = Integer.parseInt(key.substring(1));
            } catch (Exception ex) {
                continue;
            }
            reports.put(id, new Report(id, r.getString("reporter-uuid", ""), r.getString("reporter", "?"),
                    r.getString("target", "?"), r.getString("reason", "?"), r.getLong("time", 0L)));
            if (id >= nextReportId) nextReportId = id + 1;
        }
    }

    private void loadState() {
        technical = YamlConfiguration.loadConfiguration(stateFile).getBoolean("technical", false);
    }

    // ------------------------------------------------------------ сохранение

    private void write(YamlConfiguration y, File f) {
        try {
            y.save(f);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить " + f.getName() + ": " + ex.getMessage());
        }
    }

    synchronized void saveBans() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, BanRecord> en : bans.entrySet()) {
            String k = "bans." + en.getKey();
            BanRecord b = en.getValue();
            y.set(k + ".name", b.name);
            y.set(k + ".reason", b.reason);
            y.set(k + ".by", b.by);
            y.set(k + ".time", b.time);
            y.set(k + ".until", b.until);
        }
        write(y, bansFile);
    }

    synchronized void saveMutes() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, MuteRecord> en : mutes.entrySet()) {
            String k = "mutes." + en.getKey();
            MuteRecord m = en.getValue();
            y.set(k + ".name", m.name);
            y.set(k + ".by", m.by);
            y.set(k + ".time", m.time);
            y.set(k + ".until", m.until);
        }
        write(y, mutesFile);
    }

    synchronized void saveReports() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-id", nextReportId);
        for (Report r : reports.values()) {
            String k = "reports.r" + r.id;
            y.set(k + ".reporter-uuid", r.reporterUuid);
            y.set(k + ".reporter", r.reporterName);
            y.set(k + ".target", r.target);
            y.set(k + ".reason", r.reason);
            y.set(k + ".time", r.time);
        }
        write(y, reportsFile);
    }

    synchronized void saveState() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("technical", technical);
        write(y, stateFile);
    }

    void saveAll() {
        saveBans();
        saveMutes();
        saveReports();
        saveState();
    }

    // ------------------------------------------------------------ муты

    /** Возвращает активный мут или null (просроченный удаляется). */
    MuteRecord getMute(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        MuteRecord m = mutes.get(key);
        if (m == null) return null;
        if (m.until > 0 && System.currentTimeMillis() >= m.until) {
            mutes.remove(key);
            saveMutes();
            return null;
        }
        return m;
    }

    // ------------------------------------------------------------ жалобы

    synchronized Report addReport(String reporterUuid, String reporterName, String target, String reason) {
        Report r = new Report(nextReportId++, reporterUuid, reporterName, target, reason, System.currentTimeMillis());
        reports.put(r.id, r);
        saveReports();
        return r;
    }

    synchronized boolean removeReport(int id) {
        boolean ok = reports.remove(id) != null;
        if (ok) saveReports();
        return ok;
    }

    int countReports(String reporterUuid) {
        int n = 0;
        for (Report r : reports.values()) if (r.reporterUuid.equals(reporterUuid)) n++;
        return n;
    }

    // ------------------------------------------------------------ тех. работы

    boolean isTechnical() {
        return technical;
    }

    void setTechnical(boolean value) {
        this.technical = value;
        saveState();
    }
}
