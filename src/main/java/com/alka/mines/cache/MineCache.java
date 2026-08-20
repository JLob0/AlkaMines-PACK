package com.alka.mines.cache;

import com.alka.mines.model.MineBlock;
import com.alka.mines.model.MineRegion;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Cache O(1) (Caffeine) de blocos de composicao de mina, indexado por chunk. O load
 * varre a regiao uma unica vez (O(volume), na main thread - acessa World.getBlockAt);
 * depois disso cada lookup de chunk e getIfPresent O(1).
 *
 * Read-only apos o load: a habilidade do dragao consulta mas NUNCA modifica o cache.
 */
public class MineCache {

    private final Cache<CachedMineKey, MineBlockData[]> cache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

    /** Carrega uma mina sem composicao (rewardChance fica 0). Deve rodar na main thread. */
    public void loadMine(String mineId, MineRegion region) {
        loadMine(mineId, region, null);
    }

    /** Carrega uma mina na main thread; composition (opcional) e usada pra derivar rewardChance. */
    public void loadMine(String mineId, MineRegion region, List<MineBlock> composition) {
        World world = Bukkit.getWorld(region.getWorld());
        if (world == null) {
            return;
        }

        Map<CachedMineKey, List<MineBlockData>> grouped = new HashMap<>();
        for (int y = region.getY2(); y >= region.getY1(); y--) {
            for (int x = region.getX1(); x <= region.getX2(); x++) {
                for (int z = region.getZ1(); z <= region.getZ2(); z++) {
                    Material mat = world.getBlockAt(x, y, z).getType();
                    if (mat == Material.AIR) {
                        continue;
                    }
                    double chance = 0;
                    if (composition != null) {
                        for (MineBlock mb : composition) {
                            if (mb.getMaterial() == mat) {
                                chance = mb.getWeight();
                                break;
                            }
                        }
                    }
                    MineBlockData data = new MineBlockData(region.getWorld(), x, y, z, mat, chance, null, true);
                    int chunkX = x >> 4;
                    int chunkZ = z >> 4;
                    grouped.computeIfAbsent(new CachedMineKey(mineId, chunkX, chunkZ), k -> new ArrayList<>()).add(data);
                }
            }
        }

        for (Map.Entry<CachedMineKey, List<MineBlockData>> entry : grouped.entrySet()) {
            cache.put(entry.getKey(), entry.getValue().toArray(new MineBlockData[0]));
        }
    }

    /** Lookup O(1) do array de blocos de um chunk. */
    public MineBlockData[] getBlocksInChunk(String mineId, int chunkX, int chunkZ) {
        return cache.getIfPresent(new CachedMineKey(mineId, chunkX, chunkZ));
    }

    /** Acha o MineBlockData em x,y,z exatos, se existir no cache. */
    public Optional<MineBlockData> findBlockAt(String mineId, Location loc) {
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        MineBlockData[] blocks = getBlocksInChunk(mineId, chunkX, chunkZ);
        if (blocks == null) {
            return Optional.empty();
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        for (MineBlockData data : blocks) {
            if (data.x() == x && data.y() == y && data.z() == z) {
                return Optional.of(data);
            }
        }
        return Optional.empty();
    }

    /** Remove todas as entradas do cache que pertencem a uma mina. */
    public void invalidateMine(String mineId) {
        cache.asMap().keySet().removeIf(key -> key.mineId().equals(mineId));
    }

    public CacheStats getStats() {
        return cache.stats();
    }

    /** Numero de entradas (chunks) atualmente no cache. */
    public long getSize() {
        return cache.asMap().size();
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }
}
