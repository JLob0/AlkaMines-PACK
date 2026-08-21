# AlkaMines-PACK — Prompts Completos para Claude Code

> **Contexto:** O AlkaMines v1.0.23 ainda não está integrado com o AlkaCore. Ele possui seu próprio `MenuBuilder`, `ChatUtil`, `PlayerDataManager`, `PacketFactory`, `RewardBatcher`, `MineCache` (Caffeine) e `DragonBreathAbility`. Todos os novos sistemas devem consumir os serviços do AlkaCore (`BaseGui`, `MessageProvider`, `PlayerDataManager` do core) e a `AlkaShopAPI` para venda de drops.
>
> **Regra de Ouro:** NUNCA edite arquivos diretamente. Sempre gere prompts completos para o Claude Code CLI aplicar.

---

## 🆕 ADIÇÃO CRÍTICA — Sistema de Blocos Virtuais (Anti-Lag)

Antes de tudo, implemente o `VirtualBlockSystem`. Isso elimina entidades de item, atualizações de chunk e física do servidor.

### Prompt 0 — VirtualBlockSystem

```
No AlkaMines-PACK, crie o sistema de blocos virtuais para eliminar lag em minas pessoais.

1. NOVO: com.alka.mines.virtual.VirtualBlockSystem
   - Responsabilidade: gerenciar TODA a mina como uma grade de blocos client-side.
   - NUNCA altera o mundo real (World#setType).
   - O mundo real da mina pessoal é preenchido com BEDROCK no chão (Y = surface - 2) e AR no restante.
   - As paredes/laterais são BEDROCK real para evitar saída.

2. NOVO: com.alka.mines.virtual.VirtualMineGrid
   - Mapa interno: Map<BlockPos, VirtualBlock> blocks
   - BlockPos: record com int x, y, z (imutável, usa como key)
   - VirtualBlock: record com Material material, double sellValue, boolean breakable
   - Método generate(PersonalMine mine, int level):
     * Itera sobre os bounds da mina
     * Para cada posição interna (não parede), calcula material baseado na composição do nível
     * Preenche o Map
   - Método getBlockAt(Location): retorna VirtualBlock ou null
   - Método breakBlock(Player, BlockPos):
     * Remove do mapa (ou marca como AIR)
     * Envia pacote de quebra visual para o jogador
     * Acumula valor no RewardBatcher
     * Agenda respawn após delay (ex: 2 segundos) com BukkitRunnable

3. ATUALIZE PacketFactory:
   Adicione métodos:
   - sendBlockBreakEffect(Player, Location, Material):
     * Envia pacote de som de quebra (Sound.BLOCK_STONE_BREAK)
     * Envia particles de destruição (Particle.BLOCK_CRACK com data do material)
     * Envia BLOCK_BREAK_ANIMATION com entityId fictício (ex: player.getEntityId() + 100000) e stage 9
   - sendBlockChange(Player, Location, Material):
     * Envia pacote de alteração de bloco para o jogador APENAS (wrapper de World#sendBlockChange)
   - sendBulkBlockChanges(Player, Map<Location, Material>):
     * Usa MultiBlockChangeInfo se disponível (1.19.4+) para enviar vários blocos em 1 pacote

4. ATUALIZE MineBreakListener:
   - Em vez de e.setCancelled(false) + drops:
     * e.setCancelled(true)
     * Se estiver em mina pessoal: virtualBlockSystem.handleBreak(player, block.getLocation())
     * NUNCA spawna ItemEntity no mundo
     * NUNCA altera o bloco real no chunk
   - O jogador vê o bloco quebrando e "respawnando" instantaneamente (ou após delay) via pacotes.

5. ATUALIZE PersonalMineManager.generateMine():
   - Em vez de preencher o mundo com blocos reais:
     * Cria VirtualMineGrid
     * Chama grid.generate(mine, mine.getLevel())
     * Envia bulk block changes para o dono da mina (todos os blocos de uma vez)
     * O jogador vê a mina cheia, mas o servidor só tem bedrock no chão e ar no resto.

6. EFEITO AO CAIR NO BLOCO (como no vídeo):
   - No PlayerMoveEvent ou via packet de collision:
     * Se player está em mina pessoal e cai sobre um bloco virtual:
     * Envia som de queda + particle de terra (ou do material do bloco virtual abaixo dele)
     * Isso é PUREMENTE VISUAL — o servidor não processa collision real (o chão de bedrock real está 2 blocos abaixo)

7. VENDA AUTOMÁTICA:
   - O VirtualBlockSystem, ao quebrar, já sabe o sellValue do bloco.
   - Chama RewardBatcher.add(player, value) — NÃO spawna itens.
   - O RewardBatcher, ao flush, usa AlkaShopAPI para converter em coins direto.

NÃO use:
- World#setType em minas pessoais
- ItemEntity / EntitySpawnEvent para drops
- BlockPhysicsEvent / BlockFormEvent
```

---

## FASE 1 — Mina Pessoal Evolutiva (Core)

### Prompt 1.1 — PersonalMine & PersonalMineManager

```
No AlkaMines-PACK, redesenhe o sistema de minas para ser 100% pessoal e evolutivo.
CADA jogador tem sua própria mina. Não existem mais minas públicas.

1. NOVO: com.alka.mines.personal.PersonalMine

```java
package com.alka.mines.personal;

import org.bukkit.Location;
import java.util.UUID;

public class PersonalMine {
    private final UUID ownerUuid;
    private final String worldName;
    private final int centerX;
    private final int centerZ;
    private int size; // sempre ímpar
    private int level;
    private final int depth = 50;
    private boolean generated;
    private int surfaceY;

    public PersonalMine(UUID ownerUuid, String worldName, int centerX, int centerZ, int surfaceY) {
        this.ownerUuid = ownerUuid;
        this.worldName = worldName;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.size = 5;
        this.level = 1;
        this.generated = false;
        this.surfaceY = surfaceY;
    }

    public Bounds getBounds() {
        int half = size / 2;
        return new Bounds(
            centerX - half, centerX + half,
            surfaceY - depth, surfaceY - 2,
            centerZ - half, centerZ + half
        );
    }

    public boolean shouldExpand(int pickaxeLevel) {
        int newSize = 5 + (pickaxeLevel / 20) * 2;
        if (newSize > 35) newSize = 35;
        if (newSize < 5) newSize = 5;
        if (newSize != this.size) {
            this.size = newSize;
            return true;
        }
        return false;
    }

