package com.alka.mines.listener;

import com.alka.mines.pickaxe.EvolutionPickaxe;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Protege a picareta evolutiva de descarte acidental: dropar exige confirmar 2x no chat
 * (5s). O estado vive NO ITEM - se realmente perder/dropar/morrer, perde tudo e o
 * /picareta entrega uma nova do zero (nao tem mais retencao por jogador).
 */
public class PickaxeProtectionListener implements Listener {

    private final Plugin plugin;
    private final Map<UUID, Long> pendingDropConfirm = new HashMap<>();

    public PickaxeProtectionListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack item = e.getItemDrop().getItemStack();
        if (!EvolutionPickaxe.isEvolutionPickaxe(item)) {
            return;
        }
        UUID uuid = e.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        Long pending = pendingDropConfirm.get(uuid);
        if (pending != null && now - pending < 5000) {
            pendingDropConfirm.remove(uuid);
            return; // confirmou - deixa dropar
        }
        e.setCancelled(true);
        pendingDropConfirm.put(uuid, now);
        e.getPlayer().sendMessage("§c§lCUIDADO! §7Drope a picareta novamente em 5s para confirmar o descarte.");
    }
}
