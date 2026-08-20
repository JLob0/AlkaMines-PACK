package com.alka.mines.packet;

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
 * caller logar. Todos verificam {@link Player#isOnline()} antes de enviar.
 */
public final class PacketFactory {

    private PacketFactory() {
    }

    private static ProtocolManager manager() {
        return ProtocolLibrary.getProtocolManager();
    }

    /** Spawn de uma entidade viva client-side (ENDER_DRAGON). Yaw/Pitch em angulo de 1/256. */
    public static void sendSpawnLivingEntity(Player target, int entityId, UUID entityUuid, EntityType type, Location location) {
        if (!target.isOnline()) {
            return;
        }
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        packet.getIntegers().write(0, entityId);
        packet.getUUIDs().write(0, entityUuid);
        packet.getEntityTypeModifier().write(0, type);
        packet.getDoubles().write(0, location.getX());
        packet.getDoubles().write(1, location.getY());
        packet.getDoubles().write(2, location.getZ());
        packet.getBytes().write(0, (byte) (location.getYaw() * 256.0F / 360.0F));
        packet.getBytes().write(1, (byte) (location.getPitch() * 256.0F / 360.0F));
        manager().sendServerPacket(target, packet);
    }

    /** Metadata da entidade: index 0 = flags Byte, index 15 = dragonPhase (10 = voo circular). */
    public static void sendEntityMetadata(Player target, int entityId, byte flags, int dragonPhase) {
        if (!target.isOnline()) {
            return;
        }
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, entityId);
        List<WrappedDataValue> values = new ArrayList<>();
        values.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), flags));
        values.add(new WrappedDataValue(15, WrappedDataWatcher.Registry.get(Integer.class), dragonPhase));
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

    /** Destroi uma ou mais entidades client-side. */
    public static void sendEntityDestroy(Player target, int... entityIds) {
        if (!target.isOnline()) {
            return;
        }
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        List<Integer> ids = Arrays.stream(entityIds).boxed().collect(Collectors.toList());
        packet.getIntLists().write(0, ids);
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
