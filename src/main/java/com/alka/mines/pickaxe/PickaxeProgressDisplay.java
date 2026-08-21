package com.alka.mines.pickaxe;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/** Mostra o progresso da picareta na action bar e o titulo de nivel up. */
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
        if (n >= 1_000_000) {
            return String.format("%.2fM", n / 1_000_000.0);
        }
        if (n >= 1_000) {
            return String.format("%.1fK", n / 1_000.0);
        }
        return String.valueOf(n);
    }
}
