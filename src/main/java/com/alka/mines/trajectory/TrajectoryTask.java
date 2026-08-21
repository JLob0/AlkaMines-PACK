package com.alka.mines.trajectory;

import com.alka.mines.entity.FakeDragonEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Anima o dragao ao longo da BezierTrajectory: calculo async, envio de pacote sync.
 * Ao terminar, destroi o dragao e dispara o callback.
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
            Bukkit.getScheduler().runTask(plugin, () -> {
                dragon.destroy();
                if (bukkitTask != null) {
                    bukkitTask.cancel();
                }
                onComplete.run();
            });
            return;
        }
        if (!dragon.isSpawned() || !dragon.getTargetPlayer().isOnline()) {
            currentTick++;
            return;
        }

        double t = currentTick / (double) durationTicks;
        Vector pos = trajectory.calculate(t);
        Vector tangent = trajectory.getTangent(t).normalize();
        float[] rot = BezierTrajectory.getYawPitch(tangent);
        Location newLoc = new Location(world, pos.getX(), pos.getY(), pos.getZ(), rot[0], rot[1]);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (dragon.isSpawned() && dragon.getTargetPlayer().isOnline()) {
                dragon.teleportRelative(newLoc);
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
}
