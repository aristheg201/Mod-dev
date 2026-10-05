package vn.worldcomesalive.world;
import vn.worldcomesalive.model.LivingWorld.*;
import java.util.*;

/** Cached hierarchical route sections; local navigation handles the final block segment. */
public final class RoadRoutes {
    private final Map<String,List<Pos>> cache=new HashMap<>();
    public List<Pos> route(Settlement s,Pos start,Building destination){
        Pos entrance=destination.point(Marker.ENTRANCE);String key=s.id+":"+Math.round(start.x()/4)+":"+Math.round(start.z()/4)+":"+destination.id;
        List<Pos> tail=cache.computeIfAbsent(key,k->{
            List<Pos> points=new ArrayList<>();Building source=s.buildings.values().stream().filter(b->b.contains(start)).findFirst().orElse(null);
            if(source!=null){Pos exit=source.point(Marker.ENTRANCE);points.add(exit);points.add(new Pos(exit.x(),exit.y(),exit.z()+2));points.add(new Pos(source.origin.x()-2+.5,exit.y(),exit.z()+2));points.add(new Pos(source.origin.x()-2+.5,exit.y(),s.center.z()+.5));}
            else points.add(new Pos(start.x(),s.center.y()+1,s.center.z()+.5));
            double side=destination.origin.x()-2+.5;
            points.add(new Pos(side,s.center.y()+1,s.center.z()+.5));points.add(new Pos(side,entrance.y(),entrance.z()+2));points.add(new Pos(entrance.x(),entrance.y(),entrance.z()+2));points.add(entrance);return List.copyOf(points);
        });
        List<Pos> result=new ArrayList<>();result.add(start);result.addAll(tail);return result;
    }
    public void invalidate(){cache.clear();}
    public int cached(){return cache.size();}
}
