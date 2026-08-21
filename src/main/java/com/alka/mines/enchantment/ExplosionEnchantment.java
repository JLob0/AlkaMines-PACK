package com.alka.mines.enchantment;

import com.alka.mines.virtual.VirtualBlockSystem;
import com.alka.mines.virtual.VirtualMineGrid;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Encantamento Explosao: quebra os blocos virtuais ao redor do bloco minado (raio por
 * nivel) e mostra particula/som de explosao.
 */
public class ExplosionEnchantment {

    private final VirtualBlockSystem virtualBlockSystem;

    public ExplosionEnchantment(VirtualBlockSystem virtualBlockSystem) {
        this.virtualBlockSystem = virtualBlockSystem;
    }

    public void explode(Player player, Location center, int level, VirtualMineGrid grid) {
        int radius = switch (level) {
            case 1, 2, 3 -> 1;
            case 4, 5, 6, 7 -> 2;
            default -> 3;
        };

        int broken = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Location loc = center.clone().add(dx, dy, dz);
                    if (virtualBlockSystem.handleBreak(player, loc, grid)) {
                        broken++;
                    }
                }
            }
        }

        player.getWorld().spawnParticle(Particle.EXPLOSION, center, 5, 1, 1, 1, 0);
        player.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        if (broken > 0) {
            player.sendMessage("§c§lBOOM! §7" + broken + " blocos destruídos!");
        }
    }
}
