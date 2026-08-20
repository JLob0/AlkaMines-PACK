# AlkaMines v2 — Prompts para Claude Code CLI

> **Status:** Projeto ainda não iniciado. Este documento é o ponto de partida.  
> **Base:** AlkaMines já existe em produção (github.com/JLob0/AlkaMines).  
> **Core:** AlkaCore já existe (github.com/JLob0/AlkaCore).  
> **Objetivo:** Adicionar um módulo de HABILIDADE DO DRAGÃO (client-side, trajetória matemática, cache Caffeine) ao AlkaMines existente, SEM reescrever o que já funciona.

---

## 📋 Contexto para o Claude

O AlkaMines atual é um plugin de minas prison maduro com:
- Minas públicas (reset sync via BlockFillHook, índice por chunk O(1))
- Minas particulares (PlotSquared, expansão volumétrica, schematics FAWE)
- Sistema de picareta com níveis (PlayerDataManager → banco AlkaCore)
- Listener de quebra sofisticado (soft-cancel LOWEST→HIGHEST, integração AlkaShop/AlkaDrop/mcMMO)
- GUIs via BaseGui do AlkaCore
- Persistência no banco do AlkaCore (SQLite/MySQL via HikariCP)

**O que NÃO existe e precisa ser criado:**
- Entidades falsas client-side (ProtocolLib)
- Trajetórias matemáticas (Bézier)
- Cache Caffeine de blocos de mina para lookups massivos
- Sistema de habilidades (dragão que quebra blocos em área)
- RewardBatcher extraído do MineBreakListener

**Regras de ouro:**
1. NUNCA reescreva MineBreakListener, MineManager, PrivateMineManager, BlockFillHook ou PlayerDataManager. Eles funcionam.
2. TODA a infraestrutura (DB, mensagens, GUI, scheduler) vem do AlkaCore via `AlkaAPI.get()`.
3. O dragão é uma HABILIDADE OPCIONAL dentro do ecossistema existente — não substitui nada.

---

## 🗂️ Estrutura de Pacotes Final

```
com.alka.mines
├── AlkaMines.java                    (existe — adicionar init do novo módulo)
├── ability/
│   ├── DragonBreathAbility.java      (orquestra a habilidade)
│   ├── DragonBreathTask.java         (raycast + quebra visual + partículas)
│   └── AbilitySession.java           (sessão ativa de habilidade por player)
├── entity/
│   ├── FakeDragonEntity.java         (ENDER_DRAGON client-side via ProtocolLib)
│   └── FakeEntityRegistry.java       (mapa de entidades por player + cleanup)
├── trajectory/
│   ├── BezierTrajectory.java         (curvas de Bézier cúbicas)
│   ├── TrajectoryTask.java           (runnable async + sync teleport packets)
│   └── TrajectoryFactory.java        (fábrica de trajetórias: círculo, dive)
├── packet/
│   └── PacketFactory.java            (spawn, teleport, destroy, block_change, particles)
├── cache/
│   ├── MineCache.java                (Caffeine: chunk → MineBlockData[])
│   └── MineBlockData.java            (POJO: world,x,y,z,material,rewardChance)
├── lifecycle/
│   ├── TaskManager.java              (registro e cancelamento de tasks/entidades)
│   └── MineAbilitySessionManager.java (1 sessão por player, cleanup garantido)
├── reward/
│   └── RewardBatcher.java            (buffer de drops → venda em lote via AlkaShop)
├── [tudo que já existe continua inalterado]
```

---

## 🔗 Dependências a Adicionar no `build.gradle.kts`

```kotlin
dependencies {
    // já existem: paper-api, AlkaCore, FAWE, DecentHolograms, etc.

    // NOVO: ProtocolLib para entidades client-side
    compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0")

    // NOVO: Caffeine para cache O(1) de blocos de mina
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // já existe mas confirmar: Apache Commons Math (opcional para vetores)
    // compileOnly("org.apache.commons:commons-math3:3.6.1")
}
```

**No `plugin.yml`:** adicionar `ProtocolLib` em `softdepend`.

---

## 📝 Prompts Sequenciais

Execute UM POR VEZ. Cada prompt assume que o anterior já foi aplicado.

---

### Prompt 1 — Extrair RewardBatcher do MineBreakListener

