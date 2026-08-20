package com.alka.mines.cache;

/** Chave composta usada no MineCache: (mina, chunk). */
public record CachedMineKey(String mineId, int chunkX, int chunkZ) {
}
