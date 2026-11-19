package ru.herplun;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Меню в стиле DeluxeMenus. Файлы меню: plugins/HerPlun/menus/*.yml */
final class MenuManager {

    static final class Item {
        String key;
        int priority;
        final List<Integer> slots = new ArrayList<>();
        Material material = Material.STONE;
        String skullOwner;
        String name;
        final List<String> lore = new ArrayList<>();
        int amount = 1;
        boolean glow;
        String permission;       // кто видит предмет
        String clickPermission;  // кто может нажимать
        String denyMessage;
        final Map<String, List<String>> actions = new HashMap<>();
    }

    static final class Menu {
        String name;
        String title;
        int size;
        String permission;
        String denyMessage;
        final List<String> aliases = new ArrayList<>();
        List<String> openActions = new ArrayList<>();
        List<String> closeActions = new ArrayList<>();
        Item fill;
        final List<Item> items = new ArrayList<>();
    }

    static final class Holder implements InventoryHolder {
        final Menu menu;
        final Map<Integer, Item> slots = new HashMap<>();
        Inventory inv;

        Holder(Menu menu) {
            this.menu = menu;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final HerPlun plugin;
    private final File dir;
    private volatile Map<String, Menu> menus = new TreeMap<>();
    private volatile Map<String, Menu> aliases = new HashMap<>();
    private final Set<UUID> viewers = new HashSet<>();

    MenuManager(HerPlun plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "menus");
    }

    // ------------------------------------------------------------ загрузка

    /** Создаёт примеры меню при первом запуске. */
    void saveDefaults() {
        if (dir.exists()) return;
        dir.mkdirs();
        for (String n : new String[]{"example.yml", "tools.yml"}) {
            try {
                plugin.saveResource("menus/" + n, false);
            } catch (Exception ex) {
                plugin.getLogger().warning("Не удалось создать menus/" + n + ": " + ex.getMessage());
            }
        }
    }