```
No projeto AlkaMines, refatore o método giveOrSellDrops() da classe 
com.alka.mines.listener.MineBreakListener para uma nova classe reutilizável 
com.alka.mines.reward.RewardBatcher.

Requisitos:
1. Construtor: RewardBatcher(Player player, Supplier<Optional<AlkaShopHook>> shopHookSupplier, Supplier<Optional<AlkaDropHook>> dropHookSupplier)
2. Método addDrop(ItemStack drop): adiciona ao buffer interno (List<ItemStack>)
3. Método flush(Location dropLocation): 
   - Para cada drop no buffer, verifica auto-venda via AlkaShopHook (mesma lógica atual de giveOrSellDrops)
   - Vende o que for vendável (isSellable + isAutoSellActive)
   - Agrupa vendas por currency em um Map<String, Double> soldTotals
   - Entrega o restante via AlkaDropHook.deliverDrops() ou world.dropItemNaturally()
   - Chama shopHook.notifyAutoSell() se houver vendas
   - Limpa o buffer
4. Modifique MineBreakListener para usar RewardBatcher em vez da lógica inline
5. NÃO altere NENHUM outro comportamento do MineBreakListener (soft-cancel, XP, mcMMO, AE, etc)
6. A classe RewardBatcher deve ser pública e reutilizável por outros pacotes
```

---

### Prompt 2 — Criar PacketFactory (Pacotes Visuais via ProtocolLib)

```
Crie com.alka.mines.packet.PacketFactory usando ProtocolLib (NMS 1.21.8 compatível):

Métodos estáticos:
1. sendSpawnLivingEntity(Player target, int entityId, UUID entityUuid, EntityType type, Location location):
   - PacketType.Play.Server.SPAWN_ENTITY
   - setEntityID, setUUID, setEntityType, setX/Y/Z, setYaw/Pitch
   - Envia via ProtocolManager

2. sendEntityMetadata(Player target, int entityId, byte flags, int dragonPhase):
   - PacketType.Play.Server.ENTITY_METADATA
   - Index 0: Byte flags (0x20 = invisible se necessário, mas dragão precisa ser visível)
   - Index 15: Integer dragonPhase (10 = voando circularmente)
   - Usar WrappedDataWatcher para construir os data watchers

3. sendEntityTeleport(Player target, int entityId, Location location):
   - PacketType.Play.Server.ENTITY_TELEPORT
   - setEntityID, setX/Y/Z, setYaw/Pitch

4. sendEntityDestroy(Player target, int... entityIds):
   - PacketType.Play.Server.ENTITY_DESTROY
   - getIntLists().write(0, List.of(entityIds)) — ou versão compatível com ProtocolLib 5.3

5. sendBlockChange(Player target, Location location, Material material):
   - PacketType.Play.Server.BLOCK_CHANGE
   - setBlockPosition (WrappedBlockData)

6. sendParticle(Player target, Location location, Particle particle, int count, double offsetX, double offsetY, double offsetZ, double speed):
   - PacketType.Play.Server.WORLD_PARTICLES
   - Usar WrappedParticle.create(particle, null)

7. sendNamedSound(Player target, Location location, Sound sound, float volume, float pitch):
   - PacketType.Play.Server.NAMED_SOUND_EFFECT

IMPORTANTE:
- Todos os métodos devem verificar player.isOnline() antes de enviar
- Usar ProtocolLibrary.getProtocolManager() para obter o ProtocolManager
- Não envolver em try-catch genérico — deixe exceções subirem para o caller logar
```

---

### Prompt 3 — Criar FakeDragonEntity e FakeEntityRegistry

