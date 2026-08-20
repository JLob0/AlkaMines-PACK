package com.alka.mines.trajectory;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Curva de Bezier cubica (4 pontos de controle) com calculo em double pra precisao.
 * So matematica pura - nao toca em mundo nem em pacotes; isso e responsabilidade do
 * caller (o calculo pode rodar em thread async sem problema).
 */
public class BezierTrajectory {

    private final List<Vector> controlPoints;

    public BezierTrajectory(List<Vector> controlPoints) {
        if (controlPoints == null || controlPoints.size() != 4) {
            throw new IllegalArgumentException("BezierTrajectory precisa de exatamente 4 pontos de controle (cubica).");
        }
        this.controlPoints = List.copyOf(controlPoints);
    }

    public BezierTrajectory(Vector... controlPoints) {
        this(Arrays.asList(controlPoints));
    }

    /**
     * Ponto na curva em t: B(t) = (1-t)^3*P0 + 3(1-t)^2*t*P1 + 3(1-t)*t^2*P2 + t^3*P3.
     * t deve estar em [0,1].
     */
    public Vector calculate(double t) {
        t = clampT(t);
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

    /**
     * Tangente (derivada) da curva em t:
     * B'(t) = 3(1-t)^2(P1-P0) + 6(1-t)t(P2-P1) + 3t^2(P3-P2).
     */
    public Vector getTangent(double t) {
        t = clampT(t);
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

    /** Converte um vetor de direcao em {yaw, pitch} (via Location#setDirection). */
    public static float[] getYawPitch(Vector direction) {
        Location tmp = new Location(null, 0, 0, 0);
        tmp.setDirection(direction);
        return new float[]{tmp.getYaw(), tmp.getPitch()};
    }

    /** Aproximacao do comprimento da curva somando as distancias entre N pontos amostrados. */
    public double getLengthApproximation(int samples) {
        if (samples <= 1) {
            samples = 100;
        }
        double length = 0;
        Vector previous = calculate(0);
        for (int i = 1; i <= samples; i++) {
            Vector next = calculate((double) i / samples);
            length += previous.distance(next);
            previous = next;
        }
        return length;
    }

    public List<Vector> getControlPoints() {
        return new ArrayList<>(controlPoints);
    }

    private static double clampT(double t) {
        return Math.max(0.0, Math.min(1.0, t));
    }
}
