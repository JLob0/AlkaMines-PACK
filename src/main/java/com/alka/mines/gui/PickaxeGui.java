package com.alka.mines.gui;

import com.alka.mines.enchantment.EnchantmentType;
import com.alka.mines.pickaxe.EvolutionPickaxe;
import com.alka.mines.pickaxe.PickaxeManager;
import com.alka.mines.pickaxe.PickaxeSkin;
import com.alka.mines.pickaxe.PrestigeSystem;
import com.alkacode.core.gui.BaseGui;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * GUI da picareta evolutiva: mostra a picareta, os encantamentos (comprar/upar), as skins
 * desbloqueadas, o botao de prestigio e as estatisticas. Abre ao clicar com botao direito
 * na picareta evolutiva.
 */
public class PickaxeGui extends BaseGui {

    private final PickaxeManager pickaxeManager;
    private final PrestigeSystem prestigeSystem;

    public PickaxeGui(JavaPlugin plugin, Player player, PickaxeManager pickaxeManager, PrestigeSystem prestigeSystem) {
        super(plugin, player, "Sua Picareta", 6, "alkamines-pickaxe");
        this.pickaxeManager = pickaxeManager;
        this.prestigeSystem = prestigeSystem;
    }

    @Override
    public void render() {
        fillBorder(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
        EvolutionPickaxe pick = pickaxeManager.getFromHand(player);
        if (pick == null) {
            setItem(13, infoItem("§cSegure sua picareta", List.of("§7Clique direito na picareta evolutiva", "§7para abrir este menu.")),
                    e -> e.setCancelled(true));
            return;
        }

        setItem(13, pick.getDisplayItem(), e -> e.setCancelled(true));

        int slot = 10;
        for (EnchantmentType type : EnchantmentType.values()) {
            setItem(slot, buildEnchantIcon(pick, type), e -> {
                e.setCancelled(true);
                handleEnchantClick(pick, type);
            });
            slot++;
            if (slot == 17) {
                slot = 19;
            }
        }

        renderSkins(pick);

        if (prestigeSystem != null && prestigeSystem.isEligible(player)) {
            setItem(40, buildPrestigeItem(), e -> {
                e.setCancelled(true);
                prestigeSystem.openPrestigeGui(player);
            });
        }

        setItem(44, buildStatsItem(pick), e -> e.setCancelled(true));
    }

    private void renderSkins(EvolutionPickaxe pick) {
        Set<String> unlocked = pick.getUnlockedSkins();
        int slot = 19;
        for (PickaxeSkin skin : PickaxeSkin.values()) {
            boolean owned = unlocked.contains(skin.name());
            boolean active = pick.getSkin().equalsIgnoreCase(skin.name());
            String customId = com.alka.mines.config.MineConfig.get().getSkinCustomItem(skin.name());
            ItemStack item = customId != null && !customId.isEmpty()
                    ? com.alka.mines.hook.ItemsAdderHook.getCustomItem(customId).orElse(new ItemStack(skin.getMaterial()))
                    : new ItemStack(skin.getMaterial());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName((owned ? "§a" : "§8") + skin.getDisplayName()
                        + (active ? " §7[ATIVA]" : ""));
                List<String> lore = new ArrayList<>();
                if (!owned) {
                    lore.add("§cNão desbloqueada");
                } else if (!active) {
                    lore.add("§eClique para equipar");
                } else {
                    lore.add("§7Skin atual");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            final PickaxeSkin fs = skin;
            setItem(slot, item, e -> {
                e.setCancelled(true);
                if (unlocked.contains(fs.name())) {
                    pick.setSkin(fs);
                    player.sendMessage("§aSkin §f" + fs.getDisplayName() + " §aequipada!");
                    pickaxeManager.saveToHeld(player, pick);
                } else {
                    player.sendMessage("§cSkin não desbloqueada.");
                }
            });
            slot++;
            if (slot == 26) {
                slot = 28;
            }
        }
    }

    private ItemStack buildPrestigeItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lPRESTÍGIO DISPONÍVEL!");
            meta.setLore(List.of(
                    "§7Clique para prestigiar sua picareta.",
                    "§cIsso resetará seu nível para 1."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildEnchantIcon(EvolutionPickaxe pick, EnchantmentType type) {
        int currentLevel = pick.getEnchantmentLevel(type);
        boolean unlocked = pick.getLevel() >= type.getUnlockLevel();
        boolean owned = currentLevel > 0;

        ItemStack item = new ItemStack(unlocked ? Material.ENCHANTED_BOOK : Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        List<String> lore = new ArrayList<>();
        lore.add("§7" + type.getDescription());

        String chance = "§7Chance: §f" + EnchantmentType.formatChance(type.getBaseChance())
                + "% §7base + §f" + EnchantmentType.formatChance(type.getChancePerLevel()) + "%§7/nível";
        lore.add(chance);

        if (!unlocked) {
            meta.setDisplayName("§8" + type.getDisplayName());
            lore.add("");
            lore.add("§cBloqueado — Nível " + type.getUnlockLevel() + " da picareta");
        } else if (!owned) {
            meta.setDisplayName("§e" + type.getDisplayName());
            lore.add("");
            lore.add("§aClique para comprar");
            lore.add("§7Custo: §6" + type.getBaseCost() + " coins");
        } else {
            meta.setDisplayName("§a" + type.getDisplayName() + " §7[" + currentLevel + "/" + type.getMaxLevel() + "]");
            lore.add("");
            lore.add(currentLevel < type.getMaxLevel()
                    ? "§eClique para upar"
                    : "§aMáximo!");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Compra/upa o encantamento (pagamento via economia fica pra uma fase posterior). */
    private void handleEnchantClick(EvolutionPickaxe pick, EnchantmentType type) {
        if (pick.getLevel() < type.getUnlockLevel()) {
            player.sendMessage("§cAlcance nível §f" + type.getUnlockLevel() + " §cda picareta!");
            return;
        }
        int current = pick.getEnchantmentLevel(type);
        if (current < type.getMaxLevel()) {
            pick.addEnchantment(type, current + 1);
            player.sendMessage("§a" + type.getDisplayName() + " §7nível §f" + (current + 1) + " §a✓");
            pickaxeManager.saveToHeld(player, pick);
        } else {
            player.sendMessage("§a" + type.getDisplayName() + " já está no máximo!");
        }
    }

    private ItemStack infoItem(String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildStatsItem(EvolutionPickaxe pick) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bEstatísticas");
            meta.setLore(List.of(
                    "§7Blocos totais: §f" + pick.getTotalBlocksMined(),
                    "§7Nível atual: §e" + pick.getLevel(),
                    "§7Prestígio: §6" + pick.getPrestige()
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
}
