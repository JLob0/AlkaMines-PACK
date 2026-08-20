package com.alka.mines.cache;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;

/**
 * Representacao imutavel de um bloco de composicao de mina carregado no cache. So
 * dados - o cache e read-only apos o load (a habilidade do dragao nunca o modifica).
 */
public record MineBlockData(String world, int x, int y, int z, Material originalMaterial,
                            double rewardChance, String rewardItemId, boolean regenerable) {

    public Location getLocation(Server server) {
        World w = server.getWorld(world);
        return new Location(w, x, y, z);
    }

    /** Chave longa do chunk (X<<32 | Z), mesma do MineCache. */
    public long getChunkKey() {
        return ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
    }
}
