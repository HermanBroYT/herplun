package ru.herplun;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Меню жалоб (/report open).
 * Обычный игрок видит только свои жалобы и может их отменить (ЛКМ).
 * Модератор (herplun.check.on) видит все жалобы и может закрыть любую (ПКМ).
 */
final class ReportMenu implements InventoryHolder {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 53;
    private static final Set<UUID> OPEN = new HashSet<>();

    private final HerPlun plugin;
    private final Player viewer;
    private final List<Integer> ids = new ArrayList<>();
    private Inventory inv;
    private int page;

    private ReportMenu(HerPlun plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    static void open(Player viewer) {
        HerPlun plugin = JavaPlugin.getPlugin(HerPlun.class);
        ReportMenu m = new ReportMenu(plugin, viewer);
        m.render();
        viewer.openInventory(m.inv);
        OPEN.add(viewer.getUniqueId());
    }

    /** Закрыть все открытые меню жалоб (при выключении плагина). */
    static void closeAll() {
        for (UUID id : new java.util.ArrayList<>(OPEN)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.closeInventory();
        }
        OPEN.clear();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    private boolean staff() {
        return viewer.hasPermission("herplun.check.on");
    }

    private boolean own(Store.Report r) {
        return r.reporterUuid.equals(viewer.getUniqueId().toString());
    }

    private List<Store.Report> visible() {
        List<Store.Report> list = new ArrayList<>();
        boolean staff = staff();
        for (Store.Report r : plugin.store().reports.values()) {
            if (staff || own(r)) list.add(r);
        }
        return list;
    }

    private void render() {
        List<Store.Report> all = visible();
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;

        if (inv == null) inv = Bukkit.createInventory(this, 54, "HerPlun: Жалобы");
        inv.clear();
        ids.clear();

        int from = page * PAGE_SIZE;
        int to = Math.min(all.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Store.Report r = all.get(i);
            inv.setItem(i - from, reportItem(r));
            ids.add(r.id);
        }

        if (page > 0) inv.setItem(SLOT_PREV, named(Material.ARROW, "§e← Назад"));
        if (page < pages - 1) inv.setItem(SLOT_NEXT, named(Material.ARROW, "§eВперёд →"));

        List<String> lore = new ArrayList<>();
        lore.add("§7Страница: §f" + (page + 1) + "/" + pages);
        lore.add("§7Жалоб: §f" + all.size());
        inv.setItem(SLOT_CLOSE, named(Material.BARRIER, "§cЗакрыть", lore));
    }

    private ItemStack named(Material m, String name) {
        return named(m, name, null);
    }

    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack reportItem(Store.Report r) {
        boolean own = own(r);
        List<String> lore = new ArrayList<>();
        lore.add("§7От: §f" + r.reporterName);
        lore.add("§7На игрока: §c" + r.target);
        lore.add("§7Дата: §f" + Util.date(r.time));
        String reason = r.reason;
        boolean first = true;
        while (!reason.isEmpty()) {
            int n = Math.min(32, reason.length());
            lore.add((first ? "§7Причина: §f" : "§f") + reason.substring(0, n));
            reason = reason.substring(n);
            first = false;
        }
        lore.add("");
        if (own) lore.add("§aЛКМ §7— отменить свою жалобу");
        if (staff()) lore.add("§cПКМ §7— закрыть (рассмотрена)");
        return named(own ? Material.BOOK : Material.PAPER, "§eЖалоба #" + r.id, lore);
    }

    private void click(int slot, ClickType type) {
        if (slot == SLOT_PREV && page > 0) {
            page--;
            render();
            return;
        }
        if (slot == SLOT_NEXT) {
            page++;
            render();
            return;
        }
        if (slot == SLOT_CLOSE) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    viewer.closeInventory();
                }
            });
            return;
        }
        if (slot < 0 || slot >= ids.size()) return;

        Store.Report r = plugin.store().reports.get(ids.get(slot));
        if (r == null) {
            render();
            return;
        }
        boolean left = type == ClickType.LEFT || type == ClickType.SHIFT_LEFT;
        boolean right = type == ClickType.RIGHT || type == ClickType.SHIFT_RIGHT;

        if (left && own(r)) {
            plugin.store().removeReport(r.id);
            plugin.say(viewer, "Жалоба §e#" + r.id + " §fотменена.");
            render();
        } else if (right && staff()) {
            plugin.store().removeReport(r.id);
            plugin.say(viewer, "Жалоба §e#" + r.id + " §fзакрыта.");
            Player reporter = null;
            try {
                reporter = Bukkit.getPlayer(java.util.UUID.fromString(r.reporterUuid));
            } catch (IllegalArgumentException ignored) {
            }
            if (reporter != null && !reporter.equals(viewer)) {
                plugin.say(reporter, "Ваша жалоба §e#" + r.id + " §fна §c" + r.target + " §fрассмотрена.");
            }
            render();
        }
    }

    /** Обработчик кликов по меню. */
    static final class Events implements Listener {

        @EventHandler
        public void onClick(InventoryClickEvent e) {
            Inventory top = e.getInventory();
            if (!(top.getHolder() instanceof ReportMenu)) return;
            e.setCancelled(true);
            if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;
            ((ReportMenu) top.getHolder()).click(e.getRawSlot(), e.getClick());
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getInventory().getHolder() instanceof ReportMenu) e.setCancelled(true);
        }

        @EventHandler
        public void onClose(InventoryCloseEvent e) {
            if (e.getInventory().getHolder() instanceof ReportMenu) OPEN.remove(e.getPlayer().getUniqueId());
        }
    }
}
