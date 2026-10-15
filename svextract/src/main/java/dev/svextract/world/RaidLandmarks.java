package dev.svextract.world;

import java.util.List;

/** Immutable, data-shaped raid landmark metadata. Coordinates are slot-relative and bounded. */
public final class RaidLandmarks {
    private RaidLandmarks() {}

    public record Landmark(String map, String id, int x, int z, int radius, boolean highRisk) {
        public Landmark {
            if (!List.of("redline_station", "blackglass_industrial").contains(map))
                throw new IllegalArgumentException("Unsupported landmark map: " + map);
            if (id == null || !id.matches("[a-z_]{3,40}"))
                throw new IllegalArgumentException("Invalid landmark id");
            int boundX = map.equals("redline_station") ? 35 : 51;
            int boundZ = map.equals("redline_station") ? 27 : 51;
            if (Math.abs(x) > boundX || Math.abs(z) > boundZ || radius < 3 || radius > 18)
                throw new IllegalArgumentException("Landmark outside instance or invalid radius: " + id);
        }
        public boolean contains(double localX, double localZ) {
            double dx = localX - x, dz = localZ - z;
            return dx * dx + dz * dz <= (double) radius * radius;
        }
    }

    private static final List<Landmark> BLACKGLASS = List.of(
        new Landmark("blackglass_industrial", "security_checkpoint", 21, -39, 10, true),
        new Landmark("blackglass_industrial", "quarantine_clinic", -38, 13, 10, false),
        new Landmark("blackglass_industrial", "signal_relay", -41, -33, 9, true),
        new Landmark("blackglass_industrial", "research_warehouse", 22, 17, 12, true),
        new Landmark("blackglass_industrial", "industrial_foundry", -22, -33, 13, true),
        new Landmark("blackglass_industrial", "freight_depot", 0, 39, 13, false),
        new Landmark("blackglass_industrial", "power_substation", 41, 17, 8, true),
        new Landmark("blackglass_industrial", "service_tunnel", -39, -3, 9, true),
        new Landmark("blackglass_industrial", "evacuation_yard", -19, -12, 8, false)
    );
    private static final List<Landmark> REDLINE = List.of(
        new Landmark("redline_station", "platform_west", -24, 0, 10, false),
        new Landmark("redline_station", "signal_control", -12, 8, 7, true),
        new Landmark("redline_station", "derailed_train", 17, 16, 9, true),
        new Landmark("redline_station", "maintenance_bay", 25, -20, 7, false),
        new Landmark("redline_station", "evacuation_platform", -19, -12, 9, false)
    );
    public static List<Landmark> forMap(String map) {
        return switch (map) {
            case "blackglass_industrial" -> BLACKGLASS;
            case "redline_station" -> REDLINE;
            default -> List.of();
        };
    }
}
