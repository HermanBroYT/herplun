package ru.herplun;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Простой анти-чит: только предупреждает персонал (право herplun.anti-cheat).
 * Проверки: Fly (зависание/подъём в воздухе), Speed, Reach, KillAura (удар за спину).
 * Наказаний не выдаёт — возможны ложные срабатывания.
 */
final class AntiCheat implements Listener {

    private static final class Data {
        int hover;
        int up;
        double riseStartY;
        long speedStart;
        double speedDist;
        int speedStrikes;
        long exemptUntil;
        final Map<String, Integer> vl = new HashMap<>();
        final Map<String, Long> lastFlag = new HashMap<>();
        final Map<String, Long> lastAlert = new HashMap<>();
    }

    private final HerPlun plugin;
    private final Map<UUID, Data> data = new HashMap<>();

    AntiCheat(HerPlun plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.cfgBool("anticheat.enabled", true);
    }

    private Data data(Player p) {
        Data d = data.get(p.getUniqueId());
        if (d == null) {
            d = new Data();
            data.put(p.getUniqueId(), d);
        }
        return d;
    }

    private void reset(Data d) {
        d.hover = 0;
        d.up = 0;
        d.speedStart = 0;
        d.speedDist = 0;
        d.speedStrikes = 0;
    }

    // ------------------------------------------------------------ исключения

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        data(e.getPlayer()).exemptUntil = System.currentTimeMillis() + 5000;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        data.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        Data d = data(e.getPlayer());
        d.exemptUntil = System.currentTimeMillis() + 3000;
        reset(d);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Data d = data(e.getPlayer());
        d.exemptUntil = System.currentTimeMillis() + 3000;
        reset(d);
    }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent e) {
        Data d = data(e.getPlayer());
        d.exemptUntil = Math.max(d.exemptUntil, System.currentTimeMillis() + 1500);
        d.up = 0;
        d.hover = 0;
    }

    private int effectLevel(Player p, String... names) {
        for (PotionEffect pe : p.getActivePotionEffects()) {
            String n = pe.getType().getName();
            if (n == null) continue;
            for (String x : names) {
                if (n.equalsIgnoreCase(x)) return pe.getAmplifier() + 1;
            }
        }
        return 0;
    }

    private boolean holdingTrident(Player p) {
        return p.getInventory().getItemInMainHand().getType().name().contains("TRIDENT")
                || p.getInventory().getItemInOffHand().getType().name().contains("TRIDENT");
    }

    private boolean exempt(Player p, Data d, long now) {
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true;
        if (p.getAllowFlight() || p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isDead()) return true;
        if (now < d.exemptUntil) return true;
        if (effectLevel(p, "LEVITATION", "JUMP", "JUMP_BOOST") > 0) return true;
        return holdingTrident(p);
    }

    // ------------------------------------------------------------ движение

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!enabled()) return;
        Player p = e.getPlayer();
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return;
        if (plugin.checks().containsKey(p.getUniqueId())) return;

        Data d = data(p);
        long now = System.currentTimeMillis();
        if (exempt(p, d, now)) {
            reset(d);
            return;
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        checkFly(p, d, from, to, dy, horiz);
        checkSpeed(p, d, to, horiz, now);
    }

    /** Есть ли опора под ногами (с учётом ширины хитбокса). */
    private boolean supported(Location l) {
        double[] offs = {-0.31, 0, 0.31};
        double[] ys = {0, -0.001, -0.501};
        for (double y : ys) {
            for (double x : offs) {
                for (double z : offs) {
                    if (!l.clone().add(x, y, z).getBlock().isEmpty()) return true;
                }
            }
        }
        return false;
    }

    private boolean entityBelow(Player p) {
        for (Entity en : p.getNearbyEntities(0.9, 2.0, 0.9)) {
            if (en != p && !(en instanceof Item)) return true;
        }
        return false;
    }

    private void checkFly(Player p, Data d, Location from, Location to, double dy, double horiz) {
        if (dy < -1.0E-5) {
            d.hover = 0;
            d.up = 0;
            return;
        }
        // быстрый выход: стоим на блоке
        if (!to.clone().subtract(0, 0.001, 0).getBlock().isEmpty() || supported(to)) {
            d.hover = 0;
            d.up = 0;
            return;
        }
        if (dy > 1.0E-5) {
            if (d.up == 0) d.riseStartY = from.getY();
            d.up++;
            d.hover = 0;
            if (d.up >= 15 && to.getY() - d.riseStartY > 3.0) {
                d.up = 0;
                flag(p, d, "Fly", "подъём в воздухе");
            }
        } else if (horiz > 0.05) {
            d.up = 0;
            d.hover++;
            if (d.hover >= 12) {
                d.hover = 0;
                if (!entityBelow(p)) flag(p, d, "Fly", "зависание в воздухе");
            }
        }
    }

    private void checkSpeed(Player p, Data d, Location to, double horiz, long now) {
        String under = to.clone().subtract(0, 0.5, 0).getBlock().getType().name();
        if (under.contains("ICE")) {
            d.speedStart = 0;
            d.speedDist = 0;
            return;
        }
        if (d.speedStart == 0) {
            d.speedStart = now;
            d.speedDist = 0;
        }
        d.speedDist += horiz;
        long elapsed = now - d.speedStart;
        if (elapsed < 1000) return;

        double bps = d.speedDist / (elapsed / 1000.0);
        d.speedStart = 0;
        d.speedDist = 0;

        double max = plugin.cfgDouble("anticheat.speed.max-bps", 16.0);
        max *= Math.max(0.5, p.getWalkSpeed() / 0.2);
        max *= 1.0 + 0.2 * effectLevel(p, "SPEED");

        if (bps > max) {
            d.speedStrikes++;
            if (d.speedStrikes >= 2) {
                d.speedStrikes = 0;
                flag(p, d, "Speed", String.format(Locale.US, "%.1f б/с", bps));
            }
        } else {
            d.speedStrikes = 0;
        }
    }

    // ------------------------------------------------------------ удары

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!enabled()) return;
        if (!(e.getDamager() instanceof Player) || !(e.getEntity() instanceof Player)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        Player p = (Player) e.getDamager();
        Player v = (Player) e.getEntity();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (!p.getWorld().equals(v.getWorld())) return;

        Data d = data(p);
        Location eye = p.getEyeLocation();
        Location feet = v.getLocation();
        double h = ((LivingEntity) v).getEyeHeight();
        Location mid = feet.clone().add(0, h * 0.5, 0);
        Location head = feet.clone().add(0, h, 0);

        double dist = Math.min(eye.distance(feet), Math.min(eye.distance(mid), eye.distance(head)));
        double max = plugin.cfgDouble("anticheat.reach.max", 5.0);
        if (dist > max) {
            flag(p, d, "Reach", String.format(Locale.US, "%.1f блока", dist));
        }

        if (dist > 2.0) {
            Vector dir = mid.toVector().subtract(eye.toVector()).normalize();
            double dot = eye.getDirection().dot(dir);
            if (dot < 0.0) {
                flag(p, d, "KillAura", "удар не глядя на цель");
            }
        }
    }

    // ------------------------------------------------------------ оповещение

    private void flag(Player p, Data d, String check, String info) {
        long now = System.currentTimeMillis();
        Long last = d.lastFlag.get(check);
        if (last != null && now - last > 30_000) d.vl.put(check, 0);

        Integer cur = d.vl.get(check);
        int vl = (cur == null ? 0 : cur) + 1;
        d.vl.put(check, vl);
        d.lastFlag.put(check, now);

        if (vl < plugin.cfgInt("anticheat.alert-after", 2)) return;

        long cooldown = plugin.cfgInt("anticheat.alert-cooldown", 5) * 1000L;
        Long lastAlert = d.lastAlert.get(check);
        if (lastAlert != null && now - lastAlert < cooldown) return;
        d.lastAlert.put(check, now);

        String msg = plugin.prefix() + "§cАнти-чит: §fигрок §e" + p.getName()
                + " §fвозможно играет с читами: §c" + check + " §7(" + info + ", VL " + vl + ")";
        plugin.notifyStaff(msg, "herplun.anti-cheat");
    }
}
