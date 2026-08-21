package com.alka.mines.trajectory;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/** Fabrica de trajetorias de Bezier para o dragao (circulo sobre a mina pessoal). */
public final class TrajectoryFactory {

    private TrajectoryFactory() {
    }

    public static BezierTrajectory createCircleAround(Location center, double radius, double height) {
        Vector p0 = center.clone().add(new Vector(radius, height, 0)).toVector();
        Vector p1 = center.clone().add(new Vector(0, height, radius)).toVector();
        Vector p2 = center.clone().add(new Vector(-radius, height, 0)).toVector();
        Vector p3 = center.clone().add(new Vector(0, height, -radius)).toVector();
        return new BezierTrajectory(p0, p1, p2, p3);
    }
}
