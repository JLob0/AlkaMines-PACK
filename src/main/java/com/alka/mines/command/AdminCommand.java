package com.alka.mines.command;

import com.alka.mines.enchantment.EnchantmentType;
import com.alka.mines.personal.PersonalMineManager;
import com.alka.mines.pickaxe.PickaxeManager;
import com.alka.mines.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Comando admin da mina pessoal: /alkaminesadmin <encantamento|resetmine> <jogador> ...
 */
public class AdminCommand implements CommandExecutor {

    private final PickaxeManager pickaxeManager;
    private final PersonalMineManager mineManager;

    public AdminCommand(PickaxeManager pickaxeManager, PersonalMineManager mineManager) {
        this.pickaxeManager = pickaxeManager;
        this.mineManager = mineManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("alkamines.admin")) {
            ChatUtil.send(sender, "<red>Sem permissão.");
            return true;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "<red>Uso: <gray>/alkaminesadmin <encantamento|resetmine> <jogador> [tipo nível]");
            return true;
        }
        String sub = args[0].toLowerCase();
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            ChatUtil.send(sender, "<red>Jogador offline.");
            return true;
        }

        switch (sub) {
            case "encantamento" -> {
                if (args.length < 4) {
                    ChatUtil.send(sender, "<red>Uso: <gray>/alkaminesadmin encantamento <jogador> <tipo> <nível>");
                    return true;
                }
                EnchantmentType type;
                try {
                    type = EnchantmentType.valueOf(args[2].toUpperCase());
                } catch (IllegalArgumentException e) {
                    ChatUtil.send(sender, "<red>Tipos válidos: <gray>" + java.util.Arrays.toString(EnchantmentType.values()));
                    return true;
                }
                int level;
                try {
                    level = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    ChatUtil.send(sender, "<red>Nível inválido.");
                    return true;
                }
                com.alka.mines.pickaxe.EvolutionPickaxe pick = pickaxeManager.getFromHand(target);
                if (pick == null) {
                    ChatUtil.send(sender, "<red>" + target.getName() + " precisa estar com a picareta na mão.");
                    return true;
                }
                pick.addEnchantment(type, level);
                pickaxeManager.saveToHeld(target, pick);
                ChatUtil.send(sender, "<green>Encantamento <gold>" + type.getDisplayName()
                        + " <green>nível <white>" + level + " <green>aplicado em <white>" + target.getName());
            }
            case "resetmine" -> {
                mineManager.regenerateMine(mineManager.getOrCreateMine(target), target);
                ChatUtil.send(sender, "<green>Mina de <white>" + target.getName() + " <green>resetada.");
            }
            default -> ChatUtil.send(sender, "<red>Subcomando desconhecido. Use: <gray>encantamento|resetmine");
        }
        return true;
    }
}
