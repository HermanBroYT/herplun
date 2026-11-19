package ru.herplun;

import org.bukkit.Bukkit;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Заморозка на проверке, муты, технические работы. */
final class EventListener implements Listener {

    private static final List<String> DEF_BLOCKED = Arrays.asList(
            "msg", "tell", "w", "whisper", "m", "t", "pm", "r", "reply", "me", "say");

    private final HerPlun plugin;

    EventListener(HerPlun plugin) {
        this.plugin = plugin;
    }

    private boolean frozen(Player p) {
        return plugin.checks().containsKey(p.getUniqueId());
    }

    // ---------------------------------------------------- технические работы

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent e) {
        if (!plugin.store().isTechnical()) return;
        if (e.getPlayer().isOp()) return;
        e.disallow(PlayerLoginEvent.Result.KICK_OTHER, plugin.cfg("messages.technical-kick",
                "&cНа сервере проводятся технические работы.\n&fЗаходить могут только администраторы."));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (plugin.store().isTechnical() && p.hasPermission("herplun.technical")) {
            plugin.say(p, "§eТехнические работы ВКЛЮЧЕНЫ. Выключить: /herplun technical off");
        }
    }

    // ---------------------------------------------------- проверка на читы

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
        Check ch = plugin.checks().remove(e.getPlayer().getUniqueId());
        if (ch == null) return;
        plugin.ban(ch.targetName, "Выход во время проверки на читы", "HerPlun", 0);
        plugin.notifyStaff(plugin.prefix() + "Игрок §e" + ch.targetName
                + " §fвышел во время проверки и забанен навсегда.", "herplun.check.on");
    }

    // ---------------------------------------------------- муты

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Store.MuteRecord m = plugin.store().getMute(e.getPlayer().getName());
        if (m == null) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(plugin.prefix() + plugin.muteText(m));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (frozen(p)) {
            e.setCancelled(true);
            plugin.say(p, "Во время проверки команды запрещены.");
            return;
        }
        Store.MuteRecord m = plugin.store().getMute(p.getName());
        if (m == null) return;

        String label = e.getMessage().trim();
        if (label.startsWith("/")) label = label.substring(1);
        label = label.split(" ")[0].toLowerCase(Locale.ROOT);
        int i = label.indexOf(':');
        if (i >= 0) label = label.substring(i + 1);

        List<String> blocked = plugin.getConfig().isList("mute.blocked-commands")
                ? plugin.getConfig().getStringList("mute.blocked-commands") : DEF_BLOCKED;
        for (String b : blocked) {
            if (b != null && b.equalsIgnoreCase(label)) {
                e.setCancelled(true);
                p.sendMessage(plugin.prefix() + plugin.muteText(m));
                return;
            }
        }
    }
}
