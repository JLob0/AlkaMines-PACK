package com.alka.mines.pickaxe;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

/**
 * Skins da picareta: mudam material, prefixo de cor e efeito (particula/som) ao minerar.
 */
public enum PickaxeSkin {

    DEFAULT("Padrão", Material.DIAMOND_PICKAXE, "§7", null, null),
    NETHERITE("Netherite", Material.NETHERITE_PICKAXE, "§8", null, Sound.ITEM_ARMOR_EQUIP_NETHERITE),
    DRAGON("Dragão", Material.NETHERITE_PICKAXE, "§5", Particle.DRAGON_BREATH, Sound.ENTITY_ENDER_DRAGON_GROWL),
    CRYSTAL("Cristal", Material.DIAMOND_PICKAXE, "§b", Particle.END_ROD, Sound.BLOCK_AMETHYST_BLOCK_CHIME),
    EVENTO("Evento", Material.GOLDEN_PICKAXE, "§6", Particle.FIREWORK, Sound.ENTITY_PLAYER_LEVELUP);

    private final String displayName;
    private final Material material;
    private final String colorPrefix;
    private final Particle particle;
    private final Sound sound;

    PickaxeSkin(String displayName, Material material, String colorPrefix, Particle particle, Sound sound) {
        this.displayName = displayName;
        this.material = material;
        this.colorPrefix = colorPrefix;
        this.particle = particle;
        this.sound = sound;
    }

    public static PickaxeSkin fromId(String id) {
        if (id == null) {
            return DEFAULT;
        }
        try {
            return valueOf(id.toUpperCase());
        } catch (Exception e) {
            return DEFAULT;
        }
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public String getColorPrefix() {
        return colorPrefix;
    }

    public Particle getParticle() {
        return particle;
    }

    public Sound getSound() {
        return sound;
    }
}
