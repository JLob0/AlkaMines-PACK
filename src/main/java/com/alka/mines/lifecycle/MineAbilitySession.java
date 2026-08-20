package com.alka.mines.lifecycle;

import com.alka.mines.ability.MineAbilityRegenerator;
import com.alka.mines.cache.MineCache;
import com.alka.mines.entity.FakeDragonEntity;
import com.alka.mines.model.Mine;
import com.alka.mines.reward.RewardBatcher;
import com.alka.mines.trajectory.TrajectoryTask;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uma sessao ativa da habilidade do dragao pra um jogador. O unico caminho de saida e o
 * {@link #endSession()} - que para a trajetoria, cancela o breath task, entrega as
 * recompensas pendentes (flush do batcher), destroi o dragao, agenda a regeneracao dos
 * blocos quebrados e remove a sessao do manager. Nunca deixar uma sessao pendurada.
 */
public class MineAbilitySession {

    private final UUID playerId;
    private final Mine mine;
    private final FakeDragonEntity dragon;
    private final RewardBatcher batcher;
    private final Set<Location> brokenBlocks = ConcurrentHashMap.newKeySet();
    private final long startTime;
    private final Plugin plugin;
    private final MineCache cache;
    private final MineAbilitySessionManager manager;
    private TrajectoryTask trajectory;
    private volatile BukkitTask breathTask;

    public MineAbilitySession(UUID playerId, Mine mine, FakeDragonEntity dragon, RewardBatcher batcher,
                              Plugin plugin, MineCache cache, MineAbilitySessionManager manager) {
        this.playerId = playerId;
        this.mine = mine;
        this.dragon = dragon;
        this.batcher = batcher;
        this.plugin = plugin;
        this.cache = cache;
        this.manager = manager;
        this.startTime = System.currentTimeMillis();
    }

    public void endSession() {
        if (trajectory != null) {
            trajectory.stop();
        }
        if (breathTask != null) {
            breathTask.cancel();
        }
        Location dropLoc = mine.getRegion().getCenter();
        batcher.flush(dropLoc);
        dragon.destroy();
        MineAbilityRegenerator.scheduleRegeneration(mine, new HashSet<>(brokenBlocks), plugin, cache);
        manager.removeByUuid(playerId);
    }

    public void setTrajectory(TrajectoryTask trajectory) {
        this.trajectory = trajectory;
    }

    public void setBreathTask(BukkitTask breathTask) {
        this.breathTask = breathTask;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Mine getMine() {
        return mine;
    }

    public FakeDragonEntity getDragon() {
        return dragon;
    }

    public RewardBatcher getBatcher() {
        return batcher;
    }

    public Set<Location> getBrokenBlocks() {
        return brokenBlocks;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getElapsedMs() {
        return System.currentTimeMillis() - startTime;
    }
}
