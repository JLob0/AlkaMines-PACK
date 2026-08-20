package com.alka.mines.ability;

import com.alka.mines.cache.MineBlockData;
import com.alka.mines.cache.MineCache;
import com.alka.mines.entity.FakeDragonEntity;
import com.alka.mines.model.Mine;
import com.alka.mines.packet.PacketFactory;
import com.alka.mines.reward.RewardBatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Raycast do bafo do dragao: roda SYNC (main thread) a cada poucos ticks enquanto a
 * trajetoria dura. Para cada bloco da mina atingido, envia o pacote de quebra (AIR)
 * pro jogador, calcula os drops reais, acumula no {@link RewardBatcher} e marca o bloco
 * pra regeneracao. A quebra e puramente VISUAL - o bloco no servidor so e restaurado
 * na regeneracao (MineAbilityRegenerator).
 */
public class DragonBreathTask implements Runnable {

    private final FakeDragonEntity dragon;
    private final Mine mine;
    private final MineCache cache;
    private final Player player;
    private final RewardBatcher batcher;
    private final Plugin plugin;
    private final Set<Location> brokenBlocks;
    private BukkitTask bukkitTask;

    public DragonBreathTask(FakeDragonEntity dragon, Mine mine, MineCache cache, Player player,
                            RewardBatcher batcher, Plugin plugin, Set<Location> brokenBlocks) {
        this.dragon = dragon;
        this.mine = mine;
        this.cache = cache;
        this.player = player;
        this.batcher = batcher;
        this.plugin = plugin;
        this.brokenBlocks = brokenBlocks;
    }

    public void start() {
        int interval = plugin.getConfig().getInt("abilities.dragon_breath.breath.check-interval-ticks", 2);
        this.bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, Math.max(1, interval));
    }

    @Override
    public void run() {
        if (!dragon.isSpawned() || !player.isOnline()) {
            cancel();
            return;
        }
        if (!dragon.getCurrentLocation().getWorld().getName().equals(mine.getRegion().getWorld())) {
            return;
        }

        // aproximacao da cabeca do dragao
        Location head = dragon.getCurrentLocation().clone().add(0, 2.5, 0);
        Vector direction = dragon.getCurrentLocation().getDirection().normalize();
        int range = plugin.getConfig().getInt("abilities.dragon_breath.breath.range", 25);

        for (int i = 0; i < range; i++) {
            Location point = head.clone().add(direction.clone().multiply(i));
            Optional<MineBlockData> found = cache.findBlockAt(mine.getId(), point);
            if (found.isEmpty()) {
                continue;
            }

            Location blockLoc = point.getBlock().getLocation();
            // quebra visual: envia AIR pro jogador alvo
            PacketFactory.sendBlockChange(player, blockLoc, Material.AIR);

            // drops reais (Fortune/Silk via getDrops), acumulados pra venda/entrega em lote
            Collection<ItemStack> drops = point.getBlock().getDrops(player.getInventory().getItemInMainHand());
            for (ItemStack drop : drops) {
                batcher.addDrop(drop);
            }

            Location fx = blockLoc.clone().add(0.5, 0.5, 0.5);
            PacketFactory.sendParticle(player, fx, Particle.FLAME, 3, 0.2, 0.2, 0.2, 0.01);
            broadcastBreathParticles(fx);

            brokenBlocks.add(blockLoc);
        }
    }

    /** Particulas secundarias pra jogadores proximos (efeito visual em area). */
    private void broadcastBreathParticles(Location fx) {
        for (Player p : fx.getWorld().getPlayers()) {
            if (p != player && p.getLocation().distanceSquared(fx) <= 64.0 * 64.0) {
                PacketFactory.sendParticle(p, fx, Particle.SMOKE, 2, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    private void cancel() {
        if (bukkitTask != null) {
            bukkitTask.cancel();
        }
    }

    public BukkitTask getTask() {
        return bukkitTask;
    }
}