```
Crie em com.alka.mines.entity:

CLASSE FakeDragonEntity:
- Campos: final UUID entityUuid; final int entityId; final Player targetPlayer; 
  Location currentLocation; volatile boolean spawned;
- entityId gerado via AtomicInteger estático (inicie em 1000000 para evitar colisão com IDs reais do servidor)
- Construtor: FakeDragonEntity(Player target, Location spawnLocation)
- Método spawn():
  1. Chama PacketFactory.sendSpawnLivingEntity(targetPlayer, entityId, entityUuid, EntityType.ENDER_DRAGON, spawnLocation)
  2. Chama PacketFactory.sendEntityMetadata(targetPlayer, entityId, (byte)0x00, 10)
  3. currentLocation = spawnLocation; spawned = true
- Método teleport(Location newLoc):
  1. Se !spawned, retorna
  2. PacketFactory.sendEntityTeleport(targetPlayer, entityId, newLoc)
  3. currentLocation = newLoc
- Método lookAt(Vector target):
  1. Calcula direction = target - currentLocation.toVector()
  2. Converte direction para yaw/pitch (usar Location#setDirection + getYaw/getPitch)
  3. teleport(currentLocation.clone().setDirection(direction))
- Método destroy():
  1. Se !spawned, retorna
  2. PacketFactory.sendEntityDestroy(targetPlayer, entityId)
  3. spawned = false
- Métodos getCurrentLocation(), isSpawned(), getTargetPlayer(), getEntityId()

CLASSE FakeEntityRegistry:
- Map<UUID, List<FakeDragonEntity>> entitiesByPlayer = new ConcurrentHashMap<>()
- Método register(Player, FakeDragonEntity): adiciona à lista do player
- Método unregister(Player, FakeDragonEntity): chama destroy() e remove da lista
- Método unregisterAll(Player): para cada entidade do player, chama destroy(); remove a entrada
- Método getEntities(Player): List<FakeDragonEntity>
- Listener interno PlayerQuitEvent: unregisterAll(player)
- O listener deve ser registrado no onEnable do AlkaMines

REGRA FUNDAMENTAL: NUNCA chamar world.spawnEntity() ou qualquer método Bukkit de spawn. 
O servidor NÃO deve saber que essa entidade existe.
```

---

### Prompt 4 — Criar BezierTrajectory e TrajectoryFactory

```
Crie em com.alka.mines.trajectory:

CLASSE BezierTrajectory:
- Campos: final List<Vector> controlPoints (4 pontos para cúbica)
- Construtor: recebe List<Vector> ou varargs Vector
- Método calculate(double t): Vector
  Fórmula cúbica exata:
  B(t) = (1-t)³·P0 + 3(1-t)²t·P1 + 3(1-t)t²·P2 + t³·P3
  Implementar com double para precisão. t deve estar em [0,1].
- Método getTangent(double t): Vector (derivada da curva)
  B'(t) = 3(1-t)²(P1-P0) + 6(1-t)t(P2-P1) + 3t²(P3-P2)
- Método getYawPitch(Vector direction): float[2] {yaw, pitch}
  Usar Location#setDirection(direction) e extrair getYaw()/getPitch()
- Método getLengthApproximation(int samples): double — soma das distâncias entre N pontos amostrados

CLASSE TrajectoryFactory:
- Método createCircleAround(Location center, double radius, double height, int points):
  Gera 4 pontos de controle em círculo ao redor do center:
  P0 = center + (radius, height, 0)
  P1 = center + (0, height, radius)
  P2 = center + (-radius, height, 0)
  P3 = center + (0, height, -radius)
  Retorna BezierTrajectory com esses pontos

- Método createDiveAttack(Location start, Location target, double height):
  Gera curva de mergulho:
  P0 = start
  P1 = start.clone().add(0, height*0.5, 0) — sobe
  P2 = target.clone().add(0, height*0.3, 0) — desce próximo ao alvo
  P3 = target
  Retorna BezierTrajectory

- Método createSwoopOverMine(Location center, double radius, double height):
  Curva em "S" que passa sobre a mina em 2 arcos
```

---

### Prompt 5 — Criar TrajectoryTask (Async Math + Sync Packets)

```
Crie com.alka.mines.trajectory.TrajectoryTask implements Runnable:

Campos:
- final FakeDragonEntity dragon
- final BezierTrajectory trajectory
- final int durationTicks (ex: 200 ticks = 10 segundos)
- final World world
- final Plugin plugin
- int currentTick = 0
- BukkitTask bukkitTask (referência para cancelar)

Construtor: recebe dragon, trajectory, durationTicks, world, plugin

Método run() (executado ASYNC via runTaskTimerAsynchronously):
1. Se currentTick >= durationTicks:
   - dragon.destroy()
   - bukkitTask.cancel()
   - Notificar MineAbilitySessionManager que a sessão terminou
   - Retornar
2. double t = currentTick / (double) durationTicks
3. Vector pos = trajectory.calculate(t)
4. Vector tangent = trajectory.getTangent(t)
5. float[] rot = trajectory.getYawPitch(tangent)
6. Location newLoc = new Location(world, pos.getX(), pos.getY(), pos.getZ(), rot[0], rot[1])
7. Bukkit.getScheduler().runTask(plugin, () -> {
      if (dragon.isSpawned() && dragon.getTargetPlayer().isOnline()) {
          dragon.teleport(newLoc);
      }
   });
   → APENAS o envio do pacote é sync. Todo o cálculo matemático é async.
8. currentTick++

Método start():
- bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this, 0L, 1L)
- Retorna a si mesmo (TrajectoryTask) para chaining

Método stop():
- Se bukkitTask != null, cancela
- dragon.destroy()

REGRA: SEMPRE verificar player.isOnline() antes de enviar pacotes. 
SEMPRE usar runTask() para o teleport sync.
```

