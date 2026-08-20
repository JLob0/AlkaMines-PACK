package com.alka.mines.entity;

import com.alka.mines.packet.PacketFactory;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entidade 100% client-side: o servidor NUNCA sabe que ela existe (nao usa
 * {@code world.spawnEntity()} nem qualquer API Bukkit de spawn). So o jogador alvo
 * recebe os pacotes (spawn, metadata, teleporte, destroy) via ProtocolLib.
 *
 * O entityId começa em 1_000_000 pra nao colidir com os IDs reais que o servidor
 * distribui.
 */
public class FakeDragonEntity {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1_000_000);

    private final UUID entityUuid;
    private final int entityId;
    private final Player targetPlayer;
    private volatile Location currentLocation;
    private volatile boolean spawned;

    public FakeDragonEntity(Player target, Location spawnLocation) {
        this.targetPlayer = target;
        this.entityUuid = UUID.randomUUID();
        this.entityId = NEXT_ID.getAndIncrement();
        this.currentLocation = spawnLocation;
    }

    /** Envia spawn + metadata pro jogador alvo (dragao visivel em voo circular). */
    public void spawn() {
        PacketFactory.sendSpawnLivingEntity(targetPlayer, entityId, entityUuid, EntityType.ENDER_DRAGON, currentLocation);
        PacketFactory.sendEntityMetadata(targetPlayer, entityId, (byte) 0x00, 10);
        this.spawned = true;
    }

    /** Teleporta a entidade pro jogador (sync, deve rodar na main thread). */
    public void teleport(Location newLoc) {
        if (!spawned) {
            return;
        }
        PacketFactory.sendEntityTeleport(targetPlayer, entityId, newLoc);
        this.currentLocation = newLoc;
    }

    /** Faz a entidade olhar pra um ponto do mundo (via setDirection + yaw/pitch). */
    public void lookAt(Vector target) {
        if (!spawned) {
            return;
        }
        Vector direction = target.clone().subtract(currentLocation.toVector());
        teleport(currentLocation.clone().setDirection(direction));
    }

    public void destroy() {
        if (!spawned) {
            return;
        }
        PacketFactory.sendEntityDestroy(targetPlayer, entityId);
        this.spawned = false;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public Player getTargetPlayer() {
        return targetPlayer;
    }

    public int getEntityId() {
        return entityId;
    }
}
