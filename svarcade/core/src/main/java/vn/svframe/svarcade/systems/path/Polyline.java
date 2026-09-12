package vn.svframe.svarcade.systems.path;

import java.util.*;

/** Arc-length parameterized waypoint route. Lookup is logarithmic in waypoint count. */
public final class Polyline {
    private final List<Vec3> points;
    private final double[] cumulative;
    public Polyline(List<Vec3> points) {
        if (points.size() < 2 || points.size() > 4096) throw new IllegalArgumentException("Path requires 2..4096 points");
        this.points = List.copyOf(points); cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            double segment = points.get(i - 1).distance(points.get(i));
            double total = cumulative[i - 1] + segment;
            if (!Double.isFinite(total) || segment <= 0 || total <= cumulative[i - 1]) throw new IllegalArgumentException("Degenerate or overflowing path segment");
            cumulative[i] = total;
        }
    }
    public double length() { return cumulative[cumulative.length - 1]; }
    public List<Vec3> points() { return points; }
    public Vec3 at(double distance) {
        if (!Double.isFinite(distance) || distance < 0 || distance > length()) throw new IllegalArgumentException("Path distance outside route");
        int index = Arrays.binarySearch(cumulative, distance);
        if (index >= 0) return points.get(index);
        int end = -index - 1;
        double fraction = (distance - cumulative[end - 1]) / (cumulative[end] - cumulative[end - 1]);
        return points.get(end - 1).interpolate(points.get(end), fraction);
    }
}