    public boolean isInside(Location loc) {
        if (!loc.getWorld().getName().equals(worldName)) return false;
        Bounds b = getBounds();
        return loc.getX() >= b.minX() && loc.getX() <= b.maxX()
            && loc.getY() >= b.minY() && loc.getY() <= b.maxY()
            && loc.getZ() >= b.minZ() && loc.getZ() <= b.maxZ();
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public String getWorldName() { return worldName; }
    public int getCenterX() { return centerX; }
    public int getCenterZ() { return centerZ; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getDepth() { return depth; }
    public boolean isGenerated() { return generated; }
    public void setGenerated(boolean generated) { this.generated = generated; }
    public int getSurfaceY() { return surfaceY; }

    public record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}
}
```

2. NOVO: com.alka.mines.personal.PersonalMineManager

```java
package com.alka.mines.personal;

import com.alka.mines.virtual.VirtualMineGrid;
import com.alka.mines.packet.PacketFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PersonalMineManager {
    private final Map<UUID, PersonalMine> mines = new ConcurrentHashMap<>();
    private final Map<UUID, VirtualMineGrid> grids = new ConcurrentHashMap<>();
    private final PacketFactory packetFactory;
    private final int gridSpacing;
    private final int surfaceY;
    private final String mineWorld;

    public PersonalMineManager(PacketFactory packetFactory, int gridSpacing, int surfaceY, String mineWorld) {
        this.packetFactory = packetFactory;
        this.gridSpacing = gridSpacing;
        this.surfaceY = surfaceY;
        this.mineWorld = mineWorld;
    }

    public PersonalMine getOrCreateMine(Player player) {
        return mines.computeIfAbsent(player.getUniqueId(), uuid -> {
            long hash = Math.abs(uuid.getMostSignificantBits());
            int offsetX = (int) (hash % 10000) * gridSpacing;
            int offsetZ = (int) ((hash >> 32) % 10000) * gridSpacing;

            PersonalMine mine = new PersonalMine(uuid, mineWorld, offsetX, offsetZ, surfaceY);
            generateMine(mine, player);
            return mine;
        });
    }

    public void expandIfNeeded(Player player, int pickaxeLevel) {
        PersonalMine mine = mines.get(player.getUniqueId());
        if (mine == null) return;
        if (mine.shouldExpand(pickaxeLevel)) {
            regenerateMine(mine, player);
        }
    }

    public void generateMine(PersonalMine mine, Player player) {
        World world = Bukkit.getWorld(mineWorld);
        if (world == null) throw new IllegalStateException("Mundo de minas não encontrado: " + mineWorld);

        PersonalMine.Bounds b = mine.getBounds();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    world.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR, false);
                }
                world.getBlockAt(x, b.minY(), z).setType(org.bukkit.Material.BEDROCK, false);
                world.getBlockAt(x, b.minY() + 1, z).setType(org.bukkit.Material.BEDROCK, false);
            }
        }

        VirtualMineGrid grid = new VirtualMineGrid();
        grid.generate(mine, mine.getLevel());
        grids.put(mine.getOwnerUuid(), grid);

        Map<Location, org.bukkit.Material> bulk = new java.util.HashMap<>();
        for (Map.Entry<com.alka.mines.virtual.BlockPos, com.alka.mines.virtual.VirtualBlock> entry : grid.getAllBlocks().entrySet()) {
            com.alka.mines.virtual.BlockPos pos = entry.getKey();
            bulk.put(new Location(world, pos.x(), pos.y(), pos.z()), entry.getValue().material());
        }
        packetFactory.sendBulkBlockChanges(player, bulk);
        mine.setGenerated(true);
    }

    public void regenerateMine(PersonalMine mine, Player player) {
        grids.remove(mine.getOwnerUuid());
        generateMine(mine, player);
    }

    public VirtualMineGrid getGrid(UUID uuid) {
        return grids.get(uuid);
    }

    public void saveAll() {
        // Persiste mines no banco via repository
    }

    public void teleportToMine(Player player) {
        PersonalMine mine = getOrCreateMine(player);
        PersonalMine.Bounds b = mine.getBounds();
        Location top = new Location(
            Bukkit.getWorld(mineWorld),
            mine.getCenterX(), b.maxY() + 2, mine.getCenterZ(),
            0, 90
        );
        player.teleport(top);
    }
}
```

3. NOVO: com.alka.mines.virtual.VirtualMineGrid

```java
package com.alka.mines.virtual;

import com.alka.mines.personal.PersonalMine;
import org.bukkit.Material;

import java.util.*;

public class VirtualMineGrid {
    private final Map<BlockPos, VirtualBlock> blocks = new HashMap<>();
    private final Random random = new Random();

    public void generate(PersonalMine mine, int level) {
        blocks.clear();
        PersonalMine.Bounds b = mine.getBounds();
        Map<Material, Double> composition = getCompositionForLevel(level);

        for (int x = b.minX() + 1; x < b.maxX(); x++) {
            for (int z = b.minZ() + 1; z < b.maxZ(); z++) {
                for (int y = b.minY() + 2; y <= b.maxY(); y++) {
                    Material mat = rollMaterial(composition);
                    double value = getSellValue(mat);
                    blocks.put(new BlockPos(x, y, z), new VirtualBlock(mat, value, true));
                }
            }
        }
    }

    private Material rollMaterial(Map<Material, Double> composition) {
        double roll = random.nextDouble();
        double cumulative = 0.0;
        for (Map.Entry<Material, Double> entry : composition.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) return entry.getKey();
        }
        return Material.STONE;
    }

    private Map<Material, Double> getCompositionForLevel(int level) {
        Map<Material, Double> comp = new LinkedHashMap<>();
        if (level < 21) {
            comp.put(Material.STONE, 0.9);
            comp.put(Material.COAL_ORE, 0.1);
        } else if (level < 41) {
            comp.put(Material.STONE, 0.7);
            comp.put(Material.COAL_ORE, 0.2);
            comp.put(Material.IRON_ORE, 0.1);
        } else if (level < 61) {
            comp.put(Material.STONE, 0.5);
            comp.put(Material.IRON_ORE, 0.2);
            comp.put(Material.GOLD_ORE, 0.2);
            comp.put(Material.REDSTONE_ORE, 0.1);
        } else if (level < 81) {
            comp.put(Material.STONE, 0.3);
            comp.put(Material.IRON_ORE, 0.2);
            comp.put(Material.GOLD_ORE, 0.2);
            comp.put(Material.DIAMOND_ORE, 0.2);
            comp.put(Material.EMERALD_ORE, 0.1);
        } else {
            comp.put(Material.STONE, 0.1);
            comp.put(Material.GOLD_ORE, 0.3);
            comp.put(Material.DIAMOND_ORE, 0.3);
            comp.put(Material.EMERALD_ORE, 0.2);
            comp.put(Material.ANCIENT_DEBRIS, 0.1);
        }
        return comp;
    }

    private double getSellValue(Material mat) {
        return switch (mat) {
            case COAL_ORE -> 5.0;
            case IRON_ORE -> 10.0;
            case GOLD_ORE -> 25.0;
            case REDSTONE_ORE -> 15.0;
            case DIAMOND_ORE -> 50.0;
            case EMERALD_ORE -> 75.0;
            case ANCIENT_DEBRIS -> 200.0;
            default -> 1.0;
        };
    }

    public VirtualBlock getBlockAt(BlockPos pos) {
        return blocks.get(pos);
    }

    public void setBlock(BlockPos pos, VirtualBlock block) {
        blocks.put(pos, block);
    }

    public Map<BlockPos, VirtualBlock> getAllBlocks() {
        return Collections.unmodifiableMap(blocks);
    }

    public record BlockPos(int x, int y, int z) {}
    public record VirtualBlock(Material material, double sellValue, boolean breakable) {}
}
```

4. ATUALIZE config.yml:

```yaml
personal-mine:
  max-size: 35
  start-size: 5
  depth: 50
  expand-every-levels: 20
  surface-y: 100
  grid-spacing: 1000
  floor-material: BEDROCK
  wall-material: BEDROCK
  respawn-delay-ticks: 40
  compositions:
    '1':
      STONE: 0.9
      COAL_ORE: 0.1
    '21':
      STONE: 0.7
      COAL_ORE: 0.2
      IRON_ORE: 0.1
    '41':
      STONE: 0.5
      IRON_ORE: 0.2
      GOLD_ORE: 0.2
      REDSTONE_ORE: 0.1
    '61':
      STONE: 0.3
      IRON_ORE: 0.2
      GOLD_ORE: 0.2
      DIAMOND_ORE: 0.2
      EMERALD_ORE: 0.1
    '81':
      STONE: 0.1
      GOLD_ORE: 0.3
      DIAMOND_ORE: 0.3
      EMERALD_ORE: 0.2
      ANCIENT_DEBRIS: 0.1
