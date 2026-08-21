package com.alka.mines.ability;

import com.alka.mines.entity.FakeDragonEntity;
import com.alka.mines.personal.PersonalMine;
import com.alka.mines.personal.PersonalMineManager;
import com.alka.mines.pickaxe.EvolutionPickaxe;
import com.alka.mines.pickaxe.PickaxeLevelManager;
import com.alka.mines.pickaxe.PickaxeManager;
import com.alka.mines.pickaxe.PickaxeProgressDisplay;
import com.alka.mines.virtual.VirtualBlockSystem;
import com.alka.mines.virtual.VirtualMineGrid;
import com.alka.mines.visual.FloatingTextManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Bafo do dragao na mina pessoal: raycast da cabeca em direcao ao centro da mina, quebra
 * os blocos VIRTUAIS atingidos (venda automatica + visual) e avanca a picareta na mao.
 * Roda sync.
 */
public class PersonalDragonTask implements Runnable {

    private final Plugin plugin;
    private final FakeDragonEntity dragon;
    private final PersonalMine mine;
    private final VirtualMineGrid grid;
    private final VirtualBlockSystem virtualBlockSystem;
    private final PickaxeManager pickaxeManager;
    private final PickaxeLevelManager levelManager;
    private final PickaxeProgressDisplay progressDisplay;
    private final FloatingTextManager floatingTextManager;
    private final PersonalMineManager mineManager;
    private BukkitTask bukkitTask;

    public PersonalDragonTask(Plugin plugin, FakeDragonEntity dragon, PersonalMine mine, VirtualMineGrid grid,
                              VirtualBlockSystem virtualBlockSystem, PickaxeManager pickaxeManager,
                              PickaxeLevelManager levelManager, PickaxeProgressDisplay progressDisplay,
                              FloatingTextManager floatingTextManager, PersonalMineManager mineManager) {
        this.plugin = plugin;
        this.dragon = dragon;
        this.mine = mine;
        this.grid = grid;
        this.virtualBlockSystem = virtualBlockSystem;
        this.pickaxeManager = pickaxeManager;
        this.levelManager = levelManager;
        this.progressDisplay = progressDisplay;
        this.floatingTextManager = floatingTextManager;
        this.mineManager = mineManager;
    }

    public void start() {
        int interval = plugin.getConfig().getInt("abilities.dragon_breath.breath.check-interval-ticks", 2);
        this.bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, Math.max(1, interval));
    }

    public void cancel() {
        if (bukkitTask != null) {
            bukkitTask.cancel();
        }
    }

    @Override
    public void run() {
        Player player = dragon.getTargetPlayer();
        if (!dragon.isSpawned() || !player.isOnline()) {
            cancel();
            return;
        }

        Location head = dragon.getCurrentLocation().clone().add(0, 2.5, 0);
        Location center = new Location(dragon.getCurrentLocation().getWorld(),
                mine.getCenterX(), mine.getBounds().maxY(), mine.getCenterZ());
        Vector direction = center.toVector().subtract(head.toVector()).normalize();
        int range = plugin.getConfig().getInt("abilities.dragon_breath.breath.range", 35);

        for (int i = 0; i < range; i++) {
            Location point = head.clone().add(direction.clone().multiply(i));
            if (virtualBlockSystem.handleBreak(player, point, grid)) {
                advancePickaxe(player);
            }
        }
    }

    private void advancePickaxe(Player player) {
        EvolutionPickaxe pick = pickaxeManager.getFromHand(player);
        if (pick == null) {
            return;
        }
        int oldLevel = pick.getLevel();
        pick.addBlocksMined(1);
        int newLevel = levelManager.getLevelForBlocks(pick.getEffectiveBlocksMined());
        if (newLevel > oldLevel) {
            pick.setLevel(newLevel);
            progressDisplay.sendLevelUpTitle(player, oldLevel, newLevel);
            if (floatingTextManager != null) {
                floatingTextManager.spawnLevelUpText(player, player.getLocation(), oldLevel, newLevel);
            }
            int effectiveLevel = newLevel + (pick.getPrestige() * com.alka.mines.config.MineConfig.get().getPrestigeLevelBonus());
            mineManager.expandIfNeeded(player, effectiveLevel);
        }
        progressDisplay.update(player, pick);
        pickaxeManager.saveToHeld(player, pick);
    }
}
