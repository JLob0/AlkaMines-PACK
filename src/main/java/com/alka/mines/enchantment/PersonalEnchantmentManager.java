package com.alka.mines.enchantment;

import com.alka.mines.ability.PersonalDragonAbility;
import com.alka.mines.pickaxe.EvolutionPickaxe;
import com.alka.mines.pickaxe.PickaxeManager;
import com.alka.mines.virtual.VirtualBlockSystem;
import com.alka.mines.virtual.VirtualMineGrid;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Avalia, a cada bloco virtual quebrado na mina pessoal, se algum encantamento da picareta
 * evolutiva proca (chance + cooldown) e executa o efeito (Explosao, Keyfinder, Velocidade,
 * Memorizando, Fortuna+ e o Dragao).
 */
public class PersonalEnchantmentManager {

    private final VirtualBlockSystem virtualBlockSystem;
    private final ExplosionEnchantment explosionHandler;
    private final MemorizeSystem memorizeSystem;
    private final PersonalDragonAbility dragonAbility;
    private final Map<UUID, Map<EnchantmentType, Long>> cooldowns = new HashMap<>();
    private final Random random = new Random();

    public PersonalEnchantmentManager(VirtualBlockSystem virtualBlockSystem,
                                      ExplosionEnchantment explosionHandler, MemorizeSystem memorizeSystem,
                                      PersonalDragonAbility dragonAbility) {
        this.virtualBlockSystem = virtualBlockSystem;
        this.explosionHandler = explosionHandler;
        this.memorizeSystem = memorizeSystem;
        this.dragonAbility = dragonAbility;
    }

    public void tryProc(Player player, EvolutionPickaxe pick, Material brokenMaterial,
                        Location brokenLoc, VirtualMineGrid grid) {
        UUID uuid = player.getUniqueId();
        Map<EnchantmentType, Long> playerCooldowns = cooldowns.computeIfAbsent(uuid, k -> new EnumMap<>(EnchantmentType.class));
        long now = System.currentTimeMillis();

        for (Map.Entry<EnchantmentType, Integer> entry : pick.getEnchantments().entrySet()) {
            EnchantmentType type = entry.getKey();
            int level = entry.getValue();

            Long lastUse = playerCooldowns.get(type);
            long cdMillis = type.getCooldownSeconds() * 1000L;
            if (lastUse != null && (now - lastUse) < cdMillis) {
                continue;
            }

            double chance = type.getChance(level);
            if (random.nextDouble() * 100.0 >= chance) {
                continue;
            }

            playerCooldowns.put(type, now);
            executeEffect(player, type, level, brokenMaterial, brokenLoc, grid);
        }

        if (pick.getEnchantments().containsKey(EnchantmentType.MEMORIZE)) {
            memorizeSystem.onBlockBreak(player, brokenMaterial);
        }
    }

    private void executeEffect(Player player, EnchantmentType type, int level,
                               Material brokenMaterial, Location brokenLoc, VirtualMineGrid grid) {
        switch (type) {
            case EXPLOSION -> explosionHandler.explode(player, brokenLoc, level, grid);
            case KEYFINDER -> {
                player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.TRIPWIRE_HOOK));
                player.sendMessage("§e§lKEY ENCONTRADA!");
            }
            case SPEED_MINING -> player.addPotionEffect(
                    new PotionEffect(PotionEffectType.HASTE, 200, 2, true, false));
            case FORTUNE_BOOST -> {
                // multiplicador de venda sera aplicado no RewardBatcher (fase posterior)
            }
            case DRAGON -> {
                if (dragonAbility != null) {
                    dragonAbility.activate(player);
                } else {
                    player.sendMessage("§cO Dragão ainda não está disponível.");
                }
            }
            case MEMORIZE -> {
                // tratado fora do loop (combo)
            }
        }
    }
}