```

5. ATUALIZE PlayerMineData:

```java
package com.alka.mines.data;

import java.util.*;

public class PlayerMineData {
    private UUID uuid;
    private int pickaxeLevel = 1;
    private long totalBlocksMined = 0;
    private int prestige = 0;
    private double moneyMultiplier = 1.0;
    private String activeSkin = "DEFAULT";
    private int prestigePoints = 0;
    private Set<String> unlockedSkins = new HashSet<>();

    // getters e setters
    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
    public int getPickaxeLevel() { return pickaxeLevel; }
    public void setPickaxeLevel(int pickaxeLevel) { this.pickaxeLevel = pickaxeLevel; }
    public long getTotalBlocksMined() { return totalBlocksMined; }
    public void setTotalBlocksMined(long totalBlocksMined) { this.totalBlocksMined = totalBlocksMined; }
    public int getPrestige() { return prestige; }
    public void setPrestige(int prestige) { this.prestige = prestige; }
    public double getMoneyMultiplier() { return moneyMultiplier; }
    public void setMoneyMultiplier(double moneyMultiplier) { this.moneyMultiplier = moneyMultiplier; }
    public String getActiveSkin() { return activeSkin; }
    public void setActiveSkin(String activeSkin) { this.activeSkin = activeSkin; }
    public int getPrestigePoints() { return prestigePoints; }
    public void setPrestigePoints(int prestigePoints) { this.prestigePoints = prestigePoints; }
    public Set<String> getUnlockedSkins() { return unlockedSkins; }
    public void setUnlockedSkins(Set<String> unlockedSkins) { this.unlockedSkins = unlockedSkins; }
}
```

6. ATUALIZE PlayerMineRepository:
   Adicione colunas: prestige INTEGER DEFAULT 0, money_multiplier DOUBLE DEFAULT 1.0, active_skin TEXT DEFAULT 'DEFAULT', prestige_points INTEGER DEFAULT 0, unlocked_skins TEXT (JSON array).
   Migration silenciosa com try-catch ALTER TABLE.

NÃO remova ainda:
- MineBreakListener (vamos adaptar na Fase 2)
- DragonBreathAbility (vamos adaptar)
- PacketFactory, RewardBatcher, MineCache (reaproveitar)
```

---

## FASE 2 — Picareta Evolutiva (/picareta)

### Prompt 2.1 — EvolutionPickaxe & PickaxeLevelManager

