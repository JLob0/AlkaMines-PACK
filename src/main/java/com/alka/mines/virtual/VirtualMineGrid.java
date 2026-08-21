package com.alka.mines.virtual;

import com.alka.mines.config.MineConfig;
import com.alka.mines.personal.PersonalMine;
import org.bukkit.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Grade de blocos virtuais de uma mina pessoal. So dados em memoria - nunca toca no
 * mundo real. O material de cada posicao vem da composicao do nivel (config.yml) e cada
 * bloco carrega seu valor de venda (tambem do config).
 */
public class VirtualMineGrid {

    private final Map<BlockPos, VirtualBlock> blocks = new HashMap<>();
    private final Random random = new Random();

    /** Gera a grade do interior da mina (excluindo as paredes). */
    public void generate(PersonalMine mine, int level) {
        blocks.clear();
        PersonalMine.Bounds b = mine.getBounds();
        Map<Material, Double> composition = MineConfig.get().getCompositionForLevel(level);
        if (composition == null || composition.isEmpty()) {
            composition = Map.of(Material.STONE, 1.0);
        }
        Map<Material, Double> sellValues = MineConfig.get().getSellValues();

        for (int x = b.minX() + 1; x < b.maxX(); x++) {
            for (int z = b.minZ() + 1; z < b.maxZ(); z++) {
                // mina começa 2 blocos acima do chao de bedrock (nao encosta na bedrock)
                for (int y = b.minY() + 4; y <= b.maxY(); y++) {
                    Material mat = rollMaterial(composition);
                    double value = sellValues.getOrDefault(mat, 1.0);
                    blocks.put(new BlockPos(x, y, z), new VirtualBlock(mat, value, true));
                }
            }
        }
    }

    private Material rollMaterial(Map<Material, Double> composition) {
        double roll = random.nextDouble();
        double cumulative = 0.0;
        for (Map.Entry<Material, Double> entry : composition.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }
        return Material.STONE;
    }

    public VirtualBlock getBlockAt(BlockPos pos) {
        return blocks.get(pos);
    }

    public void setBlock(BlockPos pos, VirtualBlock block) {
        blocks.put(pos, block);
    }

    public Map<BlockPos, VirtualBlock> getAllBlocks() {
        return Collections.unmodifiableMap(blocks);
    }
}
