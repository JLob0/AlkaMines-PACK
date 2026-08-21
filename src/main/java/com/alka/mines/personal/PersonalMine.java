package com.alka.mines.personal;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Mina pessoal evolutiva. O mundo real so tem BEDROCK no chao e AR no resto; os blocos
 * visiveis sao virtuais (client-side, via VirtualMineGrid). Cresce com o nivel da picareta.
 */
public class PersonalMine {

    private final UUID ownerUuid;
    private final String worldName;
    private final int centerX;
    private final int centerZ;
    private final int depth;
    private final int startSize;
    private final int expandEveryLevels;
    private final int maxSize;
    private int size;
    private int level;
    private boolean generated;
    private int surfaceY;

    public PersonalMine(UUID ownerUuid, String worldName, int centerX, int centerZ, int surfaceY,
                        int startSize, int depth, int expandEveryLevels, int maxSize) {
        this.ownerUuid = ownerUuid;
        this.worldName = worldName;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.surfaceY = surfaceY;
        this.startSize = startSize;
        this.depth = depth;
        this.expandEveryLevels = expandEveryLevels;
        this.maxSize = maxSize;
        this.size = startSize;
        this.level = 1;
        this.generated = false;
    }

    public Bounds getBounds() {
        int half = size / 2;
        return new Bounds(
                centerX - half, centerX + half,
                surfaceY - depth, surfaceY - 2,
                centerZ - half, centerZ + half
        );
    }

    public boolean shouldExpand(int pickaxeLevel) {
        // cresce 1 a cada expandEveryLevels niveis (teto = maxSize). NUNCA encolhe:
        // depois do prestigio o nivel reseta, mas a mina mantem o tamanho ja conquistado.
        int newSize = startSize + (pickaxeLevel / expandEveryLevels) * 1;
        if (newSize > maxSize) {
            newSize = maxSize;
        }
        if (newSize > this.size) {
            this.size = newSize;
            return true;
        }
        return false;
    }

    public boolean isInside(Location loc) {
        if (!loc.getWorld().getName().equals(worldName)) {
            return false;
        }
        Bounds b = getBounds();
        return loc.getX() >= b.minX() && loc.getX() <= b.maxX()
                && loc.getY() >= b.minY() && loc.getY() <= b.maxY()
                && loc.getZ() >= b.minZ() && loc.getZ() <= b.maxZ();
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getDepth() {
        return depth;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void setGenerated(boolean generated) {
        this.generated = generated;
    }

    public int getSurfaceY() {
        return surfaceY;
    }

    public record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }
}
