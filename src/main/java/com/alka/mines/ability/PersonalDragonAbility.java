package com.alka.mines.ability;

import com.alka.mines.entity.FakeDragonEntity;
import com.alka.mines.entity.FakeEntityRegistry;
import com.alka.mines.personal.PersonalMine;
import com.alka.mines.personal.PersonalMineManager;
import com.alka.mines.pickaxe.PickaxeLevelManager;
import com.alka.mines.pickaxe.PickaxeManager;
import com.alka.mines.pickaxe.PickaxeProgressDisplay;
import com.alka.mines.trajectory.BezierTrajectory;
import com.alka.mines.trajectory.TrajectoryFactory;
import com.alka.mines.trajectory.TrajectoryTask;
import com.alka.mines.virtual.VirtualBlockSystem;
import com.alka.mines.virtual.VirtualMineGrid;
import com.alka.mines.visual.FloatingTextManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Habilidade do dragao na MINA PESSOAL: o dragao fake sobrevoa o centro da mina numa curva
 * de Bezier e o bafo quebra blocos virtuais (venda automatica + a picareta na mao upa).
 * Com cooldown por jogador e uma ativacao por vez.
 */
public class PersonalDragonAbility {

    private final Plugin plugin;
    private final PersonalMineManager mineManager;
    private final VirtualBlockSystem virtualBlockSystem;
    private final PickaxeManager pickaxeManager;
    private final PickaxeLevelManager levelManager;
    private final PickaxeProgressDisplay progressDisplay;
    private final FloatingTextManager floatingTextManager;
    private final FakeEntityRegistry registry;
    private final Map<UUID, Boolean> active = new HashMap<>();
    private final Map<UUID, Long> lastUsed = new HashMap<>();

    public PersonalDragonAbility(Plugin plugin, PersonalMineManager mineManager, VirtualBlockSystem virtualBlockSystem,
                                 PickaxeManager pickaxeManager, PickaxeLevelManager levelManager,
                                 PickaxeProgressDisplay progressDisplay, FloatingTextManager floatingTextManager,
                                 FakeEntityRegistry registry) {
        this.plugin = plugin;
        this.mineManager = mineManager;
        this.virtualBlockSystem = virtualBlockSystem;
        this.pickaxeManager = pickaxeManager;
        this.levelManager = levelManager;
        this.progressDisplay = progressDisplay;
        this.floatingTextManager = floatingTextManager;
        this.registry = registry;
    }

    /** Ativa o dragao pra um jogador, se ele tiver mina pessoal e cooldown liberado. */
    public void activate(Player player) {
        UUID uuid = player.getUniqueId();
        if (active.getOrDefault(uuid, false)) {
            player.sendMessage("§cO Dragão já está ativo!");
            return;
        }
        long cooldownMs = plugin.getConfig().getLong("abilities.dragon_breath.cooldown-seconds", 30) * 1000L;
        Long last = lastUsed.get(uuid);
        if (last != null && System.currentTimeMillis() - last < cooldownMs) {
            player.sendMessage("§cDragão em cooldown.");
            return;
        }

        PersonalMine mine = mineManager.getMine(uuid);
        VirtualMineGrid grid = mineManager.getGrid(uuid);
        if (mine == null || grid == null) {
            player.sendMessage("§cVocê precisa estar na sua mina pessoal.");
            return;
        }

        double height = plugin.getConfig().getDouble("abilities.dragon_breath.trajectory.height", 15.0);
        double radius = plugin.getConfig().getDouble("abilities.dragon_breath.trajectory.radius", 20.0);
        int duration = plugin.getConfig().getInt("abilities.dragon_breath.trajectory.duration-ticks", 200);

        Location center = new Location(org.bukkit.Bukkit.getWorld(mine.getWorldName()),
                mine.getCenterX(), mine.getBounds().maxY(), mine.getCenterZ());
        FakeDragonEntity dragon = new FakeDragonEntity(player, center.clone().add(0, height, 0));
        registry.register(player, dragon);
        dragon.spawn();

        active.put(uuid, true);

        BezierTrajectory traj = TrajectoryFactory.createCircleAround(center, radius, height);
        PersonalDragonTask breath = new PersonalDragonTask(plugin, dragon, mine, grid, virtualBlockSystem,
                pickaxeManager, levelManager, progressDisplay, floatingTextManager, mineManager);
        breath.start();

        TrajectoryTask trajTask = new TrajectoryTask(dragon, traj, duration, center.getWorld(), plugin, () -> {
            breath.cancel();
            registry.unregister(player, dragon);
            active.put(uuid, false);
            lastUsed.put(uuid, System.currentTimeMillis());
        });
        trajTask.start();
    }
}