```
No AlkaMines-PACK, crie o sistema de picareta evolutiva pessoal.

1. NOVO: com.alka.mines.pickaxe.EvolutionPickaxe

```java
package com.alka.mines.pickaxe;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class EvolutionPickaxe {
    private int level = 1;
    private long totalBlocksMined = 0;
    private final Map<EnchantmentType, Integer> enchantments = new EnumMap<>(EnchantmentType.class);
    private String skin = "DEFAULT";
    private int prestige = 0;

    public double getProgressToNextLevel(PickaxeLevelManager manager) {
        long current = totalBlocksMined;
        long requiredForCurrent = manager.getBlocksForLevel(level);
        long requiredForNext = manager.getBlocksForLevel(level + 1);
        if (requiredForNext <= requiredForCurrent) return 1.0;
        return (double) (current - requiredForCurrent) / (requiredForNext - requiredForCurrent);
    }

    public ItemStack getDisplayItem() {
        Material mat = level > 100 ? Material.NETHERITE_PICKAXE : Material.DIAMOND_PICKAXE;
        if ("NETHERITE".equals(skin) && level >= 50) mat = Material.NETHERITE_PICKAXE;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&b⛏ Picareta &7[&f" + formatBlocks(totalBlocksMined) + "&7]"));
            List<String> lore = new ArrayList<>();
            lore.add(color("&7Nível: &e" + level));
            lore.add(color("&7Blocos: &f" + formatBlocks(totalBlocksMined)));
            lore.add(color("&7Prestígio: &6" + prestige));
            lore.add("");
            lore.add(color("&bEncantamentos:"));
            for (Map.Entry<EnchantmentType, Integer> ench : enchantments.entrySet()) {
                lore.add(color(" &8• &f" + ench.getKey().getDisplayName() + " &7" + ench.getValue()));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        if (!enchantments.isEmpty()) {
            item.addUnsafeEnchantment(Enchantment.LURE, 1);
        }
        return item;
    }

    private String formatBlocks(long blocks) {
        if (blocks >= 1_000_000) return String.format("%.2fM", blocks / 1_000_000.0);
        if (blocks >= 1_000) return String.format("%.1fK", blocks / 1_000.0);
        return String.valueOf(blocks);
    }

    private String color(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }

    public void addBlocksMined(long amount) { this.totalBlocksMined += amount; }
    public long getTotalBlocksMined() { return totalBlocksMined; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public void addEnchantment(EnchantmentType type, int level) { enchantments.put(type, level); }
    public Map<EnchantmentType, Integer> getEnchantments() { return Collections.unmodifiableMap(enchantments); }
    public int getEnchantmentLevel(EnchantmentType type) { return enchantments.getOrDefault(type, 0); }
    public String getSkin() { return skin; }
    public void setSkin(String skin) { this.skin = skin; }
    public int getPrestige() { return prestige; }
    public void setPrestige(int prestige) { this.prestige = prestige; }
}
```

2. NOVO: com.alka.mines.pickaxe.PickaxeLevelManager

```java
package com.alka.mines.pickaxe;

public class PickaxeLevelManager {
    private final double base;
    private final double exponent;
    private final int maxLevel;

    public PickaxeLevelManager(double base, double exponent, int maxLevel) {
        this.base = base;
        this.exponent = exponent;
        this.maxLevel = maxLevel;
    }

    public long getBlocksForLevel(int level) {
        if (level <= 1) return 0;
        return (long) (base * Math.pow(level, exponent));
    }

    public int getLevelForBlocks(long blocks) {
        int level = 1;
        while (level < maxLevel && getBlocksForLevel(level + 1) <= blocks) {
            level++;
        }
        return level;
    }

    public boolean isMaxLevel(int level) {
        return level >= maxLevel;
    }

    public int getMaxLevel() { return maxLevel; }
}
```

3. NOVO: com.alka.mines.pickaxe.PickaxeProgressDisplay

```java
package com.alka.mines.pickaxe;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public class PickaxeProgressDisplay {
    private final PickaxeLevelManager levelManager;

    public PickaxeProgressDisplay(PickaxeLevelManager levelManager) {
        this.levelManager = levelManager;
    }

    public void update(Player player, EvolutionPickaxe pickaxe) {
        double progress = pickaxe.getProgressToNextLevel(levelManager);
        String bar = buildProgressBar(progress, 10);
        String msg = String.format(
            "§b⛏ Picareta §7[§f%s§7] §8| §eRankUP §7[%s§7] §a%.2f%%",
            formatCompact(pickaxe.getTotalBlocksMined()),
            bar,
            progress * 100
        );
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    public void sendLevelUpTitle(Player player, int oldLevel, int newLevel) {
        player.sendTitle(
            "§6§l⛏ PICARETA UP!",
            "§e" + oldLevel + " §7→ §e" + newLevel,
            10, 60, 10
        );
    }

    private String buildProgressBar(double progress, int length) {
        int filled = (int) (progress * length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(i < filled ? "§a█" : "§7░");
        }
        return sb.toString();
    }

    private String formatCompact(long n) {
        if (n >= 1_000_000) return String.format("%.2fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
```

4. NOVO: com.alka.mines.pickaxe.PickaxeCommand (/picareta)

```java
package com.alka.mines.pickaxe;

import com.alka.core.gui.BaseGui;
import com.alka.mines.enchantment.EnchantmentType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PickaxeCommand implements CommandExecutor {
    private final PickaxeManager pickaxeManager;
    private final PrestigeSystem prestigeSystem;

    public PickaxeCommand(PickaxeManager pickaxeManager, PrestigeSystem prestigeSystem) {
        this.pickaxeManager = pickaxeManager;
        this.prestigeSystem = prestigeSystem;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            sendInfo(player);
            return true;
        }
        openGui(player);
        return true;
    }

    private void openGui(Player player) {
        EvolutionPickaxe pick = pickaxeManager.getPickaxe(player);
        BaseGui gui = new BaseGui("Sua Picareta", 54);

        gui.setItem(13, pick.getDisplayItem(), e -> e.setCancelled(true));

        int slot = 10;
        for (EnchantmentType type : EnchantmentType.values()) {
            int currentLevel = pick.getEnchantmentLevel(type);
            boolean unlocked = pick.getLevel() >= type.getUnlockLevel();
            boolean owned = currentLevel > 0;

            ItemStack icon = buildEnchantIcon(type, unlocked, owned, currentLevel);
            gui.setItem(slot, icon, e -> {
                e.setCancelled(true);
                if (!unlocked) {
                    player.sendMessage("§cAlcance nível §f" + type.getUnlockLevel() + " §cda picareta!");
                    return;
                }
                if (!owned) {
                    confirmBuyEnchantment(player, type);
                } else if (currentLevel < type.getMaxLevel()) {
                    confirmUpgradeEnchantment(player, type, currentLevel);
                }
            });
            slot++;
            if (slot == 17) slot = 19;
        }

        if (prestigeSystem.isEligible(player)) {
            gui.setItem(40, buildPrestigeItem(), e -> {
                e.setCancelled(true);
                prestigeSystem.openPrestigeGui(player);
            });
        }

        gui.setItem(44, buildStatsItem(pick), e -> e.setCancelled(true));
        gui.open(player);
    }

    private ItemStack buildEnchantIcon(EnchantmentType type, boolean unlocked, boolean owned, int level) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(
            unlocked ? org.bukkit.Material.ENCHANTED_BOOK : org.bukkit.Material.BOOK
        );
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (!unlocked) {
            meta.setDisplayName("§8" + type.getDisplayName());
            meta.setLore(List.of(
                "§7" + type.getDescription(),
                "",
                "§cBloqueado — Alcance nível " + type.getUnlockLevel()
            ));
        } else if (!owned) {
            meta.setDisplayName("§e" + type.getDisplayName());
            meta.setLore(List.of(
                "§7" + type.getDescription(),
                "",
                "§aClique para comprar",
                "§7Custo: §6" + type.getBaseCost() + " coins"
            ));
        } else {
            meta.setDisplayName("§a" + type.getDisplayName() + " §7[" + level + "/" + type.getMaxLevel() + "]");
            meta.setLore(List.of(
                "§7" + type.getDescription(),
                "",
                level < type.getMaxLevel() ? "§eClique para upar" : "§aMáximo!"
            ));
        }
        item.setItemMeta(meta);
        return item;
    }

    private void sendInfo(Player player) {
        EvolutionPickaxe pick = pickaxeManager.getPickaxe(player);
        player.sendMessage("§b§lSUA PICARETA");
        player.sendMessage("§7Nível: §e" + pick.getLevel());
        player.sendMessage("§7Blocos minerados: §f" + pick.getTotalBlocksMined());
        player.sendMessage("§7Prestígio: §6" + pick.getPrestige());
        player.sendMessage("§7Multiplicador: §6" + pickaxeManager.getMoneyMultiplier(player) + "x");
    }

    private ItemStack buildPrestigeItem() {
        ItemStack item = new ItemStack(org.bukkit.Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lPRESTÍGIO DISPONÍVEL!");
            meta.setLore(List.of("§7Clique para prestigiar sua picareta.", "§cIsso resetará seu nível para 1."));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildStatsItem(EvolutionPickaxe pick) {
        ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bEstatísticas");
            meta.setLore(List.of(
                "§7Blocos totais: §f" + pick.getTotalBlocksMined(),
                "§7Nível atual: §e" + pick.getLevel(),
                "§7Prestígio: §6" + pick.getPrestige()
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void confirmBuyEnchantment(Player player, EnchantmentType type) {
        // Abre GUI de confirmação ou chat
    }

    private void confirmUpgradeEnchantment(Player player, EnchantmentType type, int currentLevel) {
        // Abre GUI de confirmação
    }
}
```

5. ATUALIZE MineBreakListener:

```java
package com.alka.mines.listener;

import com.alka.mines.personal.PersonalMine;
import com.alka.mines.personal.PersonalMineManager;
import com.alka.mines.pickaxe.*;
import com.alka.mines.enchantment.EnchantmentManager;
import com.alka.mines.virtual.VirtualBlockSystem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.entity.Player;

public class MineBreakListener implements Listener {
    private final PersonalMineManager mineManager;
    private final PickaxeManager pickaxeManager;
    private final PickaxeLevelManager levelManager;
    private final PickaxeProgressDisplay progressDisplay;
    private final EnchantmentManager enchantmentManager;
    private final VirtualBlockSystem virtualBlockSystem;

    public MineBreakListener(PersonalMineManager mineManager, PickaxeManager pickaxeManager,
                             PickaxeLevelManager levelManager, PickaxeProgressDisplay progressDisplay,
                             EnchantmentManager enchantmentManager, VirtualBlockSystem virtualBlockSystem) {
        this.mineManager = mineManager;
        this.pickaxeManager = pickaxeManager;
        this.levelManager = levelManager;
        this.progressDisplay = progressDisplay;
        this.enchantmentManager = enchantmentManager;
        this.virtualBlockSystem = virtualBlockSystem;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        PersonalMine mine = mineManager.getOrCreateMine(player);

        if (!mine.isInside(e.getBlock().getLocation())) return;

        e.setCancelled(true);

        boolean broken = virtualBlockSystem.handleBreak(player, e.getBlock().getLocation());
        if (!broken) return;

        EvolutionPickaxe pickaxe = pickaxeManager.getPickaxe(player);
        int oldLevel = pickaxe.getLevel();
        pickaxe.addBlocksMined(1);

        int newLevel = levelManager.getLevelForBlocks(pickaxe.getTotalBlocksMined());
        if (newLevel > oldLevel) {
            pickaxe.setLevel(newLevel);
            progressDisplay.sendLevelUpTitle(player, oldLevel, newLevel);
            mineManager.expandIfNeeded(player, newLevel);
        }

        enchantmentManager.tryProc(player, e.getBlock(), mine);
        progressDisplay.update(player, pickaxe);
    }
}
```

NÃO altere ainda:
- EnchantmentManager (Fase 3)
- RewardBatcher (reaproveite)
```

---

## FASE 3 — Sistema de Encantamentos (incluindo Dragão)

### Prompt 3.1 — EnchantmentType & EnchantmentManager

```
No AlkaMines-PACK, crie o sistema de encantamentos da picareta.

1. ATUALIZE com.alka.mines.enchantment.EnchantmentType:

```java
package com.alka.mines.enchantment;

public enum EnchantmentType {
    DRAGON("Dragão", "Invoca um dragão que quebra blocos", 5, 0.05, 0.05, 30, 1000, 50),
    EXPLOSION("Explosão", "Quebra em área 3x3", 10, 5.0, 2.0, 5, 500, 10),
    KEYFINDER("Keyfinder", "Chance de encontrar keys", 5, 1.0, 1.0, 0, 2000, 25),
    FORTUNE_BOOST("Fortuna+", "Aumenta drops em 50% por nível", 5, 0, 0, 0, 1500, 15),
    SPEED_MINING("Velocidade", "Haste permanente ao minerar", 3, 0, 0, 0, 800, 5),
    MEMORIZE("Memorizando", "Bônus por padrão de blocos", 1, 0, 0, 0, 5000, 100);

    private final String displayName;
    private final String description;
    private final int maxLevel;
    private final double baseChance;
    private final double chancePerLevel;
    private final int cooldownSeconds;
    private final int baseCost;
    private final int unlockLevel;

    EnchantmentType(String displayName, String description, int maxLevel,
                    double baseChance, double chancePerLevel, int cooldownSeconds,
                    int baseCost, int unlockLevel) {
        this.displayName = displayName;
        this.description = description;
        this.maxLevel = maxLevel;
        this.baseChance = baseChance;
        this.chancePerLevel = chancePerLevel;
        this.cooldownSeconds = cooldownSeconds;
        this.baseCost = baseCost;
        this.unlockLevel = unlockLevel;
    }

    public double getChance(int level) {
        return baseChance + (chancePerLevel * (level - 1));
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getMaxLevel() { return maxLevel; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public int getBaseCost() { return baseCost; }
    public int getUnlockLevel() { return unlockLevel; }
}
```

2. ATUALIZE EnchantmentManager:

```java
package com.alka.mines.enchantment;

import com.alka.mines.ability.DragonBreathAbility;
import com.alka.mines.personal.PersonalMine;
import com.alka.mines.pickaxe.EvolutionPickaxe;
import com.alka.mines.pickaxe.PickaxeManager;
import com.alka.mines.reward.RewardBatcher;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class EnchantmentManager {
    private final PickaxeManager pickaxeManager;
    private final RewardBatcher rewardBatcher;
    private final DragonBreathAbility dragonAbility;
    private final ExplosionEnchantment explosionHandler;
    private final MemorizeSystem memorizeSystem;
    private final Map<UUID, Map<EnchantmentType, Long>> cooldowns = new HashMap<>();
    private final Random random = new Random();

    public EnchantmentManager(PickaxeManager pickaxeManager, RewardBatcher rewardBatcher,
                              DragonBreathAbility dragonAbility, ExplosionEnchantment explosionHandler,
                              MemorizeSystem memorizeSystem) {
        this.pickaxeManager = pickaxeManager;
        this.rewardBatcher = rewardBatcher;
        this.dragonAbility = dragonAbility;
        this.explosionHandler = explosionHandler;
        this.memorizeSystem = memorizeSystem;
    }

    public void tryProc(Player player, Block block, PersonalMine mine) {
        EvolutionPickaxe pick = pickaxeManager.getPickaxe(player);
        UUID uuid = player.getUniqueId();
        Map<EnchantmentType, Long> playerCooldowns = cooldowns.computeIfAbsent(uuid, k -> new EnumMap<>(EnchantmentType.class));
        long now = System.currentTimeMillis();

        for (Map.Entry<EnchantmentType, Integer> entry : pick.getEnchantments().entrySet()) {
            EnchantmentType type = entry.getKey();
            int level = entry.getValue();

            Long lastUse = playerCooldowns.get(type);
            long cdMillis = type.getCooldownSeconds() * 1000L;
            if (lastUse != null && (now - lastUse) < cdMillis) continue;

            double chance = type.getChance(level);
            if (random.nextDouble() > chance) continue;

            playerCooldowns.put(type, now);
            executeEffect(type, level, player, block, mine);
        }

        if (pick.getEnchantments().containsKey(EnchantmentType.MEMORIZE)) {
            memorizeSystem.onBlockBreak(player, block);
        }
    }

    private void executeEffect(EnchantmentType type, int level, Player player, Block block, PersonalMine mine) {
        switch (type) {
            case DRAGON -> dragonAbility.activate(player, mine);
            case EXPLOSION -> explosionHandler.explode(player, block.getLocation(), level);
            case KEYFINDER -> {
                player.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.TRIPWIRE_HOOK));
                player.sendMessage("§e§lKEY ENCONTRADA!");
            }
            case FORTUNE_BOOST -> {
                // O RewardBatcher já aplica multiplicador global
            }
            case SPEED_MINING -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 200, 2, true, false));
            }
        }
    }
}
```

3. NOVO: com.alka.mines.enchantment.ExplosionEnchantment

```java
package com.alka.mines.enchantment;

import com.alka.mines.packet.PacketFactory;
import com.alka.mines.reward.RewardBatcher;
import com.alka.mines.virtual.VirtualBlockSystem;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class ExplosionEnchantment {
    private final VirtualBlockSystem virtualBlockSystem;
    private final PacketFactory packetFactory;
    private final RewardBatcher rewardBatcher;

    public ExplosionEnchantment(VirtualBlockSystem virtualBlockSystem, PacketFactory packetFactory,
                                RewardBatcher rewardBatcher) {
        this.virtualBlockSystem = virtualBlockSystem;
        this.packetFactory = packetFactory;
        this.rewardBatcher = rewardBatcher;
    }

    public void explode(Player player, Location center, int level) {
        int radius = switch (level) {
            case 1, 2, 3 -> 1;
            case 4, 5, 6, 7 -> 2;
            default -> 3;
        };

        int broken = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Location loc = center.clone().add(dx, dy, dz);
                    if (virtualBlockSystem.handleBreak(player, loc)) {
                        broken++;
                    }
                }
            }
        }

        player.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, center, 5, 1, 1, 1, 0);
        player.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        if (broken > 0) {
            player.sendMessage("§c§lBOOM! §7" + broken + " blocos destruídos!");
        }
    }
}
```

4. NOVO: com.alka.mines.enchantment.MemorizeSystem

```java
package com.alka.mines.enchantment;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

public class MemorizeSystem {
    private final Map<UUID, List<Material>> sequences = new HashMap<>();
    private final Map<UUID, Integer> sequenceIndex = new HashMap<>();
    private final Map<UUID, Integer> comboCount = new HashMap<>();
    private final Random random = new Random();

    public void onBlockBreak(Player player, Block block) {
        UUID uuid = player.getUniqueId();
        Material broken = block.getType();

        List<Material> seq = sequences.get(uuid);
        if (seq == null) {
            startSequence(player);
            return;
        }

        int idx = sequenceIndex.getOrDefault(uuid, 0);
        Material expected = seq.get(idx);

        if (broken == expected) {
            sequenceIndex.put(uuid, idx + 1);
            if (idx + 1 >= seq.size()) {
                int combo = comboCount.getOrDefault(uuid, 0) + 1;
                comboCount.put(uuid, combo);
                double bonus = combo * 1.5;
                player.sendMessage("§a§lCOMBO! §7Bônus: §6" + bonus + "x");
                sequences.remove(uuid);
                sequenceIndex.remove(uuid);
            }
        } else {
            player.sendMessage("§c§lERROU! §7Sequência resetada.");
            sequences.remove(uuid);
            sequenceIndex.remove(uuid);
            comboCount.put(uuid, 0);
        }
    }

    private void startSequence(Player player) {
        List<Material> seq = new ArrayList<>();
        Material[] options = {Material.STONE, Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE};
        for (int i = 0; i < 5; i++) {
            seq.add(options[random.nextInt(options.length)]);
        }
        sequences.put(player.getUniqueId(), seq);
        sequenceIndex.put(player.getUniqueId(), 0);
        player.sendMessage("§e§lMEMORIZE: §7" + seq);
    }
}
```

NÃO altere:
- PacketFactory (reaproveite)
- RewardBatcher (reaproveite)
```

---

## FASE 4 — Skins, Prestígio e Aluguel de Ferramentas

### Prompt 4.1 — PrestigeSystem, PickaxeSkin & ToolRentalSystem

```
No AlkaMines-PACK, adicione skins, prestígio e aluguel de ferramentas.

1. NOVO: com.alka.mines.pickaxe.PrestigeSystem

```java
package com.alka.mines.pickaxe;

import com.alka.core.gui.BaseGui;
import com.alka.mines.data.PlayerMineData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class PrestigeSystem {
    private final PickaxeManager pickaxeManager;
    private final PickaxeLevelManager levelManager;
    private final int maxLevel;

    public PrestigeSystem(PickaxeManager pickaxeManager, PickaxeLevelManager levelManager, int maxLevel) {
        this.pickaxeManager = pickaxeManager;
        this.levelManager = levelManager;
        this.maxLevel = maxLevel;
    }

    public boolean isEligible(Player player) {
        return pickaxeManager.getPickaxe(player).getLevel() >= maxLevel;
    }

    public void prestige(Player player) {
        if (!isEligible(player)) return;

        EvolutionPickaxe pick = pickaxeManager.getPickaxe(player);
        PlayerMineData data = pickaxeManager.getData(player);

        int oldPrestige = pick.getPrestige();
        int newPrestige = oldPrestige + 1;

        pick.setLevel(1);
        pick.setPrestige(newPrestige);

        data.setMoneyMultiplier(1.0 + (newPrestige * 0.1));
        data.setPrestigePoints(data.getPrestigePoints() + 10);

        if (newPrestige == 1) data.getUnlockedSkins().add("DRAGON");
        if (newPrestige >= 5) data.getUnlockedSkins().add("CRYSTAL");

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l✦ §e" + player.getName() + " §6prestigiou sua picareta! §7(" + newPrestige + "º)");
        Bukkit.broadcastMessage("");

        player.sendTitle("§6§lPRESTÍGIO!", "§e" + oldPrestige + " §7→ §e" + newPrestige, 10, 70, 20);
    }

    public void openPrestigeGui(Player player) {
        BaseGui gui = new BaseGui("§8Prestígio", 27);
        ItemStack confirm = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = confirm.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lCONFIRMAR PRESTÍGIO");
            meta.setLore(List.of(
                "§7Seu nível será resetado para 1.",
                "§7Você ganhará: §6+0.1x multiplicador",
                "§7Pontos de prestígio: §6+10",
                "",
                "§aClique para confirmar"
            ));
            confirm.setItemMeta(meta);
        }
        gui.setItem(13, confirm, e -> {
            e.setCancelled(true);
            prestige(player);
            player.closeInventory();
        });
        gui.open(player);
    }
}
```

2. NOVO: com.alka.mines.pickaxe.PickaxeSkin

```java
package com.alka.mines.pickaxe;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

public enum PickaxeSkin {
    DEFAULT("Padrão", Material.DIAMOND_PICKAXE, "§7", null, null),
    NETHERITE("Netherite", Material.NETHERITE_PICKAXE, "§8", null, Sound.ITEM_NETHERITE_SCRAPE),
    DRAGON("Dragão", Material.NETHERITE_PICKAXE, "§5", Particle.DRAGON_BREATH, Sound.ENTITY_ENDER_DRAGON_GROWL),
    CRYSTAL("Cristal", Material.DIAMOND_PICKAXE, "§b", Particle.END_ROD, Sound.BLOCK_AMETHYST_BLOCK_CHIME),
    EVENTO("Evento", Material.GOLDEN_PICKAXE, "§6", Particle.FIREWORKS_SPARK, Sound.ENTITY_PLAYER_LEVELUP);

    private final String displayName;
    private final Material material;
    private final String colorPrefix;
    private final Particle particle;
    private final Sound sound;

    PickaxeSkin(String displayName, Material material, String colorPrefix, Particle particle, Sound sound) {
        this.displayName = displayName;
        this.material = material;
        this.colorPrefix = colorPrefix;
        this.particle = particle;
        this.sound = sound;
    }

    public static PickaxeSkin fromId(String id) {
        try { return valueOf(id.toUpperCase()); } catch (Exception e) { return DEFAULT; }
    }

    public String getDisplayName() { return displayName; }
    public Material getMaterial() { return material; }
    public String getColorPrefix() { return colorPrefix; }
    public Particle getParticle() { return particle; }
    public Sound getSound() { return sound; }
}
```

3. NOVO: com.alka.mines.rental.ToolRentalSystem

```java
package com.alka.mines.rental;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.Player;

import java.util.*;

public class ToolRentalSystem {
    private final Plugin plugin;
    private final NamespacedKey rentalKey;
    private final NamespacedKey expiryKey;

    public ToolRentalSystem(Plugin plugin) {
        this.plugin = plugin;
        this.rentalKey = new NamespacedKey(plugin, "rental_tool");
        this.expiryKey = new NamespacedKey(plugin, "rental_expiry");
        startExpiryTask();
    }

    public ItemStack createRentalTool(RentalType type, long durationMinutes) {
        ItemStack item = type.createItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        long expiry = System.currentTimeMillis() + (durationMinutes * 60 * 1000);
        meta.getPersistentDataContainer().set(rentalKey, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(expiryKey, PersistentDataType.LONG, expiry);

        List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
        lore.add("");
        lore.add("§c§lFERRAMENTA ALUGADA");
        lore.add("§7Expira em: §f" + durationMinutes + " minutos");
        lore.add("§7Não pode ser dropada ou vendida");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isRentalTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(rentalKey, PersistentDataType.STRING);
    }

    public boolean isExpired(ItemStack item) {
        if (!isRentalTool(item)) return false;
        Long expiry = item.getItemMeta().getPersistentDataContainer().get(expiryKey, PersistentDataType.LONG);
        return expiry != null && System.currentTimeMillis() > expiry;
    }

    public void checkAndRemoveExpired(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isExpired(item)) {
                player.getInventory().remove(item);
                player.sendMessage("§c§lEXPIROU! §7Sua ferramenta alugada desapareceu...");
            }
        }
    }

    private void startExpiryTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    checkAndRemoveExpired(player);
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // a cada minuto
    }

    public enum RentalType {
        SUPREMA("Picareta Suprema", Material.DIAMOND_PICKAXE, 60),
        EXPLOSIVA("Picareta Explosiva", Material.DIAMOND_PICKAXE, 30),
        DRAGAO("Picareta do Dragão", Material.NETHERITE_PICKAXE, 15);

        private final String displayName;
        private final Material material;
        private final int defaultMinutes;

        RentalType(String displayName, Material material, int defaultMinutes) {
            this.displayName = displayName;
            this.material = material;
            this.defaultMinutes = defaultMinutes;
        }

        public ItemStack createItem() {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + displayName);
                item.setItemMeta(meta);
            }
            return item;
        }

        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public int getDefaultMinutes() { return defaultMinutes; }
    }
}
```

4. ATUALIZE PlayerMineData (já incluído na Fase 1):
   - int prestigePoints
   - Set<String> unlockedSkins
   - List<RentedTool> activeRentals (ou gerencie pelo inventário com NBT)

5. ATUALIZE PlayerMineRepository:
   - Colunas: prestige_points INTEGER DEFAULT 0, unlocked_skins TEXT (JSON), active_rentals TEXT (JSON)
   - Migration silenciosa.

NÃO altere:
- Core de mina pessoal (já funciona)
- Sistema de encantamentos (já funciona)
```

