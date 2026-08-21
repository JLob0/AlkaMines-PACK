package com.alka.mines.pickaxe;

import com.alka.mines.enchantment.EnchantmentType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Picareta evolutiva cujo estado vive NO ITEM (PersistentDataContainer/NBT): nivel,
 * blocos, prestigio, skin e encantamentos ficam gravados na propria picareta. Se o
 * jogador perder/dropar o item, perde tudo - /picareta entrega uma nova do zero.
 */
public class EvolutionPickaxe {

    private static final NamespacedKey TAG = NamespacedKey.minecraft("alkamines_pickaxe");
    private static final NamespacedKey KEY_LEVEL = NamespacedKey.minecraft("ap_level");
    private static final NamespacedKey KEY_BLOCKS = NamespacedKey.minecraft("ap_blocks");
    private static final NamespacedKey KEY_PSB = NamespacedKey.minecraft("ap_psb");
    private static final NamespacedKey KEY_PRESTIGE = NamespacedKey.minecraft("ap_prestige");
    private static final NamespacedKey KEY_SKIN = NamespacedKey.minecraft("ap_skin");
    private static final NamespacedKey KEY_ENCHANTS = NamespacedKey.minecraft("ap_enchantments");
    private static final NamespacedKey KEY_SKINS = NamespacedKey.minecraft("ap_skins");

