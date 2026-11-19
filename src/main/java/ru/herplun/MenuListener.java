package ru.herplun;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Locale;

/** Клики по меню, закрытие меню, команды-открывашки (open_command). */
final class MenuListener implements Listener {

    private final HerPlun plugin;

    MenuListener(HerPlun plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getInventory();
        if (!(top.getHolder() instanceof MenuManager.Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;

        Inventory clicked = e.getClickedInventory();
        if (clicked == null || !clicked.equals(top)) return;

        final Player p = (Player) e.getWhoClicked();
        MenuManager.Holder h = (MenuManager.Holder) top.getHolder();
        MenuManager.Item it = h.slots.get(e.getRawSlot());
        if (it == null) return;

        if (it.clickPermission != null && !p.hasPermission(it.clickPermission)) {
            p.sendMessage(it.denyMessage != null ? Util.color(it.denyMessage)
                    : plugin.cfg("messages.no-permission", HerPlun.DEF_NO_PERM));
            return;
        }

        String key;
        ClickType t = e.getClick();
        if (t == ClickType.LEFT) key = "left";
        else if (t == ClickType.RIGHT) key = "right";
        else if (t == ClickType.SHIFT_LEFT) key = "shift_left";
        else if (t == ClickType.SHIFT_RIGHT) key = "shift_right";
        else if (t == ClickType.MIDDLE) key = "middle";
        else key = "any";

        List<String> acts = it.actions.get(key);
        if ((acts == null || acts.isEmpty()) && key.equals("shift_left")) acts = it.actions.get("left");
        if ((acts == null || acts.isEmpty()) && key.equals("shift_right")) acts = it.actions.get("right");
        if (acts == null || acts.isEmpty()) acts = it.actions.get("any");
        if (acts == null || acts.isEmpty()) return;

        final List<String> run = acts;
        // выполняем на следующем тике: безопасно для открытия/закрытия окон
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                Actions.run(plugin, p, run);
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MenuManager.Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuManager.Holder)) return;
        if (!(e.getPlayer() instanceof Player)) return;
        plugin.menus().onClosed((Player) e.getPlayer(), (MenuManager.Holder) e.getInventory().getHolder());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (plugin.checks().containsKey(p.getUniqueId())) return;
        String msg = e.getMessage().trim();
        if (msg.startsWith("/")) msg = msg.substring(1);
        String label = msg.split("\\s+")[0].toLowerCase(Locale.ROOT);
        MenuManager.Menu m = plugin.menus().byAlias(label);
        if (m == null) return;
        e.setCancelled(true);
        plugin.menus().open(p, m, true);
    }
}
