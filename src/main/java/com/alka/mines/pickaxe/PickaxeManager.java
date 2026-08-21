package com.alka.mines.pickaxe;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Gerenciador item-centric: o estado da picareta evolutiva (nivel, blocos, prestigio,
 * skins, encantamentos) vive NO ITEM (PDC). Se o jogador perder/dropar o item, perde tudo;
 * /picareta entrega uma nova do zero. Nenhum estado e guardado por jogador aqui.
 */
public class PickaxeManager {

    /** Picareta da mao do jogador (ou null se nao estiver segurando a evolutiva). */
    public EvolutionPickaxe getFromHand(Player player) {
        return EvolutionPickaxe.fromItem(player.getInventory().getItemInMainHand());
    }

    /** Picareta nova do zero (nivel 1, sem encantamentos). */
    public ItemStack getFreshItem() {
        return new EvolutionPickaxe().getDisplayItem();
    }

    /** Reescreve o estado atual da picareta no item na mao (preserva outras tags). */
    public void saveToHeld(Player player, EvolutionPickaxe pick) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (EvolutionPickaxe.isEvolutionPickaxe(hand)) {
            pick.applyTo(hand);
            player.getInventory().setItemInMainHand(hand);
        }
    }

    public Set<String> getUnlockedSkins(Player player) {
        EvolutionPickaxe pick = getFromHand(player);
        return pick != null ? pick.getUnlockedSkins() : Set.of("DEFAULT");
    }

    public double getMoneyMultiplier(Player player) {
        EvolutionPickaxe pick = getFromHand(player);
        return pick != null ? pick.getMoneyMultiplier() : 1.0;
    }
}
