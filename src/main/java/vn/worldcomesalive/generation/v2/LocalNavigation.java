package vn.worldcomesalive.generation.v2;

import vn.worldcomesalive.model.LivingWorld.*;
import java.util.*;

/** Persistent circulation grid compiled from the final furnishing pass. Used at every simulation LOD. */
public final class LocalNavigation {
    private record Cell(int x,int y,int z) {
        static Cell of(Pos p){return new Cell((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()));}
    }
    public static List<Pos> route(Building building,Pos start,Pos target){
        if(building.walkable.isEmpty())return new ArrayList<>(List.of(start,target)); // V1 saves retain their existing navigation.
        Map<Cell,Pos> cells=new LinkedHashMap<>();for(Pos p:building.walkable)cells.put(Cell.of(p),p);
        Pos source=building.walkable.stream().min(Comparator.comparingDouble(p->p.distance(start))).orElseThrow();
        Pos destination=building.walkable.stream().min(Comparator.comparingDouble(p->p.distance(target))).orElseThrow();
        Cell from=Cell.of(source),to=Cell.of(destination);Map<Cell,Cell> previous=new HashMap<>();Set<Cell> seen=new HashSet<>();ArrayDeque<Cell> queue=new ArrayDeque<>();queue.add(from);seen.add(from);
        while(!queue.isEmpty()){Cell p=queue.remove();if(p.equals(to))break;for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy:new int[]{0,1,-1}){Cell next=new Cell(p.x+d[0],p.y+dy,p.z+d[1]);if(cells.containsKey(next)&&seen.add(next)){previous.put(next,p);queue.add(next);}}}
        if(!seen.contains(to))throw new IllegalArgumentException("Disconnected furnished circulation in "+building.id);
        List<Pos> result=new ArrayList<>();for(Cell p=to;p!=null;p=previous.get(p))result.add(cells.get(p));Collections.reverse(result);result.addFirst(start);
        // Furniture is approached from its reachable adjacent cell; sitting/sleeping is a separate physical action.
        return result;
    }
    private LocalNavigation(){}
}
