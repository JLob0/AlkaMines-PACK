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
    /** Posicao anterior - usada pra calcular o delta do movimento relativo. */
    private volatile Location lastLocation;
    /** Direcao real de movimento (sem o +180° da correcao visual). */
    private volatile Vector realDirection;
    private volatile boolean spawned;

    public FakeDragonEntity(Player target, Location spawnLocation) {
        this.targetPlayer = target;
        this.entityUuid = UUID.randomUUID();
        this.entityId = NEXT_ID.getAndIncrement();
        this.currentLocation = spawnLocation;
        this.lastLocation = spawnLocation.clone();
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
        this.currentLocation = newLoc.clone();
        this.lastLocation = newLoc.clone();
    }

    /** Movimento relativo + rotacao (REL_ENTITY_MOVE_LOOK) - mais estavel na 1.21.2+. */
    public void teleportRelative(Location newLoc) {
        if (!spawned) {
            return;
        }

        // delta em unidades de 1/4096 de bloco (protocolo Minecraft)
        double deltaX = (newLoc.getX() - lastLocation.getX()) * 4096.0;
        double deltaY = (newLoc.getY() - lastLocation.getY()) * 4096.0;
        double deltaZ = (newLoc.getZ() - lastLocation.getZ()) * 4096.0;

        // clamp pra nao estourar o short (±32767 = ±7.999 blocos/tick)
        deltaX = Math.max(-32767, Math.min(32767, deltaX));
        deltaY = Math.max(-32767, Math.min(32767, deltaY));
        deltaZ = Math.max(-32767, Math.min(32767, deltaZ));

        // yaw invertido 180° porque o modelo do Ender Dragon e de costas.
        float yawDeg = (newLoc.getYaw() + 180.0f) % 360.0f;
        if (yawDeg < 0) {
            yawDeg += 360.0f;
        }
        // pitch forçado a 0 (voo horizontal) - o dragao circula na mesma altura.
        byte yaw = (byte) (yawDeg * 256.0F / 360.0F);
        byte pitch = 0;

        DebugLogger.log("Dragon: move relativo dx=%d dy=%d dz=%d para %s",
                (short) deltaX, (short) deltaY, (short) deltaZ, targetPlayer.getName());

        PacketFactory.sendEntityMoveLook(targetPlayer, entityId,
                (short) deltaX, (short) deltaY, (short) deltaZ, yaw, pitch);

        this.currentLocation = newLoc.clone();
        this.lastLocation = newLoc.clone();
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

    /** Expõe a direcao real de movimento (sem a correcao visual de +180°). */
    public void setRealDirection(Vector direction) {
        this.realDirection = direction != null ? direction.clone().normalize() : null;
    }

    public Vector getRealDirection() {
        return realDirection;
    }
}
