package com.alka.mines.command;

import com.alka.mines.personal.PersonalMineManager;
import com.alka.mines.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /minapessoal - teleporta o jogador para a sua mina pessoal (evolutiva). */
public class PersonalMineCommand implements CommandExecutor {

    private final PersonalMineManager mineManager;

    public PersonalMineCommand(PersonalMineManager mineManager) {
        this.mineManager = mineManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores.");
            return true;
        }
        mineManager.teleportToMine(player);
        ChatUtil.send(player, "<green>Teleportado para a sua mina pessoal!");
        return true;
    }
}
