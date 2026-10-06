package vn.worldcomesalive.generation.v2;

import vn.worldcomesalive.model.LivingWorld.*;
import java.util.*;

/** Persistent circulation grid compiled from the final furnishing pass. Used at every simulation LOD. */
public final class LocalNavigation {
    private record Cell(int x,int y,int z) {
        static Cell of(Pos p){return new Cell((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()));}
    }
    private static final int[][] CARDINAL={{1,0},{-1,0},{0,1},{0,-1}};

    /**
     * Keep only the circulation component connected to the authored entrance.
     * Tiny pockets behind beds/furniture are not valid navigation cells merely because they are physically empty.
     */
    public static int retainEntranceComponent(Building building){
        if(building.walkable.isEmpty())return 0;
        Map<Cell,Pos> cells=index(building.walkable);
        Pos entrance=building.point(Marker.ENTRANCE);
        Pos source=building.walkable.stream().min(Comparator.comparingDouble(p->p.distance(entrance))).orElseThrow();
        Set<Cell> reachable=reachable(cells,Cell.of(source));
        int before=building.walkable.size();
        building.walkable.removeIf(p->!reachable.contains(Cell.of(p)));
        return before-building.walkable.size();
    }

    public static List<Pos> route(Building building,Pos start,Pos target){
        if(building.walkable.isEmpty())return new ArrayList<>(List.of(start,target)); // V1 saves retain their existing navigation.
        Map<Cell,Pos> cells=index(building.walkable);

        // The compiled set is entrance-connected. Choosing within it prevents wake-up positions on the
        // inaccessible side of a bed/bench from selecting a decorative dead pocket as the source.
        Pos source=building.walkable.stream().min(Comparator.comparingDouble(p->p.distance(start))).orElseThrow();
        Pos destination=building.walkable.stream().min(Comparator.comparingDouble(p->p.distance(target))).orElseThrow();
        Cell from=Cell.of(source),to=Cell.of(destination);
        Map<Cell,Cell> previous=new HashMap<>();
        Set<Cell> seen=new HashSet<>();
        ArrayDeque<Cell> queue=new ArrayDeque<>();
        queue.add(from);seen.add(from);

        while(!queue.isEmpty()){
            Cell p=queue.remove();
            for(int[] d:CARDINAL)for(int dy:new int[]{0,1,-1}){
                Cell next=new Cell(p.x+d[0],p.y+dy,p.z+d[1]);
                if(cells.containsKey(next)&&seen.add(next)){previous.put(next,p);queue.add(next);}
            }
        }

        if(!seen.contains(to)){
            var accessible=building.walkable.stream().filter(p->seen.contains(Cell.of(p))).min(Comparator.comparingDouble(p->p.distance(target))).orElseThrow();
            if(accessible.distance(target)>1.6)throw new IllegalArgumentException("Disconnected furnished circulation in "+building.id+" from="+start+" target="+target+" closestReachable="+accessible);
            to=Cell.of(accessible);
        }

        List<Pos> result=new ArrayList<>();
        for(Cell p=to;p!=null;p=previous.get(p))result.add(cells.get(p));
        Collections.reverse(result);
        // Preserve the logical/physical starting position, but immediately move onto the certified component.
        if(result.isEmpty()||result.getFirst().distance(start)>.05)result.addFirst(start);
        return result;
    }

    private static Map<Cell,Pos> index(Collection<Pos> walkable){
        Map<Cell,Pos> cells=new LinkedHashMap<>();
        for(Pos p:walkable)cells.put(Cell.of(p),p);
        return cells;
    }

    private static Set<Cell> reachable(Map<Cell,Pos> cells,Cell source){
        Set<Cell> seen=new HashSet<>();
        ArrayDeque<Cell> queue=new ArrayDeque<>();
        if(!cells.containsKey(source))return seen;
        queue.add(source);seen.add(source);
        while(!queue.isEmpty()){
            Cell p=queue.remove();
            for(int[] d:CARDINAL)for(int dy:new int[]{0,1,-1}){
                Cell next=new Cell(p.x+d[0],p.y+dy,p.z+d[1]);
                if(cells.containsKey(next)&&seen.add(next))queue.add(next);
            }
        }
        return seen;
    }

    private LocalNavigation(){}
}
