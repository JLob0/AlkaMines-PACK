package com.alka.mines.listener;

import com.alka.mines.personal.PersonalMine;
import com.alka.mines.personal.PersonalMineManager;
import com.alka.mines.pickaxe.EvolutionPickaxe;
import com.alka.mines.pickaxe.PickaxeLevelManager;
import com.alka.mines.pickaxe.PickaxeManager;
import com.alka.mines.pickaxe.PickaxeProgressDisplay;
import com.alka.mines.enchantment.PersonalEnchantmentManager;
import com.alka.mines.virtual.VirtualBlockSystem;
import com.alka.mines.virtual.VirtualMineGrid;
import com.alka.mines.visual.FloatingTextManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Intercepta o packet BLOCK_DIG (start digging) quando o jogador mira num bloco VIRTUAL
 * da mina pessoal. Como o bloco real e AIR, o Bukkit nunca dispararia BlockBreakEvent -
 * por isso a quebra precisa ser detectada no nivel do packet. Cancela o dig e chama o
 * {@link VirtualBlockSystem} (visual + venda automatica + respawn), e avanca a picareta
 * (nivel sobe conforme blocos minerados; a mina expande a cada nivel marcado).
 */
public class PersonalMineListener {

    private final Plugin plugin;
    private final PersonalMineManager mineManager;
    private final VirtualBlockSystem virtualBlockSystem;
    private final PickaxeManager pickaxeManager;
    private final PickaxeLevelManager levelManager;
    private final PickaxeProgressDisplay progressDisplay;
    private final PersonalEnchantmentManager enchantmentManager;
    private final FloatingTextManager floatingTextManager;
    private final String mineWorld;
    private final NamespacedKey rentalToolKey;
    private final NamespacedKey rentalExpiryKey;

    public PersonalMineListener(Plugin plugin, PersonalMineManager mineManager,
                                VirtualBlockSystem virtualBlockSystem, String mineWorld,
                                PickaxeManager pickaxeManager, PickaxeLevelManager levelManager,
                                PickaxeProgressDisplay progressDisplay,
                                PersonalEnchantmentManager enchantmentManager,
                                FloatingTextManager floatingTextManager) {
        this.plugin = plugin;
        this.mineManager = mineManager;
        this.virtualBlockSystem = virtualBlockSystem;
        this.mineWorld = mineWorld;
        this.pickaxeManager = pickaxeManager;
        this.levelManager = levelManager;
        this.progressDisplay = progressDisplay;
        this.enchantmentManager = enchantmentManager;
        this.floatingTextManager = floatingTextManager;
        this.rentalToolKey = new NamespacedKey(plugin, "rental_tool");
        this.rentalExpiryKey = new NamespacedKey(plugin, "rental_expiry");
    }

    public void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin,
                PacketType.Play.Client.BLOCK_DIG) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.isCancelled()) {
                    return;
                }
                handleDig(event);
            }
        });
    }

    private void handleDig(PacketEvent event) {
        // so inicio de quebra ("start destroying") importa.
        // BLOCK_DIG: campos na ordem [pos(0), direction(1), action(2), sequence(3)]
        // - o action (PlayerDigType) esta no indice 2, nao no 0.
        if (event.getPacket().getEnumModifier(EnumWrappers.PlayerDigType.class, 2).read(0)
                != EnumWrappers.PlayerDigType.START_DESTROY_BLOCK) {
            return;
        }
        if (!event.getPlayer().getWorld().getName().equals(mineWorld)) {
            return;
        }

        BlockPosition pos = event.getPacket().getBlockPositionModifier().read(0);
        Location loc = new Location(event.getPlayer().getWorld(), pos.getX(), pos.getY(), pos.getZ());

        VirtualMineGrid grid = mineManager.getGrid(event.getPlayer().getUniqueId());
        if (grid == null) {
            return;
        }
        PersonalMine mine = mineManager.getMine(event.getPlayer().getUniqueId());
        if (mine == null || !mine.isInside(loc)) {
            return;
        }

        // cancela o dig vanilla (o bloco real e AR, nao deve sumir nada) e processa virtual
        event.setCancelled(true);
        var player = event.getPlayer();
        // PacketListener roda fora da main thread - agenda o processamento sync
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            org.bukkit.Material brokenMaterial = grid.getBlockAt(
                    new com.alka.mines.virtual.BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) != null
                    ? grid.getBlockAt(new com.alka.mines.virtual.BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())).material()
                    : org.bukkit.Material.STONE;
            if (!virtualBlockSystem.handleBreak(player, loc, grid)) {
                return;
            }
            EvolutionPickaxe pick = pickaxeManager.getFromHand(player);
            advancePickaxe(player, mine);
            if (enchantmentManager != null && pick != null) {
                enchantmentManager.tryProc(player, pick, brokenMaterial, loc, grid);
            }
        });
    }

    /** Sobe o nivel da picareta conforme blocos minerados e expande a mina no nivel marcado. */
    private void advancePickaxe(org.bukkit.entity.Player player, PersonalMine mine) {
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
