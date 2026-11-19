package ru.herplun;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Свой текст при попытке сломать блок на чужой приватной территории WorldGuard.
 * Работает через reflection — HerPlun не зависит от версии WorldGuard при сборке.
 */
final class WorldGuardHook implements Listener {

    private static final String DEF_MSG =
            "&cВы не сможете сломать, так как он за приватность территории игрока";

    private final HerPlun plugin;
    private final Map<UUID, Long> lastMsg = new HashMap<>();
    private boolean failedOnce;

    private Method wgGetInstance;
    private Method getPlatform;
    private Method getRegionContainer;
    private Method createQuery;
    private Method testBuild;
    private Method adaptLoc;
    private Method wgpInst;
    private Method wrapPlayer;
    private Object flagArray;

    WorldGuardHook(HerPlun plugin) {
        this.plugin = plugin;
    }

    boolean init() {
        try {
            Class<?> wgCls = Class.forName("com.sk89q.worldguard.WorldGuard");
            Class<?> platformCls = Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform");
            Class<?> containerCls = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
            Class<?> queryCls = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery");
            Class<?> weLocCls = Class.forName("com.sk89q.worldedit.util.Location");
            Class<?> assocCls = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable");
            Class<?> flagCls = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Class<?> flagsCls = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Class<?> adapterCls = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Class<?> wgpCls = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");

            wgGetInstance = wgCls.getMethod("getInstance");
            getPlatform = wgCls.getMethod("getPlatform");
            getRegionContainer = platformCls.getMethod("getRegionContainer");
            createQuery = containerCls.getMethod("createQuery");
            testBuild = queryCls.getMethod("testBuild", weLocCls, assocCls, Array.newInstance(flagCls, 0).getClass());
            adaptLoc = adapterCls.getMethod("adapt", Location.class);
            wgpInst = wgpCls.getMethod("inst");
            wrapPlayer = wgpCls.getMethod("wrapPlayer", Player.class);

            Object breakFlag = flagsCls.getField("BLOCK_BREAK").get(null);
            flagArray = Array.newInstance(flagCls, 1);
            Array.set(flagArray, 0, breakFlag);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("WorldGuard: " + t);
            return false;
        }
    }

    private boolean canBreak(Player p, Location loc) throws Exception {
        Object wg = wgGetInstance.invoke(null);
        Object platform = getPlatform.invoke(wg);
        Object container = getRegionContainer.invoke(platform);
        Object query = createQuery.invoke(container);
        Object weLoc = adaptLoc.invoke(null, loc);
        Object lp = wrapPlayer.invoke(wgpInst.invoke(null), p);
        return (Boolean) testBuild.invoke(query, weLoc, lp, flagArray);
    }

    // LOWEST: мы отменяем событие раньше WorldGuard, поэтому его сообщение не показывается
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.cfgBool("worldguard.custom-message", true)) return;
        Player p = e.getPlayer();
        if (p.hasPermission("worldguard.region.bypass." + e.getBlock().getWorld().getName())) return;
        try {
            if (canBreak(p, e.getBlock().getLocation())) return;
        } catch (Throwable t) {
            if (!failedOnce) {
                failedOnce = true;
                plugin.getLogger().warning("WorldGuard: не удалось проверить приват: " + t);
            }
            return;
        }
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastMsg.get(p.getUniqueId());
        if (last == null || now - last > 1500) {
            lastMsg.put(p.getUniqueId(), now);
            p.sendMessage(plugin.cfg("messages.region-break", DEF_MSG));
        }
    }
}
