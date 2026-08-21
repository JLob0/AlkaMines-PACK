package com.alka.mines.packet;

import com.alka.mines.util.DebugLogger;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedParticle;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Centraliza a construcao e o envio de pacotes visuais via ProtocolLib (NMS 1.21.8
 * compativel). Usado pelas entidades fake client-side e pela habilidade do dragao.
 *
 * Nenhum metodo aqui envolve em try-catch generico de proposito - excecoes sobem pro
 * caller logar. Todos verificam {@link Player#isOnline()} antes de enviar. Logs de
 * debug (ligados via config.yml debug: true) ajudam a rastrear qual pacote foi enviado.
 */
public final class PacketFactory {

    private PacketFactory() {
    }

    private static ProtocolManager manager() {
        return ProtocolLibrary.getProtocolManager();
    }

    /** Spawn de uma entidade viva client-side (ENDER_DRAGON). Preenche TODOS os campos
     * exigidos pelo SPAWN_ENTITY unificado (1.20.2+): velocity, headYaw e data. */
    public static void sendSpawnLivingEntity(Player target, int entityId, UUID entityUuid, EntityType type, Location location) {
        if (!target.isOnline()) {
            return;
        }
        DebugLogger.log("PacketFactory: SPAWN_ENTITY id=%d tipo=%s para %s", entityId, type, target.getName());

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        // Integers: [0]=entityId, [1]=data (VarInt). Velocity e um trio de shorts.
        packet.getIntegers().write(0, entityId);
        if (packet.getIntegers().size() > 1) {
            packet.getIntegers().write(1, 0); // entity data (0 = nenhum dado extra)
        }
        packet.getUUIDs().write(0, entityUuid);
        packet.getEntityTypeModifier().write(0, type);
        packet.getDoubles().write(0, location.getX());
        packet.getDoubles().write(1, location.getY());
        packet.getDoubles().write(2, location.getZ());

        byte yaw = (byte) (location.getYaw() * 256.0F / 360.0F);
        byte pitch = (byte) (location.getPitch() * 256.0F / 360.0F);
        // Bytes: [0]=yaw, [1]=pitch, [2]=headYaw
        packet.getBytes().write(0, yaw);
        packet.getBytes().write(1, pitch);
        if (packet.getBytes().size() > 2) {
            packet.getBytes().write(2, yaw); // headYaw = yaw para entidades voadoras
        }
        // Velocity (shorts) - zerado, entidade estatica
        if (packet.getShorts().size() >= 3) {
            packet.getShorts().write(0, (short) 0);
            packet.getShorts().write(1, (short) 0);
            packet.getShorts().write(2, (short) 0);
        }
        manager().sendServerPacket(target, packet);
    }

    /** Metadata da entidade: index 0 = flags Byte, index 16 = dragonPhase (10 = voo circular). */
    public static void sendEntityMetadata(Player target, int entityId, byte flags, int dragonPhase) {
        if (!target.isOnline()) {
            return;
        }
        DebugLogger.log("PacketFactory: ENTITY_METADATA id=%d fase=%d para %s", entityId, dragonPhase, target.getName());

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, entityId);
        List<WrappedDataValue> values = new ArrayList<>();
        values.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), flags));
        values.add(new WrappedDataValue(16, WrappedDataWatcher.Registry.get(Integer.class), dragonPhase));
        packet.getDataValueCollectionModifier().write(0, values);
        manager().sendServerPacket(target, packet);
    }

    /** Teleporte absoluto de uma entidade client-side. Na 1.21.8 o ENTITY_TELEPORT
     * refatorou a posicao pra um unico Vec3 (getVectors) - fallback pra doubles. */
    public static void sendEntityTeleport(Player target, int entityId, Location location) {
        if (!target.isOnline()) {
            return;
        }
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);
        packet.getIntegers().write(0, entityId);
        if (packet.getVectors().size() > 0) {
            packet.getVectors().write(0,
                    new Vector(location.getX(), location.getY(), location.getZ()));
        } else if (packet.getDoubles().size() >= 3) {
            packet.getDoubles().write(0, location.getX());
            packet.getDoubles().write(1, location.getY());
            packet.getDoubles().write(2, location.getZ());
        }
        if (packet.getBytes().size() >= 2) {
            packet.getBytes().write(0, (byte) (location.getYaw() * 256.0F / 360.0F));
            packet.getBytes().write(1, (byte) (location.getPitch() * 256.0F / 360.0F));
        }
        manager().sendServerPacket(target, packet);
    }

    /** Movimento relativo + rotacao (REL_ENTITY_MOVE_LOOK). Mais estavel que o
     * ENTITY_TELEPORT na 1.21.2+. Deltas em unidades de 1/4096 de bloco. */
    public static void sendEntityMoveLook(Player target, int entityId,
                                          short deltaX, short deltaY, short deltaZ, byte yaw, byte pitch) {
        if (!target.isOnline()) {
            return;
        }
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK);
        packet.getIntegers().write(0, entityId);
        packet.getShorts().write(0, deltaX);
        packet.getShorts().write(1, deltaY);
        packet.getShorts().write(2, deltaZ);
        packet.getBytes().write(0, yaw);
        packet.getBytes().write(1, pitch);
        if (packet.getBooleans().size() > 0) {
            packet.getBooleans().write(0, false); // onGround = false (voando)
        }
        manager().sendServerPacket(target, packet);
    }

    /** Destroi uma ou mais entidades client-side (intLists com fallback pra integerArrays). */
    public static void sendEntityDestroy(Player target, int... entityIds) {
        if (!target.isOnline()) {
            return;
        }
        DebugLogger.log("PacketFactory: ENTITY_DESTROY ids=%s para %s", Arrays.toString(entityIds), target.getName());

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        List<Integer> ids = Arrays.stream(entityIds).boxed().collect(Collectors.toList());
        try {
            packet.getIntLists().write(0, ids);
        } catch (Exception e) {
            // Fallback: algumas versoes expoem o campo como int[] em vez de List<Integer>.
            packet.getIntegerArrays().write(0, entityIds);
        }
        manager().sendServerPacket(target, packet);
    }

    /** Envia a mudanca de um bloco pra AIR (ou outro Material) so pro jogador. */
    public static void sendBlockChange(Player target, Location location, Material material) {
        if (!target.isOnline()) {
            return;
        }
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.BLOCK_CHANGE);
        packet.getBlockPositionModifier().write(0,
                new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        packet.getBlockData().write(0, WrappedBlockData.createData(material));
        manager().sendServerPacket(target, packet);
    }

    /** Efeito completo de quebra visual: som + particula + animacao de stage 9. */
    public static void sendBlockBreakEffect(Player target, Location location, Material material) {
        if (!target.isOnline()) {
            return;
        }
        // som de quebra
        sendNamedSound(target, location, Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);

        // particula de quebra do material (Particle.BLOCK - antigo BLOCK_CRACK - data = BlockData)
        PacketContainer particles = new PacketContainer(PacketType.Play.Server.WORLD_PARTICLES);
        particles.getNewParticles().write(0, WrappedParticle.create(Particle.BLOCK, material.createBlockData()));
        particles.getIntegers().write(0, 20);
        particles.getDoubles().write(0, location.getX());
        particles.getDoubles().write(1, location.getY());
        particles.getDoubles().write(2, location.getZ());
        particles.getFloat().write(0, 0.5f);
        particles.getFloat().write(1, 0.5f);
        particles.getFloat().write(2, 0.5f);
        particles.getFloat().write(3, 0f);
        manager().sendServerPacket(target, particles);

        // animacao de quebra (entityId ficticio, stage 9 = quebra completa)
        PacketContainer anim = new PacketContainer(PacketType.Play.Server.BLOCK_BREAK_ANIMATION);
        anim.getIntegers().write(0, target.getEntityId() + 100000);
        anim.getBlockPositionModifier().write(0,
                new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        anim.getIntegers().write(1, 9);
        manager().sendServerPacket(target, anim);
    }

    /** Envia varias mudancas de bloco de uma vez (loop de sendBlockChange). */
    public static void sendBulkBlockChanges(Player target, java.util.Map<Location, Material> changes) {
        if (!target.isOnline()) {
            return;
        }
        for (java.util.Map.Entry<Location, Material> entry : changes.entrySet()) {
            sendBlockChange(target, entry.getKey(), entry.getValue());
        }
    }

    /** Particula em uma posicao do mundo, visivel so pro jogador alvo. */
    public static void sendParticle(Player target, Location location, Particle particle, int count,
                                    double offsetX, double offsetY, double offsetZ, double speed) {
        if (!target.isOnline()) {
            return;
        }
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.WORLD_PARTICLES);
        packet.getNewParticles().write(0, WrappedParticle.create(particle, null));
        packet.getIntegers().write(0, count);
        packet.getDoubles().write(0, location.getX());
        packet.getDoubles().write(1, location.getY());
        packet.getDoubles().write(2, location.getZ());
        packet.getFloat().write(0, (float) offsetX);
        packet.getFloat().write(1, (float) offsetY);
        packet.getFloat().write(2, (float) offsetZ);
        packet.getFloat().write(3, (float) speed);
        manager().sendServerPacket(target, packet);
    }

    /** Som nomeado (efeito de som) em uma posicao. */
    public static void sendNamedSound(Player target, Location location, Sound sound, float volume, float pitch) {
        if (!target.isOnline()) {
            return;
        }
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.NAMED_SOUND_EFFECT);
        packet.getSoundEffects().write(0, sound);
        packet.getSoundCategories().write(0, EnumWrappers.SoundCategory.MASTER);
        // coordenadas em ponto fixo (x8) no pacote NAMED_SOUND_EFFECT.
        packet.getIntegers().write(0, location.getBlockX() * 8);
        packet.getIntegers().write(1, location.getBlockY() * 8);
        packet.getIntegers().write(2, location.getBlockZ() * 8);
        packet.getFloat().write(0, volume);
        packet.getFloat().write(1, pitch);
        manager().sendServerPacket(target, packet);
    }
}
