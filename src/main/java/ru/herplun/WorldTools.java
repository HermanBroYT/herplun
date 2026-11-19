package ru.herplun;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/** Помощники для WorldEdit / WorldGuard: топорик и справка по командам. */
final class WorldTools {

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
            m.setDisplayName("§6§lHerPlun §e— Топорик");
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
