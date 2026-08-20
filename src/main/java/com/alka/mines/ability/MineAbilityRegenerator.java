package com.alka.mines.ability;

import com.alka.mines.cache.MineBlockData;
import com.alka.mines.cache.MineCache;
import com.alka.mines.model.Mine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;

/**
 * Regenera, na main thread, os blocos quebrados visualmente pela habilidade. O bloco no
 * servidor nunca foi removido de verdade (a quebra foi so via pacotes), entao aqui
 * restauramos o Material original do cache (com fallback STONE). O MineCache e
 * READ-ONLY - nunca modificado.
 */
public final class MineAbilityRegenerator {

    private MineAbilityRegenerator() {
    }

    public static void scheduleRegeneration(Mine mine, Set<Location> brokenBlocks, Plugin plugin, MineCache cache) {
        int delay = plugin.getConfig().getInt("abilities.dragon_breath.regeneration.delay-ticks", 100);
        scheduleRegeneration(mine, brokenBlocks, plugin, cache, delay);
    }

    public static void scheduleRegeneration(Mine mine, Set<Location> brokenBlocks, Plugin plugin, MineCache cache,
                                            int delayTicks) {
        if (brokenBlocks == null || brokenBlocks.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Location loc : brokenBlocks) {
                if (loc.getWorld() == null) {
                    continue;
                }
                cache.findBlockAt(mine.getId(), loc)
                        .map(MineBlockData::originalMaterial)
                        .ifPresentOrElse(
                                mat -> loc.getBlock().setType(mat, false),
                                () -> loc.getBlock().setType(Material.STONE, false));
            }
            refreshForNearbyPlayers(brokenBlocks);
            brokenBlocks.clear();
        }, delayTicks);
    }

    /** Re-envia o estado real dos blocos pros jogadores proximos (resync visual). */
    private static void refreshForNearbyPlayers(Set<Location> blocks) {
        for (Location loc : blocks) {
            if (loc.getWorld() == null) {
                continue;
            }
            var blockData = loc.getBlock().getBlockData();
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(loc) <= 64.0 * 64.0) {
                    p.sendBlockChange(loc, blockData);
                }
            }
        }
    }
}
