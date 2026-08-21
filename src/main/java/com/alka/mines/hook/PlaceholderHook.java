package com.alka.mines.hook;

import com.alka.mines.enchantment.EnchantmentType;
import com.alka.mines.pickaxe.EvolutionPickaxe;
import com.alka.mines.pickaxe.PickaxeManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Placeholders do AlkaMinesPack (%alkamines_*) leem a picareta NA MAO do jogador.
 * Disponiveis: level, blocks, prestige, multiplier, skin, ench_<TIPO>.
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final PickaxeManager pickaxeManager;

    public PlaceholderHook(PickaxeManager pickaxeManager) {
        this.pickaxeManager = pickaxeManager;
    }

    @Override
    public String getIdentifier() {
        return "alkamines";
    }

    @Override
    public String getAuthor() {
        return "AlkaStudio";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        if (!(offline instanceof Player player)) {
            return "";
        }
        EvolutionPickaxe pick = pickaxeManager.getFromHand(player);
        if (pick == null) {
            return "0";
        }
        String p = params.toLowerCase();
        if (p.equals("level")) {
            return String.valueOf(pick.getLevel());
        }
        if (p.equals("blocks")) {
            return String.valueOf(pick.getTotalBlocksMined());
        }
        if (p.equals("prestige")) {
            return String.valueOf(pick.getPrestige());
        }
        if (p.equals("multiplier")) {
            return String.format("%.1f", pick.getMoneyMultiplier());
        }
        if (p.equals("skin")) {
            return pick.getPickaxeSkin().getDisplayName();
        }
        if (p.startsWith("ench_")) {
            try {
                EnchantmentType type = EnchantmentType.valueOf(p.substring(5).toUpperCase());
                return String.valueOf(pick.getEnchantmentLevel(type));
            } catch (IllegalArgumentException e) {
                return "0";
            }
        }
        return "";
    }
}
