package com.alka.mines.reward;

import com.alka.mines.hook.AlkaDropHook;
import com.alka.mines.hook.AlkaShopHook;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Buffer de drops que centraliza a decisao de auto-venda/entrega do AlkaMines, extraido
 * do MineBreakListener pra ser reutilizavel por outros pacotes (ex: habilidade do dragao,
 * que quebra varios blocos por tick e so quer vender/entregar em lote no fim).
 *
 * Logica identica ao antigo giveOrSellDrops: para cada drop no buffer, pergunta ao
 * AlkaShopHook se a auto-venda esta ativa pro jogador E o material e vendavel; se sim,
 * vende (agrupando por moeda em soldTotals) e entrega o resto via AlkaDropHook
 * (respeitando o toggle de coleta do jogador) ou solta no chao quando o AlkaDrop nao
 * esta presente. Chama notifyAutoSell no fim se houve venda.
 *
 * A mina NAO sabe preco nem o que e gold - isso e decisao exclusiva do AlkaShop. Este
 * batcher so intermedeia.
 */
public class RewardBatcher {

    private final Player player;
    private final Supplier<Optional<AlkaShopHook>> shopHookSupplier;
    private final Supplier<Optional<AlkaDropHook>> dropHookSupplier;
    private final List<ItemStack> buffer = new ArrayList<>();

    public RewardBatcher(Player player, Supplier<Optional<AlkaShopHook>> shopHookSupplier,
                          Supplier<Optional<AlkaDropHook>> dropHookSupplier) {
        this.player = player;
        this.shopHookSupplier = shopHookSupplier;
        this.dropHookSupplier = dropHookSupplier;
    }

    /** Adiciona um drop ao buffer interno para venda/entrega em lote no proximo flush. */
    public void addDrop(ItemStack drop) {
        buffer.add(drop);
    }

    /**
     * Processa todos os drops acumulados (auto-venda via AlkaShopHook, entrega do resto
     * via AlkaDropHook/dropItemNaturally) e limpa o buffer.
     */
    public void flush(Location dropLocation) {
        if (buffer.isEmpty()) {
            return;
        }

        Map<String, Double> soldTotals = new LinkedHashMap<>();
        List<ItemStack> toDeliver = new ArrayList<>();
        Optional<AlkaShopHook> shopHook = shopHookSupplier.get();
        Optional<AlkaDropHook> dropHook = dropHookSupplier.get();

        for (ItemStack drop : buffer) {
            boolean autoSell = shopHook.isPresent() && shopHook.get().isAutoSellActive(player, drop.getType());
            if (autoSell && shopHook.get().isSellable(drop.getType())) {
                Map<String, Double> totals = shopHook.get().sell(player, drop);
                for (Map.Entry<String, Double> entry : totals.entrySet()) {
                    soldTotals.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
                if (!totals.isEmpty()) {
                    continue;
                }
            }
            toDeliver.add(drop);
        }

        // Entrega o que nao foi vendido - via AlkaDrop se presente (respeita o toggle de
        // coleta do jogador: inventario ou chao). Sem AlkaDrop, solta no chao como o
        // vanilla faz ao minerar.
        if (!toDeliver.isEmpty()) {
            if (dropHook.isPresent()) {
                dropHook.get().deliverDrops(player, toDeliver, dropLocation);
            } else {
                for (ItemStack drop : toDeliver) {
                    player.getWorld().dropItemNaturally(dropLocation, drop);
                }
            }
        }

        if (!soldTotals.isEmpty()) {
            shopHook.ifPresent(hook -> hook.notifyAutoSell(player, soldTotals));
        }

        buffer.clear();
    }
}
