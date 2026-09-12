package vn.svframe.svarcade.systems.path;

import java.util.List;
import vn.svframe.svarcade.config.ConfigException;

/** Immutable geometry value; never a live world/entity reference. */
public record Vec3(double x, double y, double z) {
    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Nonfinite coordinate");
    }
    public double distance(Vec3 other) { return Math.hypot(Math.hypot(x - other.x, y - other.y), z - other.z); }
    public Vec3 interpolate(Vec3 other, double fraction) {
        if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1) throw new IllegalArgumentException("Interpolation fraction");
        return new Vec3(x * (1 - fraction) + other.x * fraction, y * (1 - fraction) + other.y * fraction, z * (1 - fraction) + other.z * fraction);
    }
    public static Vec3 parse(Object value) {
        if (!(value instanceof List<?> p) || p.size() != 3) throw new ConfigException("Point requires exactly three coordinates");
        double[] coordinates = new double[3];
        for (int i = 0; i < 3; i++) {
            if (!(p.get(i) instanceof Number n)) throw new ConfigException("Point coordinate must be numeric");
            coordinates[i] = n.doubleValue();
        }
        return new Vec3(coordinates[0], coordinates[1], coordinates[2]);
    }
}
