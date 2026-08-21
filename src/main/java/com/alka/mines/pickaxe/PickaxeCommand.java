package com.alka.mines.pickaxe;

import com.alka.mines.gui.PickaxeGui;
import com.alka.mines.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** /picareta - entrega a picareta evolutiva. Clique direito nela abre a GUI. */
public class PickaxeCommand implements CommandExecutor {

    private final PickaxeManager pickaxeManager;
    private final PrestigeSystem prestigeSystem;
    private final JavaPlugin plugin;

    public PickaxeCommand(PickaxeManager pickaxeManager, PrestigeSystem prestigeSystem, JavaPlugin plugin) {
        this.pickaxeManager = pickaxeManager;
        this.prestigeSystem = prestigeSystem;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
            new PickaxeGui(plugin, player, pickaxeManager, prestigeSystem).open();
            return true;
        }

        ItemStack pick = pickaxeManager.getFreshItem();
        // se a mao estiver vazia, coloca na mao; senao, tenta add no inventario
        if (player.getInventory().getItemInMainHand().getType().isAir()) {
            player.getInventory().setItemInMainHand(pick);
        } else {
            java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(pick);
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        ChatUtil.send(player, "<green>Picareta evolutiva entregue! <gray>Clique com <yellow>botão direito <gray>nela para abrir o menu.");
        return true;
    }
}
