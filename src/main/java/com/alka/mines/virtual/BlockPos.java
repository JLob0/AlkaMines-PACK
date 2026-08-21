package com.alka.mines.virtual;

/**
 * Posicao de bloco imutavel usada como chave no {@link VirtualMineGrid}.
 */
public record BlockPos(int x, int y, int z) {
}
