package com.alka.mines.lifecycle;

import com.alka.mines.cache.MineCache;
import com.alka.mines.entity.FakeDragonEntity;
import com.alka.mines.entity.FakeEntityRegistry;
import com.alka.mines.model.Mine;
import com.alka.mines.reward.RewardBatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia as sessoes de habilidade: no maximo UMA sessao ativa por jogador. Tambem
 * guarda o cooldown da habilidade (em memoria) e garante cleanup no quit (listener).
 */
public class MineAbilitySessionManager implements Listener {

    private final Map<UUID, MineAbilitySession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDragonUse = new ConcurrentHashMap<>();
    private final TaskManager taskManager;
    private final FakeEntityRegistry registry;

    public MineAbilitySessionManager(TaskManager taskManager, FakeEntityRegistry registry) {
        this.taskManager = taskManager;
        this.registry = registry;
    }

    public MineAbilitySession startSession(Player player, Mine mine, FakeDragonEntity dragon,
                                           RewardBatcher batcher, Plugin plugin, MineCache cache) {
        endSession(player);
        MineAbilitySession session = new MineAbilitySession(player.getUniqueId(), mine, dragon, batcher,
                plugin, cache, this);
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public void endSession(Player player) {
        endSession(player.getUniqueId());
    }

    public void endSession(UUID uuid) {
        MineAbilitySession session = sessions.remove(uuid);
        if (session != null) {
            session.endSession();
        }
    }

    public void endAllSessions() {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            endSession(uuid);
        }
    }

    /** Remocao sem re-disparar endSession - chamado pelo proprio endSession(). */
    void removeByUuid(UUID uuid) {
        sessions.remove(uuid);
    }

    public Optional<MineAbilitySession> getSession(Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    public boolean isInSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Visao das sessoes ativas (usado pelo /mina debug). */
    public Collection<MineAbilitySession> getAllSessions() {
        return sessions.values();
    }

    /** ms restantes de cooldown, 0 se pode usar. */
    public long getRemainingCooldownMs(UUID uuid, long cooldownMs) {
        Long last = lastDragonUse.get(uuid);
        if (last == null) {
            return 0;
        }
        return Math.max(0, cooldownMs - (System.currentTimeMillis() - last));
    }

    public void markUsed(UUID uuid) {
        lastDragonUse.put(uuid, System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        endSession(event.getPlayer());
        lastDragonUse.remove(event.getPlayer().getUniqueId());
    }
}
