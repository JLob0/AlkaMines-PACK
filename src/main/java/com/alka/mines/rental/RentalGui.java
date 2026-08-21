package com.alka.mines.rental;

import com.alka.mines.pickaxe.EvolutionPickaxe;
import com.alkacode.core.gui.BaseGui;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** GUI de aluguel: aluga a picareta que o jogador tem NA MAO, escolhendo o tempo. */
public class RentalGui extends BaseGui {

    private final ToolRentalSystem rentalSystem;

    public RentalGui(JavaPlugin plugin, Player player, ToolRentalSystem rentalSystem) {
        super(plugin, player, "Alugar Picareta", 3, "alkamines-rental");
        this.rentalSystem = rentalSystem;
    }

    @Override
    public void render() {
        fillBorder(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!EvolutionPickaxe.isEvolutionPickaxe(hand)) {
            setItem(13, infoItem("§cSegure sua picareta", List.of("§7Você precisa estar com a picareta", "§7evolutiva na mão para alugar.")),
                    e -> e.setCancelled(true));
            return;
        }

        // mostra a picareta que sera alugada
        setItem(13, infoItem("§ePicareta a alugar", List.of("§7Escolha o tempo abaixo")), e -> e.setCancelled(true));

        // tempos de aluguel (minutos)
        int slot = 21;
        for (long minutes : new long[]{15, 30, 60}) {
            ItemStack time = new ItemStack(Material.CLOCK);
            ItemMeta meta = time.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§6" + minutes + " minutos");
                meta.setLore(List.of("§aClique para alugar por " + minutes + " min"));
                time.setItemMeta(meta);
            }
            final long fm = minutes;
            setItem(slot, time, e -> {
                e.setCancelled(true);
                player.getInventory().setItemInMainHand(rentalSystem.makeRental(hand, fm));
                player.sendMessage("§aPicareta alugada por §f" + fm + " §aminutos!");
                player.closeInventory();
            });
            slot++;
        }
    }

    private ItemStack infoItem(String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
