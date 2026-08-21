package com.alka.mines.visual;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Feedback visual de quebra/level-up via ACTION BAR (confiavel entre versoes, sem risco
 * de DC). Nao usa Display Entities (indices de metadata variam por versao e derrubavam o
 * client com "Network Protocol error" nesta 1.21.8).
 */
public class FloatingTextManager {

    public FloatingTextManager(Plugin plugin) {
        // sem entidades - action bars somem sozinhas
    }

    public void spawnFloatingText(Player player, Location loc, String text, long durationMillis, int bgColor) {
        player.sendActionBar(text);
    }

    public void spawnLevelUpText(Player player, Location loc, int oldLevel, int newLevel) {
        player.sendActionBar("§6§l⛏ UP! §e" + oldLevel + " §7→ §e" + newLevel);
    }

    public void spawnBreakText(Player player, Location loc, String text) {
        player.sendActionBar(text);
    }

    public void cleanupAll() {
        // action bars somem sozinhas
    }

    public void cleanupOld() {
        // action bars somem sozinhas
    }
}
