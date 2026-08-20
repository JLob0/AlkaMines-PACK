package com.alka.mines.trajectory;

import com.alka.mines.entity.FakeDragonEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Anima o dragao ao longo de uma BezierTrajectory. Todo o calculo matematico roda em
 * thread ASYNC (via runTaskTimerAsynchronously); apenas o envio do pacote de teleporte
 * e feito sync (via runTask), porque ProtocolLib/PacketFactory exigem a main thread.
 *
 * Ao terminar a trajetoria, destroi o dragao e dispara o callback de conclusao (usado
 * pela habilidade pra encerrar a sessao no MineAbilitySessionManager).
 */
public class TrajectoryTask implements Runnable {

    private final FakeDragonEntity dragon;
    private final BezierTrajectory trajectory;
    private final int durationTicks;
    private final World world;
    private final Plugin plugin;
    private final Runnable onComplete;
    private int currentTick = 0;
    private BukkitTask bukkitTask;

    public TrajectoryTask(FakeDragonEntity dragon, BezierTrajectory trajectory, int durationTicks,
                          World world, Plugin plugin, Runnable onComplete) {
        this.dragon = dragon;
        this.trajectory = trajectory;
        this.durationTicks = durationTicks;
        this.world = world;
        this.plugin = plugin;
        this.onComplete = onComplete == null ? () -> {
        } : onComplete;
    }

    @Override
    public void run() {
        if (currentTick >= durationTicks) {
            dragon.destroy();
            if (bukkitTask != null) {
                bukkitTask.cancel();
            }
            onComplete.run();
            return;
        }

        double t = currentTick / (double) durationTicks;
        Vector pos = trajectory.calculate(t);
        Vector tangent = trajectory.getTangent(t);
        float[] rot = BezierTrajectory.getYawPitch(tangent);
        Location newLoc = new Location(world, pos.getX(), pos.getY(), pos.getZ(), rot[0], rot[1]);

        // SO o envio do pacote e sync; todo o calculo acima foi async.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (dragon.isSpawned() && dragon.getTargetPlayer().isOnline()) {
                dragon.teleport(newLoc);
            }
        });

        currentTick++;
    }

    public TrajectoryTask start() {
        bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this, 0L, 1L);
        return this;
    }

    public void stop() {
        if (bukkitTask != null) {
            bukkitTask.cancel();
        }
        dragon.destroy();
    }

    public BukkitTask getBukkitTask() {
        return bukkitTask;
    }
}