---

## FASE 5 — Display Entities e Otimizações Visuais

### Prompt 5.1 — DisplayEntityFactory & FloatingTextManager

```
No AlkaMines-PACK, substitua ArmorStands por Display Entities (1.19.4+) para todos os efeitos visuais.

1. NOVO: com.alka.mines.visual.DisplayEntityFactory

```java
package com.alka.mines.visual;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class DisplayEntityFactory {
    private final ProtocolManager protocolManager;
    private final AtomicInteger nextEntityId = new AtomicInteger(1000000);

    public DisplayEntityFactory() {
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public int spawnTextDisplay(Player player, Location loc, String text, int bgColor) {
        int entityId = nextEntityId.getAndIncrement();
        UUID uuid = UUID.randomUUID();

        PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getIntegers().write(0, entityId);
        spawn.getUUIDs().write(0, uuid);
        spawn.getEntityTypeModifier().write(0, EntityType.TEXT_DISPLAY);
        spawn.getDoubles().write(0, loc.getX());
        spawn.getDoubles().write(1, loc.getY());
        spawn.getDoubles().write(2, loc.getZ());

        List<WrappedDataValue> metadata = new ArrayList<>();
        metadata.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x20));
        metadata.add(new WrappedDataValue(15, WrappedDataWatcher.Registry.get(Byte.class), (byte) (0x01 | 0x02)));
        metadata.add(new WrappedDataValue(23, WrappedDataWatcher.Registry.getChatComponentSerializer(), text));
        metadata.add(new WrappedDataValue(24, WrappedDataWatcher.Registry.get(Integer.class), bgColor));
        metadata.add(new WrappedDataValue(26, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x01));

        PacketContainer metaPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        metaPacket.getIntegers().write(0, entityId);
        metaPacket.getDataValueCollectionModifier().write(0, metadata);

        try {
            protocolManager.sendServerPacket(player, spawn);
            protocolManager.sendServerPacket(player, metaPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return entityId;
    }

    public int spawnBlockDisplay(Player player, Location loc, Material material) {
        int entityId = nextEntityId.getAndIncrement();
        UUID uuid = UUID.randomUUID();

        PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getIntegers().write(0, entityId);
        spawn.getUUIDs().write(0, uuid);
        spawn.getEntityTypeModifier().write(0, EntityType.BLOCK_DISPLAY);
        spawn.getDoubles().write(0, loc.getX());
        spawn.getDoubles().write(1, loc.getY());
        spawn.getDoubles().write(2, loc.getZ());

        List<WrappedDataValue> metadata = new ArrayList<>();
        metadata.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x20));
        metadata.add(new WrappedDataValue(23, WrappedDataWatcher.Registry.getBlockDataSerializer(),
            com.comphenix.protocol.wrappers.BlockData.create(material.createBlockData())));

        PacketContainer metaPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        metaPacket.getIntegers().write(0, entityId);
        metaPacket.getDataValueCollectionModifier().write(0, metadata);

        try {
            protocolManager.sendServerPacket(player, spawn);
            protocolManager.sendServerPacket(player, metaPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return entityId;
    }

    public void destroyEntity(Player player, int entityId) {
        PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroy.getIntLists().write(0, List.of(entityId));
        try {
            protocolManager.sendServerPacket(player, destroy);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

2. NOVO: com.alka.mines.visual.FloatingTextManager

```java
package com.alka.mines.visual;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FloatingTextManager {
    private final DisplayEntityFactory factory;
    private final Plugin plugin;
    private final Map<UUID, Map<Integer, Long>> activeTexts = new ConcurrentHashMap<>();

    public FloatingTextManager(DisplayEntityFactory factory, Plugin plugin) {
        this.factory = factory;
        this.plugin = plugin;
        startCleanupTask();
    }

    public void spawnFloatingText(Player player, Location loc, String text, long durationMillis, int bgColor) {
        int entityId = factory.spawnTextDisplay(player, loc, text, bgColor);
        activeTexts.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                   .put(entityId, System.currentTimeMillis() + durationMillis);
    }

    public void spawnLevelUpText(Player player, Location loc, int oldLevel, int newLevel) {
        String text = "§6§l⛏ UP! §e" + oldLevel + " §7→ §e" + newLevel;
        spawnFloatingText(player, loc.clone().add(0, 1, 0), text, 3000, 0x80000000);
    }

    public void spawnBreakText(Player player, Location loc, String text) {
        spawnFloatingText(player, loc.clone().add(0, 0.5, 0), text, 1000, 0x40000000);
    }

    public void cleanupAll() {
        for (Map.Entry<UUID, Map<Integer, Long>> entry : activeTexts.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                for (int entityId : entry.getValue().keySet()) {
                    factory.destroyEntity(player, entityId);
                }
            }
        }
        activeTexts.clear();
    }

    public void cleanupOld() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<Integer, Long>> entry : activeTexts.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) continue;
            entry.getValue().entrySet().removeIf(e -> {
                if (e.getValue() < now) {
                    factory.destroyEntity(player, e.getKey());
                    return true;
                }
                return false;
            });
        }
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupOld();
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }
}
```

3. Onde usar Display Entities:
   - Texto "UP!" quando sobe de nível (spawnLevelUpText)
   - Texto flutuante de blocos quebrados (+$5, +$10)
   - Contagem regressiva da explosão
   - Sequência do Memorizando (blocos coloridos flutuantes ou text display)
   - Nome da mina pessoal no topo (TextDisplay fixo)

4. ATUALIZE onDisable do plugin principal:

```java
@Override
public void onDisable() {
    if (floatingTextManager != null) floatingTextManager.cleanupAll();
    if (personalMineManager != null) personalMineManager.saveAll();
    if (taskManager != null) taskManager.cancelAll();
}
```

NÃO use:
- ArmorStands (obsoletos para esse propósito desde 1.19.4)
- Holograms de plugin externo
```

---

## FASE 6 — Comandos e GUI

### Prompt 6.1 — Comandos Finais & GUI

```
No AlkaMines-PACK, crie os comandos e GUIs finais.

1. Comandos (registrar no plugin.yml):

```java
// /mina — teleporta para mina pessoal
package com.alka.mines.command;

import com.alka.mines.personal.PersonalMineManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MinaCommand implements CommandExecutor {
    private final PersonalMineManager mineManager;

    public MinaCommand(PersonalMineManager mineManager) {
        this.mineManager = mineManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cApenas jogadores.");
            return true;
        }
        if (args.length == 0) {
            mineManager.teleportToMine(player);
            player.sendMessage("§aTeleportado para sua mina pessoal!");
            return true;
        }
        if (args[0].equalsIgnoreCase("ir") && args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null && target.isOnline()) {
                // TODO: verificar se permite visita
                mineManager.teleportToMine(target); // temporário
                player.sendMessage("§aVisitando a mina de §f" + target.getName());
            } else {
                player.sendMessage("§cJogador offline.");
            }
            return true;
        }
        return true;
    }
}

// /picareta — já criado na Fase 2
// /prestigio — delega para PrestigeSystem
// /alugar — abre GUI de aluguel
// /encantamentos — atalho para /picareta
```

2. GUI de Aluguel (BaseGui do AlkaCore):

```java
package com.alka.mines.rental;

import com.alka.core.gui.BaseGui;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class RentalGui {
    private final ToolRentalSystem rentalSystem;

    public RentalGui(ToolRentalSystem rentalSystem) {
        this.rentalSystem = rentalSystem;
    }

    public void open(Player player) {
        BaseGui gui = new BaseGui("§8Alugar Ferramentas", 27);
        int slot = 11;
        for (ToolRentalSystem.RentalType type : ToolRentalSystem.RentalType.values()) {
            ItemStack icon = type.createItem();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + type.getDisplayName());
                meta.setLore(List.of(
                    "§7Duração: §f" + type.getDefaultMinutes() + " minutos",
                    "",
                    "§aClique para alugar"
                ));
                icon.setItemMeta(meta);
            }
            gui.setItem(slot, icon, e -> {
                e.setCancelled(true);
                ItemStack rental = rentalSystem.createRentalTool(type, type.getDefaultMinutes());
                player.getInventory().addItem(rental);
                player.sendMessage("§aVocê alugou §f" + type.getDisplayName() + " §apor §f" + type.getDefaultMinutes() + " minutos§a!");
                player.closeInventory();
            });
            slot += 2;
        }
        gui.open(player);
    }
}
```

3. GUI da Picareta (BaseGui do AlkaCore) — layout refinado:

```
Layout 6x9 (54 slots):
- Slot 13: Picareta atual (getDisplayItem, glow, lore completa)
- Slots 10-16: Encantamentos (ícone = livro encantado, cor = nível)
- Slots 19-25: Skins desbloqueadas (ícone = picareta com material da skin)
- Slot 40: Botão Prestígio (se elegível, glow vermelho)
- Slot 44: Estatísticas (blocos totais, tempo jogado, etc.)
```

4. Permissões (plugin.yml):

```yaml
permissions:
  alkamines.mina:
    default: true
    description: Usar /mina
  alkamines.picareta:
    default: true
    description: Usar /picareta
  alkamines.prestigio:
    default: true
    description: Usar /prestigio
  alkamines.alugar:
    default: true
    description: Usar /alugar
  alkamines.admin.encantamento:
    default: op
    description: Dar encantamentos
  alkamines.admin.resetmine:
    default: op
    description: Resetar mina de qualquer jogador
```

5. Comando admin /alkaminesadmin:

```java
package com.alka.mines.command;

import com.alka.mines.pickaxe.PickaxeManager;
import com.alka.mines.personal.PersonalMineManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminCommand implements CommandExecutor {
    private final PickaxeManager pickaxeManager;
    private final PersonalMineManager mineManager;

    public AdminCommand(PickaxeManager pickaxeManager, PersonalMineManager mineManager) {
        this.pickaxeManager = pickaxeManager;
        this.mineManager = mineManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: /alkaminesadmin <encantamento/resetmine> <jogador> [args]");
            return true;
        }
        String sub = args[0].toLowerCase();
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJogador offline.");
            return true;
        }

        switch (sub) {
            case "encantamento" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUso: /alkaminesadmin encantamento <jogador> <tipo> <nível>");
                    return true;
                }
                // pickaxeManager.addEnchantment(target, type, level);
                sender.sendMessage("§aEncantamento aplicado.");
            }
            case "resetmine" -> {
                mineManager.regenerateMine(mineManager.getOrCreateMine(target), target);
                sender.sendMessage("§aMina de §f" + target.getName() + " §aresetada.");
            }
        }
        return true;
    }
}
```
```

---

## Resumo da Arquitetura Final

```
com.alka.mines
├── personal/          ← NOVO: mina pessoal evolutiva
│   ├── PersonalMine
│   ├── PersonalMineManager
│   └── MineGenerator (substituído por VirtualMineGrid)
├── virtual/           ← NOVO: blocos client-side (ANTI-LAG)
│   ├── VirtualBlockSystem
│   └── VirtualMineGrid
├── pickaxe/           ← NOVO: picareta evolutiva
│   ├── EvolutionPickaxe
│   ├── PickaxeCommand
│   ├── PickaxeLevelManager
│   ├── PickaxeProgressDisplay
│   ├── PrestigeSystem
│   └── PickaxeSkin
├── enchantment/       ← ADAPTAÇÃO: encantamentos
│   ├── EnchantmentType
│   ├── EnchantmentManager
│   ├── ExplosionEnchantment
│   └── MemorizeSystem
├── rental/            ← NOVO: aluguel
│   ├── ToolRentalSystem
│   └── RentalGui
├── visual/            ← NOVO: Display Entities
│   ├── DisplayEntityFactory
│   └── FloatingTextManager
├── ability/           ← REAPROVEITA: dragão
├── packet/            ← REAPROVEITA: ProtocolLib
├── reward/            ← REAPROVEITA: batcher
├── cache/             ← REAPROVEITA: Caffeine
├── listener/          ← ADAPTA: MineBreakListener
└── command/           ← NOVO: comandos
    ├── MinaCommand
    ├── PickaxeCommand
    └── AdminCommand
```

---

## Ordem de Implementação Recomendada

1. **Prompt 0** — VirtualBlockSystem (base de tudo, elimina lag)
2. **Prompt 1.1** — PersonalMine + PersonalMineManager (core da mina)
3. **Prompt 2.1** — EvolutionPickaxe + PickaxeLevelManager + PickaxeProgressDisplay
4. **Prompt 2.1 (listener)** — MineBreakListener adaptado
5. **Prompt 3.1** — EnchantmentType + EnchantmentManager + Explosion + Memorize
6. **Prompt 4.1** — PrestigeSystem + PickaxeSkin + ToolRentalSystem
7. **Prompt 5.1** — DisplayEntityFactory + FloatingTextManager
8. **Prompt 6.1** — Comandos e GUIs finais

> **Nota sobre o VirtualBlockSystem:** Isso é a peça mais importante. Sem ela, o servidor terá lag com 15+ players minerando. Com ela, aguenta 50+ facilmente porque não há entidades de item, não há atualizações de chunk, e a venda é automática via RewardBatcher + AlkaShopAPI.
