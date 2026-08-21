package com.alka.mines.listener;

import com.alka.mines.gui.PickaxeGui;
import com.alka.mines.pickaxe.EvolutionPickaxe;
import com.alka.mines.pickaxe.PickaxeManager;
import com.alka.mines.pickaxe.PrestigeSystem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

/** Clique direito na picareta evolutiva abre a GUI de encantamentos. */
public class PickaxeGuiListener implements Listener {

    private final PickaxeManager pickaxeManager;
    private final PrestigeSystem prestigeSystem;
    private final JavaPlugin plugin;

    public PickaxeGuiListener(PickaxeManager pickaxeManager, PrestigeSystem prestigeSystem, JavaPlugin plugin) {
        this.pickaxeManager = pickaxeManager;
        this.prestigeSystem = prestigeSystem;
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!EvolutionPickaxe.isEvolutionPickaxe(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);
        new PickaxeGui(plugin, player, pickaxeManager, prestigeSystem).open();
    }
}
