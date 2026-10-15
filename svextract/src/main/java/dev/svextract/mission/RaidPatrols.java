package dev.svextract.mission;

import java.util.List;

/** Bounded, immutable waypoint circuits for the current small authored raid maps. */
public final class RaidPatrols {
    private RaidPatrols() {}

    public record Point(int x,int z) {
        double distanceSquared(double localX,double localZ) {
            double dx=x-localX,dz=z-localZ;
            return dx*dx+dz*dz;
        }
    }
    public record Route(String id,List<Point> points) {
        public Route {
            if(id==null||!id.matches("[a-z_]{3,40}")||points==null||points.size()<2||points.size()>12)
                throw new IllegalArgumentException("Invalid patrol route");
            points=List.copyOf(points);
            for(Point p:points)if(Math.abs(p.x())>50||Math.abs(p.z())>50)
                throw new IllegalArgumentException("Patrol route outside authored map bounds");
        }
    }
    private static Point p(int x,int z){return new Point(x,z);}
    private static final List<Route> BLACKGLASS=List.of(
        new Route("security_cordon",List.of(p(10,-31),p(12,-29),p(32,-30),p(41,-28))),
        new Route("foundry_perimeter",List.of(p(-8,-38),p(-8,-26),p(-10,-18),p(-27,-19))),
        new Route("central_avenue",List.of(p(-8,-8),p(8,-8),p(8,7),p(-8,7))),
        new Route("quarantine_watch",List.of(p(-28,4),p(-27,22),p(-34,25),p(-25,25))),
        new Route("laboratory_guard",List.of(p(9,9),p(9,25),p(39,29),p(39,8))),
        new Route("freight_patrol",List.of(p(-19,26),p(-8,26),p(8,26),p(40,29),p(40,42)))
    );
    private static final List<Route> REDLINE=List.of(
        new Route("western_tracks",List.of(p(-27,-20),p(-26,-6),p(-28,3),p(-27,22))),
        new Route("service_platform",List.of(p(10,-24),p(24,-23),p(30,-5),p(25,5))),
        new Route("research_perimeter",List.of(p(8,7),p(31,8),p(32,22),p(10,23))),
        new Route("central_concourse",List.of(p(-8,-18),p(8,-18),p(8,1),p(-8,1)))
    );
    public static List<Route> routes(String map){
        return switch(map){
            case "blackglass_industrial"->BLACKGLASS;
            case "redline_station"->REDLINE;
            default->List.of();
        };
    }
    public static List<Point> spawnPoints(String map){
        return routes(map).stream().flatMap(r->r.points().stream()).distinct().toList();
    }
    /** Next waypoint in the closest local patrol circuit. No world queries or allocations beyond the result. */
    public static Point next(String map,double localX,double localZ){
        Route best=null;int bestIndex=0;double distance=Double.POSITIVE_INFINITY;
        for(Route route:routes(map))for(int i=0;i<route.points().size();i++){
            double candidate=route.points().get(i).distanceSquared(localX,localZ);
            if(candidate<distance){distance=candidate;best=route;bestIndex=i;}
        }
        return best==null?null:best.points().get((bestIndex+1)%best.points().size());
    }
}
