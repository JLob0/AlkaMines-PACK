package com.alka.mines.pickaxe;

import com.alkacode.core.gui.BaseGui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Prestigio da picareta: ao atingir o nivel maximo, o jogador pode prestigiar - reseta o
 * nivel pra 1, ganha +1 prestigio (multiplo +0.1x de dinheiro), +10 pontos, e desbloqueia
 * skins (DRAGON no 1º, CRYSTAL no 5º). Broadcaast + titulo.
 */
public class PrestigeSystem {

    private final PickaxeManager pickaxeManager;
    private final PickaxeLevelManager levelManager;
    private final int maxLevel;
    private final JavaPlugin plugin;

    public PrestigeSystem(JavaPlugin plugin, PickaxeManager pickaxeManager, PickaxeLevelManager levelManager, int maxLevel) {
        this.plugin = plugin;
        this.pickaxeManager = pickaxeManager;
        this.levelManager = levelManager;
        this.maxLevel = maxLevel;
    }

    public boolean isEligible(Player player) {
        EvolutionPickaxe pick = pickaxeManager.getFromHand(player);
        return pick != null && pick.getLevel() >= maxLevel;
    }

    public void prestige(Player player) {
        if (!isEligible(player)) {
            return;
        }

        EvolutionPickaxe pick = pickaxeManager.getFromHand(player);
        int oldPrestige = pick.getPrestige();
        int newPrestige = oldPrestige + 1;

        pick.reset();
        pick.setPrestige(newPrestige);

        if (newPrestige == 1) {
            pick.addUnlockedSkin("DRAGON");
        }
        if (newPrestige >= 5) {
            pick.addUnlockedSkin("CRYSTAL");
        }

        pickaxeManager.saveToHeld(player, pick);

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l✦ §e" + player.getName() + " §6prestigiou sua picareta! §7(" + newPrestige + "º)");
        Bukkit.broadcastMessage("");

        player.sendTitle("§6§lPRESTÍGIO!", "§e" + oldPrestige + " §7→ §e" + newPrestige, 10, 70, 20);
    }

    public void openPrestigeGui(Player player) {
        new PrestigeGui(plugin, player, this).open();
    }

    /** GUI de confirmacao do prestigio. */
    public static class PrestigeGui extends BaseGui {
        private final PrestigeSystem system;

        public PrestigeGui(JavaPlugin plugin, Player player, PrestigeSystem system) {
            super(plugin, player, "Prestígio", 3, "alkamines-prestige");
            this.system = system;
        }

        @Override
        public void render() {
            fillBorder(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
            ItemStack confirm = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = confirm.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§6§lCONFIRMAR PRESTÍGIO");
                meta.setLore(List.of(
                        "§7Seu nível será resetado para 1.",
                        "§7Você ganhará: §6+0.1x multiplicador",
                        "§7Pontos de prestígio: §6+10",
                        "",
                        "§aClique para confirmar"
                ));
                confirm.setItemMeta(meta);
            }
            setItem(13, confirm, e -> {
                e.setCancelled(true);
                system.prestige(player);
                player.closeInventory();
            });
        }
    }
}
