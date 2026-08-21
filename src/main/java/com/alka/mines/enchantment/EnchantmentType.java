package com.alka.mines.enchantment;

import com.alka.mines.config.EnchantmentConfig;

/**
 * Tipos de encantamento customizado da picareta. Os VALORES (niveis, chances, custo,
 * desbloqueio, nome/descricao) sao carregados do config.yml (EnchantmentConfig); o enum
 * guarda so os padroes de fallback e a identidade de cada tipo.
 */
public enum EnchantmentType {

    DRAGON("Dragão", "Invoca um dragão que quebra blocos", 5, 0.05, 0.05, 30, 1000, 50),
    EXPLOSION("Explosão", "Quebra em área 3x3", 10, 5.0, 2.0, 5, 500, 10),
    KEYFINDER("Keyfinder", "Chance de encontrar keys", 5, 1.0, 1.0, 0, 2000, 25),
    FORTUNE_BOOST("Fortuna+", "Aumenta drops em 50% por nível", 5, 0, 0, 0, 1500, 15),
    SPEED_MINING("Velocidade", "Haste permanente ao minerar", 3, 0, 0, 0, 800, 5),
    MEMORIZE("Memorizando", "Bônus por padrão de blocos", 1, 0, 0, 0, 5000, 100);

    private final String displayName;
    private final String description;
    private final int maxLevel;
    private final double baseChance;       // % no nivel 1
    private final double chancePerLevel;   // % adicional por nivel
    private final int cooldownSeconds;
    private final int baseCost;
    private final int unlockLevel;

    EnchantmentType(String displayName, String description, int maxLevel,
                    double baseChance, double chancePerLevel, int cooldownSeconds,
                    int baseCost, int unlockLevel) {
        this.displayName = displayName;
        this.description = description;
        this.maxLevel = maxLevel;
        this.baseChance = baseChance;
        this.chancePerLevel = chancePerLevel;
        this.cooldownSeconds = cooldownSeconds;
        this.baseCost = baseCost;
        this.unlockLevel = unlockLevel;
    }

    /** Chance (em %) no nivel dado. */
    public double getChance(int level) {
        return getBaseChance() + (getChancePerLevel() * (level - 1));
    }

    /** Alias de {@link #getChance(int)} (compat). */
    public double getTotalChance(int level) {
        if (level <= 0) {
            return 0;
        }
        return getChance(level);
    }

    public String getDisplayName() {
        EnchantmentConfig c = EnchantmentConfig.get();
        if (c != null && c.has(this)) {
            return c.displayName(this);
        }
        return displayName;
    }

    public String getDescription() {
        EnchantmentConfig c = EnchantmentConfig.get();
        if (c != null && c.has(this)) {
            return c.description(this);
        }
        return description;
    }

    public int getMaxLevel() {
        EnchantmentConfig c = EnchantmentConfig.get();
        if (c != null && c.has(this)) {
            return c.maxLevel(this);
        }
        return maxLevel;
    }

    public double getBaseChance() {
        EnchantmentConfig c = EnchantmentConfig.get();
        if (c != null && c.has(this)) {
            return c.baseChance(this);
        }
        return baseChance;
    }

    public double getChancePerLevel() {
        EnchantmentConfig c = EnchantmentConfig.get();
        if (c != null && c.has(this)) {
            return c.chancePerLevel(this);
        }
        return chancePerLevel;
    }

    public int getCooldownSeconds() {
        EnchantmentConfig c = EnchantmentConfig.get();
        if (c != null && c.has(this)) {
            return c.cooldownSeconds(this);
        }
        return cooldownSeconds;
    }

    public int getBaseCost() {
        EnchantmentConfig c = EnchantmentConfig.get();
        if (c != null && c.has(this)) {
            return c.baseCost(this);
        }
        return baseCost;
    }

    public int getUnlockLevel() {
        EnchantmentConfig c = EnchantmentConfig.get();
        if (c != null && c.has(this)) {
            return c.unlockLevel(this);
        }
        return unlockLevel;
    }

    /** Formata a chance de % sem zeros desnecessarios (0.05 -> "0.05", 5.0 -> "5"). */
    public static String formatChance(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
