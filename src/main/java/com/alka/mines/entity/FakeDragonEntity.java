package com.alka.mines.entity;

import com.alka.mines.packet.PacketFactory;
import com.alka.mines.util.DebugLogger;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entidade 100% client-side (ENDER_DRAGON) da habilidade do dragao na mina pessoal. O
 * servidor nunca sabe que ela existe - so o jogador recebe os pacotes via ProtocolLib.
 */
public class FakeDragonEntity {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1_000_000);

    private final UUID entityUuid;
    private final int entityId;
    private final Player targetPlayer;
    private volatile Location currentLocation;
    private volatile Location lastLocation;
    private volatile boolean spawned;

    public FakeDragonEntity(Player target, Location spawnLocation) {
        this.targetPlayer = target;
        this.entityUuid = UUID.randomUUID();
        this.entityId = NEXT_ID.getAndIncrement();
        this.currentLocation = spawnLocation;
        this.lastLocation = spawnLocation.clone();
    }

    public void spawn() {
        PacketFactory.sendSpawnLivingEntity(targetPlayer, entityId, entityUuid, EntityType.ENDER_DRAGON, currentLocation);
        PacketFactory.sendEntityMetadata(targetPlayer, entityId, (byte) 0x00, 10);
        this.spawned = true;
    }

    public void teleport(Location newLoc) {
        if (!spawned) {
            return;
        }
        PacketFactory.sendEntityTeleport(targetPlayer, entityId, newLoc);
        this.currentLocation = newLoc.clone();
        this.lastLocation = newLoc.clone();
    }

    /** Movimento relativo + rotacao (REL_ENTITY_MOVE_LOOK) - mais estavel na 1.21.8. */
    public void teleportRelative(Location newLoc) {
        if (!spawned) {
            return;
        }
        double deltaX = (newLoc.getX() - lastLocation.getX()) * 4096.0;
        double deltaY = (newLoc.getY() - lastLocation.getY()) * 4096.0;
        double deltaZ = (newLoc.getZ() - lastLocation.getZ()) * 4096.0;
        deltaX = Math.max(-32767, Math.min(32767, deltaX));
        deltaY = Math.max(-32767, Math.min(32767, deltaY));
        deltaZ = Math.max(-32767, Math.min(32767, deltaZ));

        float yawDeg = (newLoc.getYaw() + 180.0f) % 360.0f;
        if (yawDeg < 0) {
            yawDeg += 360.0f;
        }
        byte yaw = (byte) (yawDeg * 256.0F / 360.0F);
        byte pitch = 0;

        DebugLogger.log("Dragon: move relativo dx=%d dy=%d dz=%d para %s",
                (short) deltaX, (short) deltaY, (short) deltaZ, targetPlayer.getName());

        PacketFactory.sendEntityMoveLook(targetPlayer, entityId,
                (short) deltaX, (short) deltaY, (short) deltaZ, yaw, pitch);
        this.currentLocation = newLoc.clone();
        this.lastLocation = newLoc.clone();
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
