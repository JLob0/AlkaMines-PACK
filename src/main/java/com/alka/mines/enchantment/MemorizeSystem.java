package com.alka.mines.enchantment;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Encantamento Memorizando: sequencia de 5 materiais a minerar; acertar em sequencia gera
 * combo com bonus de venda; errar reseta. Recebe o MATERIAL virtual (o bloco real e AR).
 */
public class MemorizeSystem {

    private final Map<UUID, List<Material>> sequences = new HashMap<>();
    private final Map<UUID, Integer> sequenceIndex = new HashMap<>();
    private final Map<UUID, Integer> comboCount = new HashMap<>();
    private final Random random = new Random();

    public void onBlockBreak(Player player, Material broken) {
        UUID uuid = player.getUniqueId();

        List<Material> seq = sequences.get(uuid);
        if (seq == null) {
            startSequence(player);
            return;
        }

        int idx = sequenceIndex.getOrDefault(uuid, 0);
        Material expected = seq.get(idx);

        if (broken == expected) {
            sequenceIndex.put(uuid, idx + 1);
            if (idx + 1 >= seq.size()) {
                int combo = comboCount.getOrDefault(uuid, 0) + 1;
                comboCount.put(uuid, combo);
                double bonus = combo * 1.5;
                player.sendMessage("§a§lCOMBO! §7Bônus: §6" + bonus + "x");
                sequences.remove(uuid);
                sequenceIndex.remove(uuid);
            }
        } else {
            player.sendMessage("§c§lERROU! §7Sequência resetada.");
            sequences.remove(uuid);
            sequenceIndex.remove(uuid);
            comboCount.put(uuid, 0);
        }
    }

    private void startSequence(Player player) {
        List<Material> seq = new ArrayList<>();
        Material[] options = {Material.STONE, Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE};
        for (int i = 0; i < 5; i++) {
            seq.add(options[random.nextInt(options.length)]);
        }
        sequences.put(player.getUniqueId(), seq);
        sequenceIndex.put(player.getUniqueId(), 0);
        player.sendMessage("§e§lMEMORIZE: §7" + seq);
    }
}
