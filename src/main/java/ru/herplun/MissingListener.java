package ru.herplun;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Сообщение, если WorldEdit / WorldGuard не установлены, а игрок пытается ими пользоваться. */
final class MissingListener implements Listener {

    private final HerPlun plugin;
    private final Map<UUID, Long> lastAxeMsg = new HashMap<>();

    MissingListener(HerPlun plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        if (!WorldTools.missingFor(e.getMessage())) return;
        e.setCancelled(true);
        WorldTools.sendMissing(plugin, e.getPlayer());
    }

    /** Клик топориком HerPlun без установленного WorldEdit. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_BLOCK && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || it.getType() != Material.WOODEN_AXE || !it.hasItemMeta()) return;
        ItemMeta m = it.getItemMeta();
        if (m == null || !WorldTools.AXE_NAME.equals(m.getDisplayName())) return;
        if (WorldTools.hasWorldEdit()) return;

        e.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastAxeMsg.get(e.getPlayer().getUniqueId());
        if (last == null || now - last > 3000) {
            lastAxeMsg.put(e.getPlayer().getUniqueId(), now);
            WorldTools.sendMissing(plugin, e.getPlayer());
        }
    }
}