---

### Prompt 6 — Criar MineCache (Caffeine O(1))

```
Crie em com.alka.mines.cache:

CLASSE MineBlockData (record/POJO imutável):
- final String world
- final int x, y, z
- final Material originalMaterial
- final double rewardChance
- final String rewardItemId (pode ser null)
- final boolean regenerable
- Método getLocation(Server server): Location
- Método getChunkKey(): long → ((long)(x >> 4) << 32) | (z >> 4) & 0xFFFFFFFFL
- equals() e hashCode() baseados em world + x + y + z

CLASSE CachedMineKey (record):
- String mineId
- int chunkX
- int chunkZ

CLASSE MineCache:
- Cache<CachedMineKey, MineBlockData[]> cache = Caffeine.newBuilder()
    .maximumSize(200)
    .expireAfterWrite(30, TimeUnit.MINUTES)
    .recordStats()
    .build()

- Método loadMine(String mineId, MineRegion region):
  1. Varre a região em Y de cima para baixo (y2 → y1)
  2. Para cada bloco que NÃO seja AIR:
     - Cria MineBlockData
     - Agrupa por chunk (chunkX = x >> 4, chunkZ = z >> 4)
  3. Armazena no cache: cache.put(new CachedMineKey(mineId, cx, cz), array)
  4. COMPLEXIDADE: O(volume) uma única vez no load

  IMPORTANTE: Este método deve ser chamado na thread sync (acessa World.getBlockAt).
  Ou use snapshots de chunks se quiser async.

- Método getBlocksInChunk(String mineId, int chunkX, int chunkZ): MineBlockData[]
  → cache.getIfPresent(new CachedMineKey(mineId, chunkX, chunkZ)) — O(1)

- Método findBlockAt(String mineId, Location loc): Optional<MineBlockData>
  1. Busca chunk no cache
  2. Itera o array procurando x,y,z exatos
  3. Retorna Optional

- Método invalidateMine(String mineId): 
  Itera as entradas do cache e remove as que começam com mineId

- Método getStats(): CacheStats

- Método invalidateAll(): cache.invalidateAll()

INTEGRAÇÃO:
- MineCache deve ser instanciado no onEnable do AlkaMines
- loadMine() deve ser chamado após MineManager.load() para cada mina pública
- loadMine() deve ser chamado para cada mina particular ativa
```

---

### Prompt 7 — Criar DragonBreathAbility e DragonBreathTask

```
Crie em com.alka.mines.ability:

CLASSE DragonBreathAbility:
- Campos: final Player player; final Mine mine (ou PrivateMine); final MineCache cache; 
  final FakeEntityRegistry registry; final RewardBatcher batcher; final Plugin plugin
- Construtor: recebe todos os campos acima
- Método activate():
  1. Verifica se player tem permissão "alkamines.ability.dragon"
  2. Verifica cooldown (usar PlayerMineData ou novo campo — ver Prompt 9)
  3. Cria FakeDragonEntity para o player
  4. Spawna a entidade (spawn())
  5. Gera trajetória via TrajectoryFactory.createCircleAround(mine.getCenter(), radius, height)
  6. Cria TrajectoryTask e inicia
  7. Agenda DragonBreathTask sync a cada 2 ticks durante a trajetória
  8. Registra sessão no MineAbilitySessionManager

CLASSE DragonBreathTask implements Runnable:
- Campos: final FakeDragonEntity dragon; final Mine mine; final MineCache cache; 
  final Player player; final RewardBatcher batcher; final Plugin plugin
- run() (SYNC — executa na main thread):
  1. Se !dragon.isSpawned() ou !player.isOnline(): cancela, retorna
  2. Location head = dragon.getCurrentLocation().clone().add(0, 2.5, 0) // aproximação da cabeça
  3. Vector direction = dragon.getCurrentLocation().getDirection().normalize()
  4. Raycast: para i de 0 até 25 blocos:
     - point = head.clone().add(direction.clone().multiply(i))
     - Arredonda para coordenadas de bloco
     - Busca no MineCache: cache.findBlockAt(mine.getId(), point)
     - Se encontrar MineBlockData:
       a. Envia pacote BLOCK_CHANGE para AIR (PacketFactory.sendBlockChange)
       b. Calcula drops reais: point.getBlock().getDrops(player.getInventory().getItemInMainHand())
       c. Para cada drop, batcher.addDrop(drop)
       d. Spawna partícula: PacketFactory.sendParticle(player, point, Particle.FLAME, 3, 0.2, 0.2, 0.2, 0.01)
       e. Spawna partículas secundárias para jogadores próximos (broadcast visual)
       f. Adiciona bloco a um Set<Location> de blocos quebrados (para regeneração)
  5. Se a trajetória terminou (TrajectoryTask parou), cancela este task

IMPORTANTE:
- O cache NÃO é modificado — ele é read-only após o load
- A quebra é VISUAL (pacotes) — o bloco no servidor permanece até a regeneração
- Para minas públicas, usar MineManager.getMineAt() para validar
- Para minas particulares, usar PrivateMineManager.getMineAt()
- Se o bloco não estiver na composição da mina, NÃO quebra
```

