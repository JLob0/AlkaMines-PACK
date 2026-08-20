package com.alka.mines.ability;

import com.alka.mines.cache.MineCache;
import com.alka.mines.entity.FakeDragonEntity;
import com.alka.mines.entity.FakeEntityRegistry;
import com.alka.mines.lifecycle.MineAbilitySession;
import com.alka.mines.lifecycle.MineAbilitySessionManager;
import com.alka.mines.lifecycle.TaskManager;
import com.alka.mines.model.Mine;
import com.alka.mines.reward.RewardBatcher;
import com.alka.mines.trajectory.BezierTrajectory;
import com.alka.mines.trajectory.TrajectoryFactory;
import com.alka.mines.trajectory.TrajectoryTask;
import com.alka.mines.util.ChatUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Orquestra a habilidade do dragao pra um jogador: spawna o dragao fake, anima numa
 * trajetoria circular sobre a mina e dispara o DragonBreathTask que quebra blocos em
 * area (visual) acumulando drops. A sessao e registrada no MineAbilitySessionManager,
 * que e quem garante cleanup no fim.
 */
public class DragonBreathAbility {

    private final Player player;
    private final Mine mine;
    private final MineCache cache;
    private final FakeEntityRegistry registry;
    private final RewardBatcher batcher;
    private final Plugin plugin;
    private final MineAbilitySessionManager sessionManager;
    private final TaskManager taskManager;

    public DragonBreathAbility(Player player, Mine mine, MineCache cache, FakeEntityRegistry registry,
                               RewardBatcher batcher, Plugin plugin, MineAbilitySessionManager sessionManager,
                               TaskManager taskManager) {
        this.player = player;
        this.mine = mine;
        this.cache = cache;
        this.registry = registry;
        this.batcher = batcher;
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.taskManager = taskManager;
    }

    /** Inicia a habilidade. Retorna false se o jogador ja esta em sessao. */
    public boolean activate() {
        if (sessionManager.isInSession(player)) {
            return false;
        }

        Location center = mine.getRegion().getCenter();
        double height = plugin.getConfig().getDouble("abilities.dragon_breath.trajectory.height", 40.0);
        FakeDragonEntity dragon = new FakeDragonEntity(player, center.clone().add(0, height, 0));
        registry.register(player, dragon);
        taskManager.registerEntity(dragon);
        dragon.spawn();

        int duration = plugin.getConfig().getInt("abilities.dragon_breath.trajectory.duration-ticks", 200);
        double radius = plugin.getConfig().getDouble("abilities.dragon_breath.trajectory.radius", 30.0);
        BezierTrajectory trajectory = TrajectoryFactory.createCircleAround(center, radius, height, 4);

        // cria a sessao primeiro (encerra sessao anterior, se houver)
        MineAbilitySession session = sessionManager.startSession(player, mine, dragon, batcher, plugin, cache);

        DragonBreathTask breath = new DragonBreathTask(dragon, mine, cache, player, batcher, plugin,
                session.getBrokenBlocks());
        breath.start();
        taskManager.registerTask(breath.getTask());
        session.setBreathTask(breath.getTask());

        // quando a trajetoria termina, encerra a sessao (cancela breath, flush, regeneracao)
        TrajectoryTask trajectoryTask = new TrajectoryTask(dragon, trajectory, duration, center.getWorld(), plugin,
                () -> {
                    ChatUtil.sendKey(player, "mines.ability.dragao.end");
                    sessionManager.endSession(player);
                });
        trajectoryTask.start();
        taskManager.registerTask(trajectoryTask.getBukkitTask());
        session.setTrajectory(trajectoryTask);

        return true;
    }
}
