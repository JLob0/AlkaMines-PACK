package com.alka.mines.rental;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Aluguel por tempo da picareta que o jogador TEM na mao: clona o item atual, marca com
 * expiracao (PDC) e remove ferramentas alugadas expiradas do inventario (task periodico).
 * Nao e por modelo - e "empresta a sua picareta por X minutos".
 */
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

    /** Cria a versao alugada de um item (a picareta na mao) por X minutos. */
    public ItemStack makeRental(ItemStack base, long durationMinutes) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        long expiry = System.currentTimeMillis() + (durationMinutes * 60 * 1000);
        meta.getPersistentDataContainer().set(rentalKey, PersistentDataType.STRING, "rental");
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
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(rentalKey, PersistentDataType.STRING);
    }

    public boolean isExpired(ItemStack item) {
        if (!isRentalTool(item)) {
            return false;
        }
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
}