---

### Prompt 8 — Criar Lifecycle Managers (TaskManager + SessionManager)

```
Crie em com.alka.mines.lifecycle:

CLASSE TaskManager:
- Campos:
  final Set<BukkitTask> activeTasks = ConcurrentHashMap.newKeySet()
  final Set<FakeDragonEntity> activeEntities = ConcurrentHashMap.newKeySet()
- Método registerTask(BukkitTask): adiciona ao set
- Método registerEntity(FakeDragonEntity): adiciona ao set
- Método unregisterTask(BukkitTask): remove
- Método unregisterEntity(FakeDragonEntity): chama destroy() e remove
- Método cancelAll():
  1. activeTasks.forEach(BukkitTask::cancel); clear()
  2. activeEntities.forEach(FakeDragonEntity::destroy); clear()
- Método getActiveCount(): retorna activeTasks.size() + activeEntities.size()

CLASSE MineAbilitySession:
- Campos: final UUID playerId; final FakeDragonEntity dragon; final TrajectoryTask trajectory;
  final DragonBreathTask breathTask (referência ao BukkitTask); final RewardBatcher batcher;
  final Set<Location> brokenBlocks; final long startTime; final Mine mine
- Método endSession():
  1. Se trajectory != null, trajectory.stop()
  2. Se breathTask != null, breathTask.cancel()
  3. batcher.flush() — entrega recompensas pendentes
  4. dragon.destroy()
  5. Agenda regeneração dos blocos quebrados (ver Prompt 10)
  6. Remove do MineAbilitySessionManager

CLASSE MineAbilitySessionManager:
- Map<UUID, MineAbilitySession> sessions = new ConcurrentHashMap<>()
- Método startSession(Player, Mine, ...): 
  1. Se já existe sessão para o player, chama endSession() na anterior primeiro
  2. Cria nova MineAbilitySession
  3. Armazena no mapa
- Método endSession(Player): busca no mapa, chama endSession(), remove
- Método endAllSessions(): para cada entrada, chama endSession()
- Método getSession(Player): Optional<MineAbilitySession>
- Método isInSession(Player): boolean

Listener PlayerQuitEvent:
- endSession(event.getPlayer())

Listener PluginDisableEvent:
- taskManager.cancelAll()
- sessionManager.endAllSessions()

PREVENÇÃO DE VAZAMENTO:
- SEMPRE limitar duração máxima da trajetória (max 400 ticks = 20 segundos)
- SEMPRE usar try-finally em tasks para garantir cleanup
- SEMPRE verificar player.isOnline() antes de enviar pacotes
- NUNCA deixar sessão pendurada — o endSession() é o único caminho de saída
```

---

### Prompt 9 — Integrar no AlkaMines.java

