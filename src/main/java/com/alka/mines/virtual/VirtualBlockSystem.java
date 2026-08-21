package com.alka.mines.virtual;

import com.alka.mines.hook.AlkaEconomyHook;
import com.alka.mines.packet.PacketFactory;
import com.alka.mines.pickaxe.PickaxeManager;
import com.alka.mines.visual.FloatingTextManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Optional;

/**
 * Sistema de blocos virtuais (client-side) - ANTI-LAG das minas pessoais.
 *
 * A mina inteira vive numa {@link VirtualMineGrid}; o mundo real so tem BEDROCK no chao
 * e AR no resto. Ao quebrar um bloco virtual, envia a quebra visual + som + particula,
 * marca o bloco como AIR, deposita o valor de venda direto na economia (sem ItemEntity)
 * e agenda o respawn do bloco. NUNCA usa World#setType em bloco de mina pessoal.
 */
public class VirtualBlockSystem {

    private final Plugin plugin;
    private final int respawnDelayTicks;
    private final PickaxeManager pickaxeManager;
    private final FloatingTextManager floatingTextManager;

    public VirtualBlockSystem(Plugin plugin, int respawnDelayTicks, PickaxeManager pickaxeManager,
                              FloatingTextManager floatingTextManager) {
        this.plugin = plugin;
        this.respawnDelayTicks = respawnDelayTicks;
        this.pickaxeManager = pickaxeManager;
        this.floatingTextManager = floatingTextManager;
    }

    /**
     * Quebra um bloco virtual. Retorna true se quebrou (bloco existia e era quebravel),
     * false caso contrario. NAO mexe no mundo real - so pacotes + grade em memoria.
     */
    public boolean handleBreak(Player player, Location loc, VirtualMineGrid grid) {
        BlockPos pos = new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        VirtualBlock block = grid.getBlockAt(pos);
        if (block == null || !block.breakable()) {
            return false;
        }

        // quebra visual: som + particula + AIR no client do jogador
        PacketFactory.sendBlockBreakEffect(player, loc, block.material());
        PacketFactory.sendBlockChange(player, loc, Material.AIR);
        grid.setBlock(pos, new VirtualBlock(Material.AIR, 0.0, false));

        // venda automatica - deposita o valor direto (sem spawnar item)
        double earned = depositSellValue(player, block);
        if (earned > 0 && floatingTextManager != null) {
            floatingTextManager.spawnBreakText(player, loc, "§a+$" + String.format("%.0f", earned));
        }

        // respawn do bloco apos o delay (visual: volta a aparecer)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                VirtualBlock current = grid.getBlockAt(pos);
                if (current != null && current.material() == Material.AIR) {
                    grid.setBlock(pos, block);
                    PacketFactory.sendBlockChange(player, loc, block.material());
                }
            }
        }.runTaskLater(plugin, respawnDelayTicks);

        return true;
    }

    private double depositSellValue(Player player, VirtualBlock block) {
        AlkaEconomyHook economy = AlkaEconomyHook.getInstance();
        if (economy != null && block.sellValue() > 0) {
            double multiplier = pickaxeManager != null ? pickaxeManager.getMoneyMultiplier(player) : 1.0;
            double amount = block.sellValue() * multiplier;
            economy.deposit(player.getUniqueId(), "GOLD", amount);
            return amount;
        }
        return 0;
    }

    /** Respawn imediato do bloco (usado no reset/regeneracao da mina). */
    public void forceRespawn(Player player, Location loc, VirtualMineGrid grid, Material material) {
        BlockPos pos = new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        VirtualBlock existing = grid.getBlockAt(pos);
        if (existing != null) {
            grid.setBlock(pos, existing);
        } else {
            grid.setBlock(pos, new VirtualBlock(material, 0.0, true));
        }
        if (player.isOnline()) {
            PacketFactory.sendBlockChange(player, loc, material);
        }
    }

    public Optional<VirtualBlock> getBlock(VirtualMineGrid grid, Location loc) {
        return Optional.ofNullable(grid.getBlockAt(new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())));
    }

    public static boolean isVirtualBlockWorld(String worldName, String mineWorld) {
        return worldName != null && worldName.equals(mineWorld);
    }
}
