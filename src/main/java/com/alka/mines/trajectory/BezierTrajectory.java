package com.alka.mines.trajectory;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;

/** Curva de Bezier cubica (4 pontos de controle) - so matematica, roda em qualquer thread. */
public class BezierTrajectory {

    private final List<Vector> controlPoints;

    public BezierTrajectory(List<Vector> controlPoints) {
        if (controlPoints == null || controlPoints.size() != 4) {
            throw new IllegalArgumentException("BezierTrajectory precisa de exatamente 4 pontos (cubica).");
        }
        this.controlPoints = List.copyOf(controlPoints);
    }

    public BezierTrajectory(Vector... controlPoints) {
        this(Arrays.asList(controlPoints));
    }

    public Vector calculate(double t) {
        t = clamp(t);
        double u = 1 - t;
        double u2 = u * u;
        double u3 = u2 * u;
        double t2 = t * t;
        double t3 = t2 * t;
        Vector p0 = controlPoints.get(0);
        Vector p1 = controlPoints.get(1);
        Vector p2 = controlPoints.get(2);
        Vector p3 = controlPoints.get(3);
        return new Vector(
                u3 * p0.getX() + 3 * u2 * t * p1.getX() + 3 * u * t2 * p2.getX() + t3 * p3.getX(),
                u3 * p0.getY() + 3 * u2 * t * p1.getY() + 3 * u * t2 * p2.getY() + t3 * p3.getY(),
                u3 * p0.getZ() + 3 * u2 * t * p1.getZ() + 3 * u * t2 * p2.getZ() + t3 * p3.getZ()
        );
    }

    public Vector getTangent(double t) {
        t = clamp(t);
        double u = 1 - t;
        double u2 = u * u;
        double t2 = t * t;
        Vector p0 = controlPoints.get(0);
        Vector p1 = controlPoints.get(1);
        Vector p2 = controlPoints.get(2);
        Vector p3 = controlPoints.get(3);
        Vector d01 = p1.clone().subtract(p0).multiply(3 * u2);
        Vector d12 = p2.clone().subtract(p1).multiply(6 * u * t);
        Vector d23 = p3.clone().subtract(p2).multiply(3 * t2);
        return d01.add(d12).add(d23);
    }

    public static float[] getYawPitch(Vector direction) {
        Location tmp = new Location(null, 0, 0, 0);
        tmp.setDirection(direction);
        return new float[]{tmp.getYaw(), tmp.getPitch()};
    }

    private static double clamp(double t) {
        return Math.max(0.0, Math.min(1.0, t));
    }
}
