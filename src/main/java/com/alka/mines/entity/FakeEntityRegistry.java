package com.alka.mines.entity;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registro de entidades fake por jogador - destroi tudo no quit. */
public class FakeEntityRegistry implements Listener {

    private final Map<UUID, List<FakeDragonEntity>> entitiesByPlayer = new ConcurrentHashMap<>();

    public void register(Player player, FakeDragonEntity entity) {
        entitiesByPlayer.computeIfAbsent(player.getUniqueId(), k -> new CopyOnWriteArrayList<>()).add(entity);
    }

    public void unregister(Player player, FakeDragonEntity entity) {
        entity.destroy();
        List<FakeDragonEntity> list = entitiesByPlayer.get(player.getUniqueId());
        if (list != null) {
            list.remove(entity);
        }
    }

    public void unregisterAll(Player player) {
        List<FakeDragonEntity> list = entitiesByPlayer.remove(player.getUniqueId());
        if (list != null) {
            for (FakeDragonEntity entity : list) {
                entity.destroy();
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        unregisterAll(event.getPlayer());
    }
}
