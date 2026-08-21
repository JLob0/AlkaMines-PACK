package com.alka.mines.virtual;

import org.bukkit.Material;

/**
 * Bloco virtual (client-side) da mina pessoal. So dados: material que o jogador ve,
 * valor de venda e se e quebravel.
 */
public record VirtualBlock(Material material, double sellValue, boolean breakable) {
}
