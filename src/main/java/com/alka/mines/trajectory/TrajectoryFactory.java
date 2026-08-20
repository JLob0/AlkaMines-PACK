package com.alka.mines.trajectory;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Fabrica de trajetorias de Bezier para o dragao. Todas geram os 4 pontos de controle
 * de uma curva cubica a partir de parametros espaciais simples (centro, raio, altura).
 */
public final class TrajectoryFactory {

    private TrajectoryFactory() {
    }

    /** Circulo ao redor de um centro (em altura fixa) - voo circular do dragao. */
    public static BezierTrajectory createCircleAround(Location center, double radius, double height, int points) {
        Vector p0 = center.clone().add(new Vector(radius, height, 0)).toVector();
        Vector p1 = center.clone().add(new Vector(0, height, radius)).toVector();
        Vector p2 = center.clone().add(new Vector(-radius, height, 0)).toVector();
        Vector p3 = center.clone().add(new Vector(0, height, -radius)).toVector();
        return new BezierTrajectory(p0, p1, p2, p3);
    }

    /** Curva de mergulho de um ponto inicial ate um alvo (sobe, depois desce). */
    public static BezierTrajectory createDiveAttack(Location start, Location target, double height) {
        Vector p0 = start.toVector();
        Vector p1 = start.clone().add(0, height * 0.5, 0).toVector();
        Vector p2 = target.clone().add(0, height * 0.3, 0).toVector();
        Vector p3 = target.toVector();
        return new BezierTrajectory(p0, p1, p2, p3);
    }

    /**
     * Curva em "S" que passa sobre a mina em dois arcos (sweep). Os 4 pontos de controle
     * formam uma onda dupla sobre a regiao: sobe na entrada, desce no meio, sobe na saida.
     */
    public static BezierTrajectory createSwoopOverMine(Location center, double radius, double height) {
        Vector p0 = center.clone().add(new Vector(-radius, 0, 0)).toVector();
        Vector p1 = center.clone().add(new Vector(-radius * 0.33, height, radius * 0.5)).toVector();
        Vector p2 = center.clone().add(new Vector(radius * 0.33, height * 0.4, -radius * 0.5)).toVector();
        Vector p3 = center.clone().add(new Vector(radius, height * 0.8, 0)).toVector();
        return new BezierTrajectory(p0, p1, p2, p3);
    }
}