```
Modifique com.alka.mines.AlkaMines para integrar o novo módulo de habilidades:

1. Adicionar campos:
   - private MineCache mineCache;
   - private TaskManager taskManager;
   - private MineAbilitySessionManager sessionManager;
   - private FakeEntityRegistry fakeEntityRegistry;

2. No onPluginEnable(), APÓS a inicialização existente (mineManager, playerDataManager, etc):
   a. this.mineCache = new MineCache();
   b. Para cada mina em mineManager.getMines(): mineCache.loadMine(mine.getId(), mine.getRegion())
   c. this.taskManager = new TaskManager();
   d. this.fakeEntityRegistry = new FakeEntityRegistry();
   e. this.sessionManager = new MineAbilitySessionManager(taskManager, fakeEntityRegistry);
   f. Registrar FakeEntityRegistry como listener (getServer().getPluginManager().registerEvents(fakeEntityRegistry, this))
   g. Registrar MineAbilitySessionManager como listener

3. Adicionar comando /mina dragao (ou /mina habilidade dragao):
   - Verifica se player está em uma mina (mineManager.getMineAt() ou privateMineManager.getMineProtectingAt())
   - Verifica permissão alkamines.ability.dragon
   - Verifica cooldown (usar playerDataManager.get(player.getUniqueId()) — adicionar campo long lastDragonUse)
   - Se passar: cria DragonBreathAbility e chama activate()
   - Envia mensagem via ChatUtil: "&6🐉 O Dragão da Mina foi invocado!"

4. No onPluginDisable(), ANTES do código existente:
   a. sessionManager.endAllSessions();
   b. taskManager.cancelAll();
   c. mineCache.invalidateAll();

5. Adicionar getter público: getMineCache(), getSessionManager() — para integrações futuras

NÃO alterar:
- MineManager.load() e save()
- MineBreakListener
- PrivateMineManager
- BlockFillHook
- Qualquer GUI existente
```

---

### Prompt 10 — Criar Regeneração de Blocos Quebrados pela Habilidade

```
Crie com.alka.mines.ability.MineAbilityRegenerator:

- Método estático scheduleRegeneration(Mine mine, Set<Location> brokenBlocks, Plugin plugin, MineCache cache):
  1. Para cada Location em brokenBlocks:
     - Obtém o MineBlockData do cache (cache.findBlockAt(mine.getId(), loc))
     - Se encontrar: loc.getBlock().setType(data.getOriginalMaterial(), false) // applyPhysics=false
     - Se não encontrar: loc.getBlock().setType(Material.STONE, false) // fallback
  2. Envia refreshChunk para os chunks afetados
  3. Limpa brokenBlocks

- Este método deve ser chamado no endSession() da MineAbilitySession
- O delay de regeneração deve ser configurável (config.yml: ability.dragon.regeneration-delay-ticks, default 100 ticks = 5s)
- Usar Bukkit.getScheduler().runTaskLater(plugin, runnable, delay)

REGRA: A regeneração é SÍNCRONA (main thread) porque modifica blocos no mundo.
O cache (MineCache) NUNCA é modificado — ele permanece read-only.
```

---

### Prompt 11 — Configuração e Mensagens

```
Adicione ao config.yml do AlkaMines (merge com o existente):

```yaml
abilities:
  dragon_breath:
    enabled: true
    permission: "alkamines.ability.dragon"
    cooldown-seconds: 60
    max-duration-ticks: 400
    trajectory:
      radius: 30.0
      height: 40.0
      duration-ticks: 200
    breath:
      range: 25
      check-interval-ticks: 2
      particles-per-check: 15
    regeneration:
      enabled: true
      delay-ticks: 100

cache:
  max-size: 200
  expire-minutes: 30

performance:
  max-concurrent-sessions: 20
  max-entities-per-player: 1
```

Adicione as mensagens ao MessagesConfig (ou ao sistema de mensagens do AlkaCore):
- "mines.ability.dragao.start": "&6🐉 &eO Dragão da Mina foi invocado!"
- "mines.ability.dragao.end": "&a✔ &aA habilidade terminou. Recompensas processadas."
- "mines.ability.dragao.cooldown": "&c⏳ Aguarde &f{time}&c para usar novamente."
- "mines.ability.dragao.no-permission": "&cVocê não tem permissão para usar esta habilidade."
- "mines.ability.dragao.not-in-mine": "&cVocê precisa estar em uma mina para usar esta habilidade."
- "mines.ability.dragao.in-session": "&cVocê já tem uma habilidade ativa."
- "mines.cache.stats": "&7Cache: &f{hits}&7 hits, &f{misses}&7 misses"
```

---

## 🧪 Prompt 12 — Comando de Debug/Teste

