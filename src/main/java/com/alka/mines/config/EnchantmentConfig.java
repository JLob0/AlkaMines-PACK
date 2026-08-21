package com.alka.mines.config;

import com.alka.mines.enchantment.EnchantmentType;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

/**
 * Carrega as definicoes dos encantamentos do config.yml (secao `enchantments`) pra que
 * display-name, descricao, niveis, chances, custo e nivel de desbloqueio sejam editaveis
 * sem tocar em codigo. Fallback pros valores padrao do enum quando a secao faltar.
 */
public class EnchantmentConfig {

    private static EnchantmentConfig instance;

    private final Map<EnchantmentType, Data> data = new EnumMap<>(EnchantmentType.class);

    public static EnchantmentConfig get() {
        return instance;
    }

    public static void load(FileConfiguration cfg) {
        EnchantmentConfig config = new EnchantmentConfig();
        for (EnchantmentType type : EnchantmentType.values()) {
            String path = "enchantments." + type.name();
            if (!cfg.isConfigurationSection(path)) {
                continue;
            }
            config.data.put(type, new Data(
                    cfg.getString(path + ".display-name", type.getDisplayName()),
                    cfg.getString(path + ".description", type.getDescription()),
                    cfg.getInt(path + ".max-level", type.getMaxLevel()),
                    cfg.getDouble(path + ".base-chance", type.getBaseChance()),
                    cfg.getDouble(path + ".chance-per-level", type.getChancePerLevel()),
                    cfg.getInt(path + ".cooldown-seconds", type.getCooldownSeconds()),
                    cfg.getInt(path + ".base-cost", type.getBaseCost()),
                    cfg.getInt(path + ".unlock-level", type.getUnlockLevel())
            ));
        }
        instance = config;
    }

    public boolean has(EnchantmentType type) {
        return data.containsKey(type);
    }

    public String displayName(EnchantmentType type) {
        Data d = data.get(type);
        return d != null ? d.displayName() : null;
    }

    public String description(EnchantmentType type) {
        Data d = data.get(type);
        return d != null ? d.description() : null;
    }

    public int maxLevel(EnchantmentType type) {
        Data d = data.get(type);
        return d != null ? d.maxLevel() : 0;
    }

    public double baseChance(EnchantmentType type) {
        Data d = data.get(type);
        return d != null ? d.baseChance() : 0;
    }

    public double chancePerLevel(EnchantmentType type) {
        Data d = data.get(type);
        return d != null ? d.chancePerLevel() : 0;
    }

    public int cooldownSeconds(EnchantmentType type) {
        Data d = data.get(type);
        return d != null ? d.cooldownSeconds() : 0;
    }

    public int baseCost(EnchantmentType type) {
        Data d = data.get(type);
        return d != null ? d.baseCost() : 0;
    }

    public int unlockLevel(EnchantmentType type) {
        Data d = data.get(type);
        return d != null ? d.unlockLevel() : 0;
    }

    public record Data(String displayName, String description, int maxLevel, double baseChance,
                       double chancePerLevel, int cooldownSeconds, int baseCost, int unlockLevel) {
    }
}
