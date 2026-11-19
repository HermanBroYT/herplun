package ru.herplun;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Помощники для WorldEdit / WorldGuard: топорик и справка по командам. */
final class WorldTools {

    static final String AXE_NAME = "\u00a76\u00a7lHerPlun \u00a7e\u2014 \u0422\u043e\u043f\u043e\u0440\u0438\u043a";

    private static final String DEF_MISSING =
            "&c\u0423 \u0432\u0430\u0441 \u043d\u0435 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d \u043f\u043b\u0430\u0433\u0438\u043d WorldGuard \u0438\u043b\u0438 WorldEdit, "
            + "\u0443\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435, \u0432\u0430\u043c \u043d\u0443\u0436\u043d\u043e \u043e\u0442\u043a\u043b\u044e\u0447\u0438\u0442\u044c \u0441\u0435\u0440\u0432\u0435\u0440 \u0438 \u0432\u043b\u043e\u0436\u0438\u0442\u0435 "
            + "\u043f\u043b\u0430\u0433\u0438\u043d\u044b, \u0438 \u0437\u0430\u043f\u0443\u0441\u0442\u0438\u0442\u0435 \u0441\u0435\u0440\u0432\u0435\u0440!";
    private static final String DEF_HINT =
            "&7\u041f\u043e\u043b\u043e\u0436\u0438\u0442\u0435 .jar \u043f\u043b\u0430\u0433\u0438\u043d\u043e\u0432 \u0432 \u043f\u0430\u043f\u043a\u0443 &fplugins/HerPlun/plugins/&7 "
            + "\u0438 \u043f\u0435\u0440\u0435\u0437\u0430\u043f\u0443\u0441\u0442\u0438\u0442\u0435 \u0441\u0435\u0440\u0432\u0435\u0440. \u0421\u043f\u0438\u0441\u043e\u043a: &f/herplun plugins";

    /** Команды WorldEdit, которые вводятся с двумя косыми чертами (//pos1 и т.д.). */
    private static final Set<String> WE_CMDS = new HashSet<>(Arrays.asList(
            "wand", "pos1", "pos2", "hpos1", "hpos2", "chunk", "sel", "desel", "expand", "contract",
            "shift", "outset", "inset", "set", "replace", "overlay", "walls", "faces", "hollow",
            "move", "stack", "copy", "cut", "paste", "undo", "redo", "rotate", "flip", "clearclipboard",
            "line", "sphere", "hsphere", "cyl", "hcyl", "pyramid", "hpyramid", "generate", "count",
            "distr", "size", "regen", "drain", "fixwater", "fixlava", "removeabove", "removebelow",
            "replacenear", "snow", "thaw", "green", "ex", "extinguish", "butcher", "brush", "fill",
            "fillr", "limit", "gmask", "mask", "toggleplace", "perf"));

    private static final Set<String> WG_CMDS = new HashSet<>(Arrays.asList(
            "rg", "region", "regions", "worldguard", "wg"));

    private WorldTools() {
    }

    static boolean hasWorldEdit() {
        return Bukkit.getPluginManager().getPlugin("WorldEdit") != null
                || Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
    }

    static boolean hasWorldGuard() {
        return Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    /** Деревянный топорик — это стандартная палочка выделения WorldEdit. */
    static ItemStack axe() {
        ItemStack it = new ItemStack(Material.WOODEN_AXE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(AXE_NAME);
            m.setLore(Arrays.asList(
                    "§7ЛКМ по блоку — §fточка 1",
                    "§7ПКМ по блоку — §fточка 2",
                    "§8Выделение WorldEdit",
                    "§8Приват: §f/rg claim <имя>"));
            m.setUnbreakable(true);
            m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            it.setItemMeta(m);
        }
        return it;
    }

    /**
     * true, если команда относится к WorldEdit/WorldGuard, а нужный плагин не установлен.
     * Примеры: "//pos1", "/rg list".
     */
    static boolean missingFor(String commandLine) {
        if (commandLine == null) return false;
        String c = commandLine.trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty()) return false;
        boolean dbl = c.startsWith("//");
        while (c.startsWith("/")) c = c.substring(1);
        if (c.isEmpty()) return false;
        String label = c.split("\\s+")[0];
        if (dbl) return WE_CMDS.contains(label) && !hasWorldEdit();
        if (label.equals("we") || label.equals("worldedit")) {
            return !hasWorldEdit() && Bukkit.getPluginCommand(label) == null;
        }
        if (WG_CMDS.contains(label)) {
            return !hasWorldGuard() && Bukkit.getPluginCommand(label) == null;
        }
        return false;
    }

    /** Сообщение «у вас не установлен WorldGuard или WorldEdit». */
    static void sendMissing(HerPlun plugin, CommandSender s) {
        s.sendMessage(plugin.cfg("messages.worldedit-missing", DEF_MISSING));
        if (s.hasPermission("herplun.plugins")) {
            s.sendMessage(plugin.cfg("messages.worldedit-missing-hint", DEF_HINT));
        }
    }

    static void help(CommandSender s) {
        s.sendMessage("§c§lHerPlun — WorldEdit и WorldGuard");
        s.sendMessage("§7WorldEdit: " + (hasWorldEdit() ? "§aустановлен" : "§cне найден")
                + " §7| WorldGuard: " + (hasWorldGuard() ? "§aустановлен" : "§cне найден"));
        s.sendMessage("§e/herplun axe §7— выдать топорик для выделения");
        s.sendMessage("§6WorldEdit:");
        s.sendMessage("§e//wand §7— топорик WorldEdit, §e//pos1 //pos2 §7— точки");
        s.sendMessage("§e//set <блок> §7— заполнить, §e//replace <из> <в> §7— заменить");
        s.sendMessage("§e//copy §7/ §e//paste §7— копировать / вставить");
        s.sendMessage("§e//undo §7/ §e//redo §7— отменить / вернуть");
        s.sendMessage("§6WorldGuard (приват):");
        s.sendMessage("§e/rg claim <имя> §7— приватить выделенную область");
        s.sendMessage("§e/rg define <имя> §7— создать регион (админ)");
        s.sendMessage("§e/rg addmember <регион> <игрок> §7— добавить участника");
        s.sendMessage("§e/rg addowner <регион> <игрок> §7— добавить владельца");
        s.sendMessage("§e/rg list §7/ §e/rg info §7— список / информация");
        s.sendMessage("§e/rg flag <регион> <флаг> <знач> §7— флаги");
        s.sendMessage("§e/rg remove <регион> §7— удалить регион");
        s.sendMessage("§7Все эти команды выполняют сами WorldEdit и WorldGuard.");
    }
}
