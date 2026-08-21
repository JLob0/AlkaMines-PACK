package com.alka.mines.pickaxe;

/**
 * Calcula o nivel da picareta a partir dos blocos minerados (curva exponencial).
 * getBlocksForLevel(level) = base * level^exponent.
 */
public class PickaxeLevelManager {

    private final double base;
    private final double exponent;
    private final int maxLevel;

    public PickaxeLevelManager(double base, double exponent, int maxLevel) {
        this.base = base;
        this.exponent = exponent;
        this.maxLevel = maxLevel;
    }

    public long getBlocksForLevel(int level) {
        if (level <= 1) {
            return 0;
        }
        return (long) (base * Math.pow(level, exponent));
    }

    public int getLevelForBlocks(long blocks) {
        int level = 1;
        while (level < maxLevel && getBlocksForLevel(level + 1) <= blocks) {
            level++;
        }
        return level;
    }

    public boolean isMaxLevel(int level) {
        return level >= maxLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }
}