    /** Загружает все меню. Возвращает список ошибок. */
    List<String> load() {
        List<String> errors = new ArrayList<>();
        Map<String, Menu> nm = new TreeMap<>();
        Map<String, Menu> na = new HashMap<>();
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                String fn = f.getName();
                if (!f.isFile() || !fn.toLowerCase(Locale.ROOT).endsWith(".yml")) continue;
                String name = fn.substring(0, fn.length() - 4).toLowerCase(Locale.ROOT);
                try {
                    Menu m = parse(name, YamlConfiguration.loadConfiguration(f), errors);
                    nm.put(name, m);
                    for (String al : m.aliases) {
                        if (na.containsKey(al)) errors.add(name + ": команда /" + al + " уже занята меню " + na.get(al).name);
                        na.put(al, m);
                    }
                } catch (Exception ex) {
                    errors.add(fn + ": " + ex);
                }
            }
        }
        menus = nm;
        aliases = na;
        for (String er : errors) plugin.getLogger().warning("[Меню] " + er);
        return errors;
    }

    private String nullIfEmpty(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private Menu parse(String name, YamlConfiguration y, List<String> errors) {
        Menu m = new Menu();
        m.name = name;
        m.title = y.getString("menu_title", name);
        int size = y.getInt("size", 27);
        if (size < 9) size = 9;
        if (size % 9 != 0) size = (size / 9 + 1) * 9;
        if (size > 54) size = 54;
        m.size = size;
        m.permission = nullIfEmpty(y.getString("permission"));
        m.denyMessage = nullIfEmpty(y.getString("no_permission_message"));

        List<String> oc = new ArrayList<>();
        if (y.isList("open_command")) oc.addAll(y.getStringList("open_command"));
        else if (y.isString("open_command")) oc.add(y.getString("open_command"));
        for (String c : oc) {
            if (c == null) continue;
            c = c.trim().toLowerCase(Locale.ROOT);
            if (c.startsWith("/")) c = c.substring(1);
            if (!c.isEmpty()) m.aliases.add(c);
        }
        m.openActions = y.getStringList("open_commands");
        m.closeActions = y.getStringList("close_commands");

        ConfigurationSection fs = y.getConfigurationSection("fill_item");
        if (fs != null) m.fill = parseItem("fill_item", fs, m, errors, false);

        ConfigurationSection is = y.getConfigurationSection("items");
        if (is != null) {
            for (String key : is.getKeys(false)) {
                ConfigurationSection s = is.getConfigurationSection(key);
                if (s == null) continue;
                Item it = parseItem(key, s, m, errors, true);
                if (it != null) m.items.add(it);
            }
        }
        Collections.sort(m.items, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return Integer.compare(a.priority, b.priority);
            }
        });
        return m;
    }

    private void addSlots(String raw, Menu m, String itemKey, List<Integer> out, List<String> errors) {
        try {
            String r = raw.trim();
            int dash = r.indexOf('-', 1);
            int from;
            int to;
            if (dash > 0) {
                from = Integer.parseInt(r.substring(0, dash).trim());
                to = Integer.parseInt(r.substring(dash + 1).trim());
            } else {
                from = Integer.parseInt(r);
                to = from;
            }
            for (int i = from; i <= to; i++) {
                if (i >= 0 && i < m.size) out.add(i);
                else errors.add(m.name + ": предмет '" + itemKey + "' — слот " + i + " вне меню (0.." + (m.size - 1) + ")");
            }
        } catch (NumberFormatException ex) {
            errors.add(m.name + ": предмет '" + itemKey + "' — неверный слот '" + raw + "'");
        }
    }

    private Item parseItem(String key, ConfigurationSection s, Menu m, List<String> errors, boolean needSlot) {
        Item it = new Item();
        it.key = key;
        it.priority = s.getInt("priority", 0);

        String mat = s.getString("material", "STONE");
        Material material = Material.matchMaterial(mat.trim());
        if (material == null) {
            errors.add(m.name + ": предмет '" + key + "' — неизвестный материал '" + mat + "'");
            material = Material.BARRIER;
        }
        it.material = material;

        if (needSlot) {
            List<String> raws = new ArrayList<>(s.getStringList("slots"));
            if (s.contains("slot")) raws.add(String.valueOf(s.get("slot")));
            for (String r : raws) addSlots(r, m, key, it.slots, errors);
            if (it.slots.isEmpty()) {
                errors.add(m.name + ": предмет '" + key + "' пропущен — не указан slot");
                return null;
            }
        }

        it.skullOwner = nullIfEmpty(s.getString("skull_owner"));
        it.name = s.getString("display_name");
        it.lore.addAll(s.getStringList("lore"));
        it.amount = Math.max(1, Math.min(64, s.getInt("amount", 1)));
        it.glow = s.getBoolean("glow", false);
        it.permission = nullIfEmpty(s.getString("permission"));
        it.clickPermission = nullIfEmpty(s.getString("click_permission"));
        it.denyMessage = nullIfEmpty(s.getString("no_permission_message"));

        String[][] keys = {
                {"left", "left_click_commands"},
                {"right", "right_click_commands"},
                {"shift_left", "shift_left_click_commands"},
                {"shift_right", "shift_right_click_commands"},
                {"middle", "middle_click_commands"},
                {"any", "click_commands"}
        };
        for (String[] k : keys) it.actions.put(k[0], s.getStringList(k[1]));
        return it;
    }

    // ------------------------------------------------------------ доступ

    Menu get(String name) {
        return name == null ? null : menus.get(name.trim().toLowerCase(Locale.ROOT));
    }

    Menu byAlias(String label) {
        return aliases.get(label);
    }

    int count() {
        return menus.size();
    }

    List<Menu> accessible(CommandSender s) {
        List<Menu> r = new ArrayList<>();
        for (Menu m : menus.values()) {
            if (m.permission == null || s.hasPermission(m.permission)) r.add(m);
        }
        return r;
    }

    // ------------------------------------------------------------ открытие

    boolean open(Player p, Menu m, boolean checkPerm) {
        if (checkPerm && m.permission != null && !p.hasPermission(m.permission)) {
            p.sendMessage(m.denyMessage != null ? Util.color(m.denyMessage)
                    : plugin.cfg("messages.no-permission", HerPlun.DEF_NO_PERM));
            return false;
        }
        Holder h = new Holder(m);
        String title = Util.color(Placeholders.apply(p, m.title));
        Inventory inv = Bukkit.createInventory(h, m.size, title);
        h.inv = inv;

        for (Item it : m.items) {
            if (it.permission != null && !p.hasPermission(it.permission)) continue;
            for (int slot : it.slots) {
                if (h.slots.containsKey(slot)) continue;
                h.slots.put(slot, it);
                inv.setItem(slot, build(p, it));
            }
        }
        if (m.fill != null) {
            ItemStack filler = build(p, m.fill);
            for (int i = 0; i < m.size; i++) {
                if (inv.getItem(i) == null) inv.setItem(i, filler.clone());
            }
        }

        p.openInventory(inv);
        viewers.add(p.getUniqueId());
        Actions.run(plugin, p, m.openActions);
        return true;
    }

    void onClosed(Player p, Holder h) {
        viewers.remove(p.getUniqueId());
        if (plugin.isEnabled()) Actions.run(plugin, p, h.menu.closeActions);
    }

    /** Закрыть все открытые меню (при выключении плагина). */
    void closeAll() {
        for (UUID id : new ArrayList<>(viewers)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.closeInventory();
        }
        viewers.clear();
    }

    private ItemStack build(Player p, Item it) {
        ItemStack st = new ItemStack(it.material, it.amount);
        ItemMeta meta = st.getItemMeta();
        if (meta == null) return st;
        if (it.name != null) meta.setDisplayName(Util.color(Placeholders.apply(p, it.name)));
        if (!it.lore.isEmpty()) {
            List<String> l = new ArrayList<>();
            for (String line : it.lore) {
                String t = Util.color(Placeholders.apply(p, line));
                l.addAll(Arrays.asList(t.split("\n")));
            }
            meta.setLore(l);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (it.skullOwner != null && meta instanceof SkullMeta) {
            String owner = Placeholders.apply(p, it.skullOwner);
            Player online = Bukkit.getPlayerExact(owner);
            ((SkullMeta) meta).setOwningPlayer(online != null ? online : Bukkit.getOfflinePlayer(owner));
        }
        if (it.glow) glow(meta);
        st.setItemMeta(meta);
        return st;
    }

    /** Блеск предмета. Через reflection, чтобы работало на всех версиях 1.16–1.21. */
    private void glow(ItemMeta meta) {
        try {
            Method m = ItemMeta.class.getMethod("setEnchantmentGlintOverride", Boolean.class);
            m.invoke(meta, Boolean.TRUE);
            return;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
        }
        try {
            Class<?> ec = Class.forName("org.bukkit.enchantments.Enchantment");
            Object ench = null;
            try {
                ench = ec.getMethod("getByKey", NamespacedKey.class).invoke(null, NamespacedKey.minecraft("unbreaking"));
            } catch (Exception ignored) {
            }
            if (ench == null) ench = ec.getMethod("getByName", String.class).invoke(null, "DURABILITY");
            if (ench != null) {
                ItemMeta.class.getMethod("addEnchant", ec, int.class, boolean.class).invoke(meta, ench, 1, true);
            }
        } catch (Throwable ignored) {
        }
    }
}
