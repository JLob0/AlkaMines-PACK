package com.alka.mines.lifecycle;

import com.alka.mines.entity.FakeDragonEntity;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registra tasks agendadas e entidades fake ativas da habilidade, pra garantir cleanup
 * total (cancelAll) no disable do plugin - nenhuma task ou entidade fantasma sobra.
 */
public class TaskManager {

    private final Set<BukkitTask> activeTasks = ConcurrentHashMap.newKeySet();
    private final Set<FakeDragonEntity> activeEntities = ConcurrentHashMap.newKeySet();

    public void registerTask(BukkitTask task) {
        if (task != null) {
            activeTasks.add(task);
        }
    }

    public void unregisterTask(BukkitTask task) {
        if (task != null) {
            activeTasks.remove(task);
        }
    }

    public void registerEntity(FakeDragonEntity entity) {
        if (entity != null) {
            activeEntities.add(entity);
        }
    }

    public void unregisterEntity(FakeDragonEntity entity) {
        if (entity != null) {
            entity.destroy();
            activeEntities.remove(entity);
        }
    }

    public void cancelAll() {
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
        activeEntities.forEach(FakeDragonEntity::destroy);
        activeEntities.clear();
    }

    public int getActiveCount() {
        return activeTasks.size() + activeEntities.size();
    }

    public Set<BukkitTask> getActiveTasks() {
        return activeTasks;
    }

    public Set<FakeDragonEntity> getActiveEntities() {
        return activeEntities;
    }
}