    public static boolean isEvolutionPickaxe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(TAG, PersistentDataType.BYTE);
    }

    /** Le o estado de uma picareta evolutiva do item. Se nao for evolutiva, retorna null. */
    public static EvolutionPickaxe fromItem(ItemStack item) {
        if (!isEvolutionPickaxe(item)) {
            return null;
        }
        var pdc = item.getItemMeta().getPersistentDataContainer();
        EvolutionPickaxe pick = new EvolutionPickaxe();
        pick.level = pdc.getOrDefault(KEY_LEVEL, PersistentDataType.INTEGER, 1);
        pick.totalBlocksMined = pdc.getOrDefault(KEY_BLOCKS, PersistentDataType.LONG, 0L);
        pick.prestigeStartBlocks = pdc.getOrDefault(KEY_PSB, PersistentDataType.LONG, 0L);
        pick.prestige = pdc.getOrDefault(KEY_PRESTIGE, PersistentDataType.INTEGER, 0);
        pick.skin = pdc.getOrDefault(KEY_SKIN, PersistentDataType.STRING, "DEFAULT");
        String enchStr = pdc.get(KEY_ENCHANTS, PersistentDataType.STRING);
        if (enchStr != null) {
            for (String part : enchStr.split(";")) {
                if (part.isEmpty()) {
                    continue;
                }
                String[] kv = part.split(":");
                try {
                    pick.enchantments.put(EnchantmentType.valueOf(kv[0]), Integer.parseInt(kv[1]));
                } catch (Exception ignored) {
                }
            }
        }
        String skinsStr = pdc.get(KEY_SKINS, PersistentDataType.STRING);
        if (skinsStr != null) {
            for (String s : skinsStr.split(";")) {
                if (!s.isEmpty()) {
                    pick.unlockedSkins.add(s);
                }
            }
        }
        if (pick.unlockedSkins.isEmpty()) {
            pick.unlockedSkins.add("DEFAULT");
        }
        return pick;
    }

    private int level = 1;
    private long totalBlocksMined = 0;
    private long prestigeStartBlocks = 0;
    private final Map<EnchantmentType, Integer> enchantments = new EnumMap<>(EnchantmentType.class);
    private final Set<String> unlockedSkins = new HashSet<>(Set.of("DEFAULT"));
    private String skin = "DEFAULT";
    private int prestige = 0;

    /** Blocos efetivos pra subir de nivel (total - marco do prestigio). */
    public long getEffectiveBlocksMined() {
        return totalBlocksMined - prestigeStartBlocks;
    }

    public long getPrestigeStartBlocks() {
        return prestigeStartBlocks;
    }

    public void setPrestigeStartBlocks(long prestigeStartBlocks) {
        this.prestigeStartBlocks = prestigeStartBlocks;
    }

    public double getProgressToNextLevel(PickaxeLevelManager manager) {
        long current = getEffectiveBlocksMined();
        long requiredForCurrent = manager.getBlocksForLevel(level);
        long requiredForNext = manager.getBlocksForLevel(level + 1);
        if (requiredForNext <= requiredForCurrent) {
            return 1.0;
        }
        return (double) (current - requiredForCurrent) / (requiredForNext - requiredForCurrent);
    }

    /** Escreve o estado no item (PDC) e atualiza nome/lore. Preserva outras tags (ex: aluguel). */
    public void applyTo(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        pdc.set(TAG, PersistentDataType.BYTE, (byte) 1);
        pdc.set(KEY_LEVEL, PersistentDataType.INTEGER, level);
        pdc.set(KEY_BLOCKS, PersistentDataType.LONG, totalBlocksMined);
        pdc.set(KEY_PSB, PersistentDataType.LONG, prestigeStartBlocks);
        pdc.set(KEY_PRESTIGE, PersistentDataType.INTEGER, prestige);
        pdc.set(KEY_SKIN, PersistentDataType.STRING, skin);

        StringJoiner sj = new StringJoiner(";");
        for (Map.Entry<EnchantmentType, Integer> e : enchantments.entrySet()) {
            sj.add(e.getKey().name() + ":" + e.getValue());
        }
        pdc.set(KEY_ENCHANTS, PersistentDataType.STRING, sj.toString());
        pdc.set(KEY_SKINS, PersistentDataType.STRING, String.join(";", unlockedSkins));

        PickaxeSkin pskin = getPickaxeSkin();
        Material mat = level > 100 ? Material.NETHERITE_PICKAXE : pskin.getMaterial();
        meta.setDisplayName(color(pskin.getColorPrefix() + "⛏ Picareta &7[&f" + formatBlocks(totalBlocksMined) + "&7]"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Nível: &e" + level));
        lore.add(color("&7Blocos: &f" + formatBlocks(totalBlocksMined)));
        lore.add(color("&7Prestígio: &6" + prestige));
        lore.add(color("&7Multiplicador: &6" + String.format("%.1fx", getMoneyMultiplier())));
        lore.add(color("&7Skin: &f" + pskin.getDisplayName()));
        lore.add("");
        lore.add(color("&bEncantamentos:"));
        for (Map.Entry<EnchantmentType, Integer> ench : enchantments.entrySet()) {
            EnchantmentType t = ench.getKey();
            String chance = EnchantmentType.formatChance(t.getBaseChance()) + "% + "
                    + EnchantmentType.formatChance(t.getChancePerLevel()) + "%/nvl";
            lore.add(color(" &8• &f" + t.getDisplayName() + " &7" + ench.getValue() + "/" + t.getMaxLevel()
                    + " &8(" + chance + ")"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        if (mat != item.getType()) {
            item.setType(mat);
        }
        if (!enchantments.isEmpty()) {
            item.addUnsafeEnchantment(Enchantment.LURE, 1);
        }
    }

    /** Item novo com o estado atual (usado ao entregar /picareta e ao atualizar a skin). */
    public ItemStack getDisplayItem() {
        PickaxeSkin pskin = getPickaxeSkin();
        // se a skin tem item custom (ItemsAdder) configurado, usa ele como base
        String customId = com.alka.mines.config.MineConfig.get().getSkinCustomItem(pskin.name());
        ItemStack item = null;
        if (customId != null && !customId.isEmpty()) {
            item = com.alka.mines.hook.ItemsAdderHook.getCustomItem(customId).orElse(null);
        }
        if (item == null) {
            item = new ItemStack(level > 100 ? Material.NETHERITE_PICKAXE : pskin.getMaterial());
        }
        applyTo(item);
        return item;
    }

    private String formatBlocks(long blocks) {
        if (blocks >= 1_000_000) {
            return String.format("%.2fM", blocks / 1_000_000.0);
        }
        if (blocks >= 1_000) {
            return String.format("%.1fK", blocks / 1_000.0);
        }
        return String.valueOf(blocks);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public void addBlocksMined(long amount) {
        this.totalBlocksMined += amount;
    }

    public long getTotalBlocksMined() {
        return totalBlocksMined;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void addEnchantment(EnchantmentType type, int level) {
        enchantments.put(type, level);
    }

    public Map<EnchantmentType, Integer> getEnchantments() {
        return Collections.unmodifiableMap(enchantments);
    }

    public int getEnchantmentLevel(EnchantmentType type) {
        return enchantments.getOrDefault(type, 0);
    }

    public String getSkin() {
        return skin;
    }

    public PickaxeSkin getPickaxeSkin() {
        return PickaxeSkin.fromId(skin);
    }

    public void setSkin(String skin) {
        this.skin = skin;
    }

    public void setSkin(PickaxeSkin skin) {
        this.skin = skin.name();
    }

    public Set<String> getUnlockedSkins() {
        return unlockedSkins;
    }

    public void addUnlockedSkin(String skin) {
        unlockedSkins.add(skin);
    }

    /** Multiplicador de dinheiro: 1.0 base + 0.1 por prestigio. */
    public double getMoneyMultiplier() {
        return 1.0 + (prestige * 0.1);
    }

    public int getPrestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        this.prestige = prestige;
    }

    /** Reset pro prestigio: zera o NIVEL (mantendo os blocos totais - o nivel passa a
     * contar dos blocos desde este marco). Prestigio, skins e encantamentos ficam. */
    public void reset() {
        this.level = 1;
        this.prestigeStartBlocks = this.totalBlocksMined;
    }
}
