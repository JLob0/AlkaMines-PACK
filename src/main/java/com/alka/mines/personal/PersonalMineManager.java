package com.alka.mines.personal;

import com.alka.mines.config.MineConfig;
import com.alka.mines.packet.PacketFactory;
import com.alka.mines.virtual.BlockPos;
import com.alka.mines.virtual.VirtualBlock;
import com.alka.mines.virtual.VirtualMineGrid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia as minas pessoais dos jogadores: cria, gera (world real = BEDROCK + AR, resto
 * virtual) e expande. Valores (tamanho, profundidade, expansao, mundo, composicao) vêm
 * do config.yml via {@link MineConfig}. A mina pessoal do jogador fica numa posicao
 * deterministica derivada do UUID (grid de spacing), entao nunca muda de lugar.
 */
public class PersonalMineManager {

    private final Map<UUID, PersonalMine> mines = new ConcurrentHashMap<>();
    private final Map<UUID, VirtualMineGrid> grids = new ConcurrentHashMap<>();
    private final org.bukkit.plugin.Plugin plugin;

    public PersonalMineManager(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    public PersonalMine getOrCreateMine(Player player) {
        return mines.computeIfAbsent(player.getUniqueId(), uuid -> {
            MineConfig cfg = MineConfig.get();
            long hash = Math.abs(uuid.getMostSignificantBits());
            int offsetX = (int) (hash % 10000) * cfg.getGridSpacing();
            int offsetZ = (int) ((hash >> 32) % 10000) * cfg.getGridSpacing();

            PersonalMine mine = new PersonalMine(uuid, cfg.getMineWorld(), offsetX, offsetZ, cfg.getSurfaceY(),
                    cfg.getStartSize(), cfg.getDepth(), cfg.getExpandEveryLevels(), cfg.getMaxSize());
            generateMine(mine, player);
            return mine;
        });
    }

    public void expandIfNeeded(Player player, int pickaxeLevel) {
        PersonalMine mine = mines.get(player.getUniqueId());
        if (mine == null) {
            return;
        }
        if (mine.shouldExpand(pickaxeLevel)) {
            regenerateMine(mine, player);
        }
    }

    /** Prepara o mundo real (BEDROCK + AR) e gera a grade virtual, enviando tudo pro jogador. */
    public void generateMine(PersonalMine mine, Player player) {
        World world = Bukkit.getWorld(MineConfig.get().getMineWorld());
        if (world == null) {
            throw new IllegalStateException("Mundo de minas não encontrado: " + MineConfig.get().getMineWorld());
        }

        PersonalMine.Bounds b = mine.getBounds();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
                // chao de bedrock real (2 blocos) - impede cair
                world.getBlockAt(x, b.minY(), z).setType(Material.BEDROCK, false);
                world.getBlockAt(x, b.minY() + 1, z).setType(Material.BEDROCK, false);
            }
        }

        VirtualMineGrid grid = new VirtualMineGrid();
        grid.generate(mine, mine.getLevel());
        grids.put(mine.getOwnerUuid(), grid);

        sendGridToPlayer(player, grid);
        mine.setGenerated(true);
    }

    /** Envia todos os blocos virtuais da grade pro jogador (bulk block changes). */
    private void sendGridToPlayer(Player player, VirtualMineGrid grid) {
        World world = Bukkit.getWorld(MineConfig.get().getMineWorld());
        if (world == null) {
            return;
        }
        Map<Location, Material> bulk = new HashMap<>();
        for (Map.Entry<BlockPos, VirtualBlock> entry : grid.getAllBlocks().entrySet()) {
            BlockPos pos = entry.getKey();
            bulk.put(new Location(world, pos.x(), pos.y(), pos.z()), entry.getValue().material());
        }
        com.alka.mines.util.DebugLogger.log("Enviando %d blocos virtuais para %s", bulk.size(), player.getName());
        PacketFactory.sendBulkBlockChanges(player, bulk);
    }

    public void regenerateMine(PersonalMine mine, Player player) {
        grids.remove(mine.getOwnerUuid());
        generateMine(mine, player);
    }

    public VirtualMineGrid getGrid(UUID uuid) {
        return grids.get(uuid);
    }

    public PersonalMine getMine(UUID uuid) {
        return mines.get(uuid);
    }

    public void saveAll() {
        // Persistencia das minas fica para uma fase posterior (repository).
    }

    /** Teleporta pro fundo do poco (sobre o BEDROCK real), olhando pra cima - o jogador
     * mina os blocos virtuais ao redor sem cair (o mundo real ali e AR/BEDROCK). */
    public void teleportToMine(Player player) {
        PersonalMine mine = getOrCreateMine(player);
        PersonalMine.Bounds b = mine.getBounds();
        Location bottom = new Location(
                Bukkit.getWorld(MineConfig.get().getMineWorld()),
                mine.getCenterX(), b.minY() + 2, mine.getCenterZ(),
                0, -90
        );
        player.teleport(bottom);

        // Reenvia os blocos virtuais varias vezes apos o teleporte (ate ~3s) porque o
        // client so aplica BLOCK_CHANGE em chunk ja enviado; se enviar antes do chunk
        // chegar, ele sobrescreve com o estado real (AR) e some tudo.
        VirtualMineGrid grid = grids.get(player.getUniqueId());
        if (grid != null) {
            final int[] attempts = {0};
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                if (!player.isOnline()) {
                    task.cancel();
                    return;
                }
                sendGridToPlayer(player, grid);
                attempts[0]++;
                if (attempts[0] >= 12) {
                    task.cancel();
                }
            }, 5L, 5L);
        }
    }
}
