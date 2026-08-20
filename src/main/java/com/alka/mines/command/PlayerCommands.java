package com.alka.mines.command;

import com.alka.mines.ability.DragonBreathAbility;
import com.alka.mines.cache.MineCache;
import com.alka.mines.entity.FakeDragonEntity;
import com.alka.mines.entity.FakeEntityRegistry;
import com.alka.mines.gui.MineListMenu;
import com.alka.mines.gui.PrivateMineGui;
import com.alka.mines.gui.RankingGui;
import com.alka.mines.hook.AlkaDropHook;
import com.alka.mines.hook.AlkaShopHook;
import com.alka.mines.lifecycle.MineAbilitySessionManager;
import com.alka.mines.lifecycle.TaskManager;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.manager.PrivateMineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineRegion;
import com.alka.mines.model.MineTemplate;
import com.alka.mines.model.PrivateMine;
import com.alka.mines.reward.RewardBatcher;
import com.alka.mines.trajectory.TrajectoryFactory;
import com.alka.mines.trajectory.TrajectoryTask;
import com.alka.mines.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PlayerCommands implements CommandExecutor, TabCompleter {

    private final MineManager mineManager;
    private final PlayerDataManager playerDataManager;
    private final PrivateMineManager privateMineManager;
    private final JavaPlugin plugin;
    private final MineListMenu listMenu;
    private final Map<UUID, Long> deleteConfirm = new HashMap<>();
    private MineCache mineCache;
    private MineAbilitySessionManager sessionManager;
    private TaskManager taskManager;
    private FakeEntityRegistry fakeEntityRegistry;
    private Supplier<Optional<AlkaShopHook>> shopHookSupplier;
    private Supplier<Optional<AlkaDropHook>> dropHookSupplier;

    public PlayerCommands(JavaPlugin plugin, MineManager mineManager, PlayerDataManager playerDataManager,
                          PrivateMineManager privateMineManager) {
        this.plugin = plugin;
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
        this.privateMineManager = privateMineManager;
        this.listMenu = new MineListMenu(plugin, mineManager, playerDataManager);
    }

    /** Anexa o modulo de habilidades (dragao) apos a inicializacao no onEnable. */
    public void attachAbilityModule(MineCache mineCache, MineAbilitySessionManager sessionManager,
                                    TaskManager taskManager, FakeEntityRegistry fakeEntityRegistry,
                                    Supplier<Optional<AlkaShopHook>> shopHookSupplier,
                                    Supplier<Optional<AlkaDropHook>> dropHookSupplier) {
        this.mineCache = mineCache;
        this.sessionManager = sessionManager;
        this.taskManager = taskManager;
        this.fakeEntityRegistry = fakeEntityRegistry;
        this.shopHookSupplier = shopHookSupplier;
        this.dropHookSupplier = dropHookSupplier;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            ChatUtil.sendKey(sender, "generic.player-only");
            return true;
        }

        if (args.length == 0) {
            // /minas (alias) sempre abre a lista - /mina (nome principal) teleporta
            // direto se so tiver 1 mina disponivel. Os dois compartilham o mesmo
            // registro de comando no plugin.yml, entao "label" e o unico jeito de
            // diferenciar qual das duas palavras o jogador digitou.
            if (label.equalsIgnoreCase("minas")) {
                listMenu.open(player);
            } else {
                handleDefault(player);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "ir" -> handleIr(player, args);
            case "sair" -> handleSair(player);
            case "lista" -> listMenu.open(player);
            case "ranking", "top" -> sendRanking(player);
            case "particular" -> handleParticular(player, args);
            case "dragao", "habilidade" -> handleDragonAbility(player);
            case "debug" -> handleDebug(player, args);
            default -> ChatUtil.sendKey(player, "error.usage.mina");
        }
        return true;
    }

    private void sendRanking(Player player) {
        if (playerDataManager.getTopBlocksBroken(1).isEmpty()) {
            ChatUtil.sendKey(player, "ranking.empty");
            return;
        }
        new RankingGui(plugin, player, playerDataManager).open();
    }

    private void handleDefault(Player player) {
        Collection<Mine> mines = mineManager.getMines();
        if (mines.size() == 1) {
            teleportTo(player, mines.iterator().next());
            return;
        }
        listMenu.open(player);
    }

    private void handleIr(Player player, String[] args) {
        if (!player.hasPermission("alkaminas.ir")) {
            ChatUtil.sendKey(player, "generic.no-permission");
            return;
        }

        Mine target;
        if (args.length >= 2) {
            target = mineManager.getMine(args[1].toLowerCase()).orElse(null);
            if (target == null) {
                ChatUtil.sendKey(player, "error.mine-not-found", Map.of("mine", args[1]));
                return;
            }
        } else {
            target = bestMineFor(player).orElse(null);
            if (target == null) {
                ChatUtil.sendKey(player, "mine.no-mine-available");
                return;
            }
        }

        teleportTo(player, target);
    }

    private void handleSair(Player player) {
        Location exit = playerDataManager.getExitLocation();
        if (exit == null) {
            World world = Bukkit.getWorlds().get(0);
            exit = world.getSpawnLocation();
        }
        player.teleport(exit);
        playerDataManager.get(player.getUniqueId()).setCurrentMineId(null);
        ChatUtil.sendKey(player, "mine.left");
    }

    private void teleportTo(Player player, Mine mine) {
        if (!canAccess(player, mine)) {
            if (mine.getSettings().hasPermission() && !player.hasPermission(mine.getSettings().getPermission())) {
                ChatUtil.sendKey(player, "mine.access.permission-denied",
                        Map.of("permission", mine.getSettings().getPermission(), "mine", mine.getId()));
                return;
            }
            ChatUtil.sendKey(player, "mine.access.level-denied", Map.of(
                    "level", String.valueOf(mine.getSettings().getMinPickaxeLevel()), "mine", mine.getId()));
            return;
        }
        player.teleport(mine.getSpawn());
        // forca o placeholder/tracker a reconhecer a mina mesmo que o spawn configurado
        // fique fora da regiao exata do WorldEdit (ex: uma plataforma de entrada) - senao
        // so o proximo PlayerMoveEvent detectaria isso, com um atraso perceptivel.
        playerDataManager.get(player.getUniqueId()).setCurrentMineId(mine.getId());
        ChatUtil.sendKey(player, "mine.teleported", Map.of("mine", mine.getId()));
    }

    private Optional<Mine> bestMineFor(Player player) {
        return mineManager.getMines().stream()
                .filter(mine -> canAccess(player, mine))
                .min(Comparator.comparingInt(mine -> mine.getSettings().getMinPickaxeLevel()));
    }

    private boolean canAccess(Player player, Mine mine) {
        if (mine.getSettings().hasPermission() && !player.hasPermission(mine.getSettings().getPermission())) {
            return false;
        }
        int required = mine.getSettings().getMinPickaxeLevel();
        if (required <= 0) {
            return true;
        }
        return playerDataManager.get(player.getUniqueId()).getPickaxeLevel() >= required;
    }

    /** /mina dragao - invoca a habilidade do dragao na mina atual. */
    private void handleDragonAbility(Player player) {
        if (mineCache == null || sessionManager == null) {
            ChatUtil.send(player, "<red>Modulo de habilidades ainda nao foi inicializado.");
            return;
        }
        if (!player.hasPermission("alkamines.ability.dragon")) {
            ChatUtil.sendKey(player, "mines.ability.dragao.no-permission");
            return;
        }
        if (sessionManager.isInSession(player)) {
            ChatUtil.sendKey(player, "mines.ability.dragao.in-session");
            return;
        }

        // mina publica primeiro; senao, particular (construindo uma visao de Mine).
        Mine mine = mineManager.getMineAt(player.getLocation()).orElse(null);
        if (mine == null) {
            PrivateMine pm = privateMineManager.getMineProtectingAt(player.getLocation()).orElse(null);
            if (pm != null) {
                MineRegion region = new MineRegion(pm.getWorldName(), pm.getMinX(), pm.getMinY(), pm.getMinZ(),
                        pm.getMaxX(), pm.getMaxY(), pm.getMaxZ());
                mine = new Mine("private:" + pm.getOwner(), "Mina particular", region);
            }
        }
        if (mine == null) {
            ChatUtil.sendKey(player, "mines.ability.dragao.not-in-mine");
            return;
        }

        // cooldown (em memoria) - segundos vindos do config.
        long cooldownMs = plugin.getConfig().getLong("abilities.dragon_breath.cooldown-seconds", 60) * 1000L;
        long remaining = sessionManager.getRemainingCooldownMs(player.getUniqueId(), cooldownMs);
        if (remaining > 0) {
            ChatUtil.sendKey(player, "mines.ability.dragao.cooldown",
                    Map.of("time", String.valueOf((remaining + 999) / 1000)));
            return;
        }

        // garante o cache da mina carregado (chunk do centro como sonda).
        Location center = mine.getRegion().getCenter();
        if (center.getWorld() != null
                && mineCache.getBlocksInChunk(mine.getId(), center.getBlockX() >> 4, center.getBlockZ() >> 4) == null) {
            mineCache.loadMine(mine.getId(), mine.getRegion(), mine.getComposition());
        }

        RewardBatcher batcher = new RewardBatcher(player, shopHookSupplier, dropHookSupplier);
        DragonBreathAbility ability = new DragonBreathAbility(player, mine, mineCache, fakeEntityRegistry,
                batcher, plugin, sessionManager, taskManager);
        if (!ability.activate()) {
            ChatUtil.sendKey(player, "mines.ability.dragao.in-session");
            return;
        }
        sessionManager.markUsed(player.getUniqueId());
        ChatUtil.sendKey(player, "mines.ability.dragao.start");
    }

    /** /mina debug <ability|cache|stress> - diagnostico do modulo de habilidades (OP). */
    private void handleDebug(Player player, String[] args) {
        if (!player.isOp()) {
            ChatUtil.sendKey(player, "generic.no-permission");
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(player, "<red>Uso: <gray>/mina debug <ability|cache|stress [jogadores]>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "ability" -> debugAbility(player);
            case "cache" -> debugCache(player);
            case "stress" -> debugStress(player, args);
            default -> ChatUtil.send(player, "<red>Subcomando desconhecido. Use: <gray>ability|cache|stress");
        }
    }

    private void debugAbility(Player player) {
        ChatUtil.send(player, "<gold>=== Sessões de habilidade ativas ===");
        int sessions = 0;
        for (var session : sessionManager.getAllSessions()) {
            sessions++;
            String who = Bukkit.getOfflinePlayer(session.getPlayerId()).getName();
            ChatUtil.send(player, String.format(
                    " <dark_gray>- <white>%s <gray>| mina: <white>%s <gray>| tempo: <white>%ds",
                    who != null ? who : session.getPlayerId(), session.getMine().getId(),
                    session.getElapsedMs() / 1000));
        }
        if (sessions == 0) {
            ChatUtil.send(player, " <gray>Nenhuma sessão ativa.");
        }

        ChatUtil.send(player, "<gold>=== Entidades fake ===");
        int entities = 0;
        for (var entity : taskManager.getActiveEntities()) {
            entities++;
            ChatUtil.send(player, String.format(
                    " <dark_gray>- <white>%s <gray>| entityId: <white>%d <gray>| spawned: <white>%s",
                    entity.getTargetPlayer().getName(), entity.getEntityId(), entity.isSpawned()));
        }
        if (entities == 0) {
            ChatUtil.send(player, " <gray>Nenhuma entidade fake.");
        }
        ChatUtil.send(player, String.format("<gold>Tasks pendentes: <white>%d <gold>| Total ativo: <white>%d",
                taskManager.getActiveTasks().size(), taskManager.getActiveCount()));
    }

    private void debugCache(Player player) {
        if (mineCache == null) {
            ChatUtil.send(player, "<red>Cache não inicializado.");
            return;
        }
        var stats = mineCache.getStats();
        ChatUtil.send(player, "<gold>=== Cache (Caffeine) ===");
        ChatUtil.send(player, String.format(
                " <gray>Hits: <white>%d <gray>| Misses: <white>%d <gray>| Entradas: <white>%d",
                stats.hitCount(), stats.missCount(), mineCache.getSize()));
        ChatUtil.sendKey(player, "mines.ability.cache.stats",
                Map.of("hits", String.valueOf(stats.hitCount()), "misses", String.valueOf(stats.missCount())));
    }

    /** Simula N dragoes sobre a mina atual por 30s e reporta metricas. */
    private void debugStress(Player player, String[] args) {
        if (mineCache == null || taskManager == null || fakeEntityRegistry == null) {
            ChatUtil.send(player, "<red>Modulo de habilidades não inicializado.");
            return;
        }
        int n = 5;
        if (args.length >= 3) {
            try {
                n = Math.max(1, Math.min(20, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        Mine mine = mineManager.getMineAt(player.getLocation()).orElseGet(() ->
                mineManager.getMines().stream().findFirst().orElse(null));
        if (mine == null) {
            ChatUtil.send(player, "<red>Você precisa estar em uma mina para o stress test.");
            return;
        }
        Location center = mine.getRegion().getCenter();
        int duration = plugin.getConfig().getInt("abilities.dragon_breath.trajectory.duration-ticks", 200);
        double radius = plugin.getConfig().getDouble("abilities.dragon_breath.trajectory.radius", 30.0);
        double height = plugin.getConfig().getDouble("abilities.dragon_breath.trajectory.height", 40.0);

        for (int i = 0; i < n; i++) {
            var dragon = new FakeDragonEntity(player, center.clone().add(0, height, i));
            fakeEntityRegistry.register(player, dragon);
            taskManager.registerEntity(dragon);
            dragon.spawn();
            var traj = new TrajectoryTask(dragon,
                    TrajectoryFactory.createCircleAround(center, radius, height + i * 0.5, 4),
                    duration, center.getWorld(), plugin,
                    () -> taskManager.unregisterEntity(dragon));
            traj.start();
            taskManager.registerTask(traj.getBukkitTask());
        }

        ChatUtil.send(player, String.format("<green>Stress test: <white>%d <green>dragões sobre '<white>%s<green>'. Métricas em ~30s.",
                n, mine.getId()));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            double[] tps = Bukkit.getTPS();
            long maxMem = Runtime.getRuntime().maxMemory() / 1024 / 1024;
            long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
            ChatUtil.send(player, "<gold>=== Relatório do stress test ===");
            ChatUtil.send(player, String.format(" <gray>TPS (1m/5m/15m): <white>%.1f / %.1f / %.1f", tps[0], tps[1], tps[2]));
            ChatUtil.send(player, String.format(" <gray>Heap: <white>%dMB <gray>/ <white>%dMB", usedMem, maxMem));
            ChatUtil.send(player, String.format(" <gray>Itens ativos: <white>%d", taskManager.getActiveCount()));
        }, duration + 20L);
    }

    /** /mina particular info|deletar - gerencia a mina particular na plot atual. */
    private void handleParticular(Player player, String[] args) {
        if (args.length < 2) {
            ChatUtil.sendKey(player, "error.usage.particular");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "menu" -> new PrivateMineGui(plugin, player, privateMineManager).open();
            case "home" -> {
                PrivateMine mine = privateMineManager.getMineProtectingAt(player.getLocation())
                        .filter(m -> m.getOwner().equals(player.getUniqueId()))
                        .orElseGet(() -> privateMineManager.getForPlayer(player.getUniqueId()).stream()
                                .findFirst().orElse(null));
                if (mine == null) {
                    ChatUtil.sendKey(player, "private-mine.not-found");
                    return;
                }
                player.teleport(privateMineManager.getHomeLocation(mine));
                ChatUtil.sendKey(player, "private-mine.teleported-home");
            }
            case "compartilhar" -> {
                if (args.length < 3) {
                    ChatUtil.sendKey(player, "error.usage.share");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null || !target.isOnline()) {
                    ChatUtil.sendKey(player, "generic.player-offline", Map.of("player", args[2]));
                    return;
                }
                String error = privateMineManager.addMember(player, target);
                if (error != null) {
                    ChatUtil.send(player, error);
                } else {
                    ChatUtil.sendKey(player, "private-mine.member-added", Map.of("target", target.getName()));
                    ChatUtil.sendKey(target, "private-mine.member-added-target", Map.of("owner", player.getName()));
                }
            }
            case "info" -> {
                PrivateMine mine = privateMineManager.getMineProtectingAt(player.getLocation())
                        .orElseGet(() -> privateMineManager.getForPlayer(player.getUniqueId()).stream()
                                .findFirst().orElse(null));
                if (mine == null) {
                    ChatUtil.sendKey(player, "private-mine.not-found");
                    return;
                }
                Optional<MineTemplate> template = privateMineManager.getTemplate(mine.getTemplateId());
                int interval = mine.getResetIntervalMinutes() > 0 ? mine.getResetIntervalMinutes()
                        : template.map(MineTemplate::getResetIntervalMinutes).orElse(0);
                long remainingMs = Math.max(0, interval * 60_000L - (System.currentTimeMillis() - mine.getLastReset()));
                long remainingSec = remainingMs / 1000;
                String timeReal = String.format("%02d:%02d", remainingSec / 60, remainingSec % 60);
                String ownerName = Bukkit.getOfflinePlayer(mine.getOwner()).getName();
                long volume = mine.volume();
                double pct = volume > 0
                        ? Math.round((mine.getBlocksRemaining() / (double) volume) * 1000.0) / 10.0 : 0.0;
                String founded = new SimpleDateFormat("dd/MM/yyyy").format(new Date(mine.getCreatedAt()));
                String expires = template.isPresent() && template.get().getExpiresInDays() > 0
                        ? new SimpleDateFormat("dd/MM/yyyy").format(new Date(mine.getCreatedAt()
                        + template.get().getExpiresInDays() * 86_400_000L))
                        : "<green>Eterna";
                ChatUtil.sendKey(player, "private-mine.info.header");
                ChatUtil.sendKey(player, "private-mine.info.type", Map.of("type",
                        template.isPresent() ? template.get().getDisplayName() : mine.getTemplateId()));
                ChatUtil.sendKey(player, "private-mine.info.owner", Map.of("owner",
                        ownerName != null ? ownerName : mine.getOwner().toString()));
                ChatUtil.sendKey(player, "private-mine.info.blocks", Map.of(
                        "blocks", String.format(Locale.US, "%,d", mine.getBlocksRemaining()),
                        "percentage", String.valueOf(pct)));
                ChatUtil.sendKey(player, "private-mine.info.next-reset",
                        Map.of("interval", String.valueOf(interval), "time", timeReal));
                ChatUtil.sendKey(player, "private-mine.info.rarity",
                        Map.of("rarity", template.map(MineTemplate::getRarity).orElse("★")));
                ChatUtil.sendKey(player, "private-mine.info.founded", Map.of("date", founded));
                ChatUtil.sendKey(player, "private-mine.info.expires", Map.of("date", expires));
            }
            case "expandir" -> {
                if (args.length < 3) {
                    ChatUtil.sendKey(player, "error.usage.expand");
                    return;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    ChatUtil.sendKey(player, "generic.invalid-number");
                    return;
                }
                String error = privateMineManager.expand(player, amount);
                if (error != null) {
                    ChatUtil.send(player, error);
                } else {
                    ChatUtil.sendKey(player, "private-mine.expanded", Map.of("amount", String.valueOf(amount)));
                }
            }
            case "deletar" -> {
                Optional<PrivateMine> mine = privateMineManager.getMineProtectingAt(player.getLocation());
                if (mine.isEmpty() || !mine.get().getOwner().equals(player.getUniqueId())) {
                    ChatUtil.sendKey(player, "private-mine.not-here");
                    return;
                }
                Long expiry = deleteConfirm.get(player.getUniqueId());
                if (expiry == null || expiry < System.currentTimeMillis()) {
                    deleteConfirm.put(player.getUniqueId(), System.currentTimeMillis() + 5000);
                    ChatUtil.sendKey(player, "private-mine.delete-confirm");
                    return;
                }
                deleteConfirm.remove(player.getUniqueId());
                if (privateMineManager.deleteAt(player, player.getLocation())) {
                    ChatUtil.sendKey(player, "private-mine.deleted");
                } else {
                    ChatUtil.sendKey(player, "private-mine.delete-failed");
                }
            }
            default -> ChatUtil.sendKey(player, "error.usage.particular");
        }
    }

    private String format(long seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("ir", "sair", "lista", "ranking", "dragao", "debug").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return List.of("ability", "cache", "stress").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ir")) {
            return mineManager.getMines().stream().map(Mine::getId).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("particular")) {
            return List.of("menu", "info", "deletar", "expandir", "home", "compartilhar").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