```
Crie o comando /mina debug (subcomando do /mina existente, apenas para OPs):

Subcomandos:
- /mina debug ability — mostra:
  - Sessões ativas (player, mine, tempo restante)
  - Entidades fake ativas (player, entityId, spawned)
  - Tasks pendentes no TaskManager
- /mina debug cache — mostra estatísticas do Caffeine (hits, misses, size)
- /mina debug stress <jogadores> — simula N jogadores usando a habilidade:
  - Spawna N dragões falsos
  - Inicia trajetórias
  - Coleta métricas por 30 segundos
  - Reporta: TPS, memória heap, pacotes/segundo, tempo médio de cálculo
  - Auto-cancela após 30s

Implementar como subcomando no PlayerCommands existente.
```

---

## 🏗️ Diagrama de Arquitetura do Módulo Novo

```
┌─────────────────────────────────────────────────────────────────┐
│                         JOGADOR                                  │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────────┐ │
│  │   Cliente   │◄───│ ProtocolLib  │◄───│  FakeDragonEntity   │ │
│  │  (Dragão)   │    │   Pacotes    │    │  (Client-Side Only) │ │
│  └─────────────┘    └──────────────┘    └─────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      SERVIDOR (Paper)                            │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────────┐│
│  │ BezierTrajectory│───►│ TrajectoryTask│───►│ BukkitScheduler   ││
│  │ (Math Async)    │    │ (Lifecycle)   │    │ (Sync Packets)    ││
│  └─────────────┘    └──────────────┘    └─────────────────────┘│
│         │                                                        │
│         ▼                                                        │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────────┐│
│  │  MineCache  │───►│   Caffeine   │───►│  MineBlockData[]    ││
│  │  (O(1))     │    │   Cache      │    │  (Read-Only)        ││
│  └─────────────┘    └──────────────┘    └─────────────────────┘│
│         │                                                        │
│         ▼                                                        │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────────┐│
│  │DragonBreath │───►│RewardBatcher │───►│ AlkaShopHook        ││
│  │Ability      │    │ (Lote)       │    │ (Auto-Venda)        ││
│  └─────────────┘    └──────────────┘    └─────────────────────┘│
│         │                                                        │
│         ▼                                                        │
│  ┌─────────────────┐    ┌──────────────┐                        │
│  │MineAbilityRegen-│───►│ Mundo Físico │ (setBlockType sync)   │
│  │erator           │    │ (Blocos)     │                        │
│  └─────────────────┘    └──────────────┘                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## ✅ Checklist de Execução

- [ ] Prompt 1: RewardBatcher extraído do MineBreakListener
- [ ] Prompt 2: PacketFactory criado com ProtocolLib
- [ ] Prompt 3: FakeDragonEntity + FakeEntityRegistry
- [ ] Prompt 4: BezierTrajectory + TrajectoryFactory
- [ ] Prompt 5: TrajectoryTask (async math + sync packets)
- [ ] Prompt 6: MineCache com Caffeine
- [ ] Prompt 7: DragonBreathAbility + DragonBreathTask
- [ ] Prompt 8: TaskManager + MineAbilitySessionManager
- [ ] Prompt 9: Integração no AlkaMines.java
- [ ] Prompt 10: Regeneração de blocos quebrados
- [ ] Prompt 11: Configuração e mensagens
- [ ] Prompt 12: Comando de debug
- [ ] Teste: 1 player, 1 mina, habilidade ativa
- [ ] Teste: 2+ players simultâneos
- [ ] Teste: Regeneração funciona
- [ ] Teste: Recompensas chegam corretamente
- [ ] Teste: Player quit durante habilidade = cleanup correto
- [ ] Teste: Plugin reload/disable = nenhuma entidade fantasma

---

## 🚨 Anti-Padrões a EVITAR

1. **NUNCA** chamar `world.spawnEntity()` — o dragão é 100% client-side
2. **NUNCA** fazer I/O de banco ou acesso a `World.getBlockAt()` fora da main thread
3. **NUNCA** modificar o MineCache após o load — ele é read-only
4. **NUNCA** deixar uma sessão sem endSession() — sempre há caminho de cleanup
5. **NUNCA** enviar pacotes para player offline — sempre verificar `isOnline()`
6. **NUNCA** usar FAWE/WorldEdit para a habilidade — é puramente visual via pacotes
7. **NUNCA** bloquear a main thread com cálculos pesados — matemática vai para async

---

*Documento gerado para início de implementação do módulo de habilidade do dragão no AlkaMines.*
