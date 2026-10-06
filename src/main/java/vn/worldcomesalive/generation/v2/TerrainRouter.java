package vn.worldcomesalive.generation.v2;
import java.util.*;
import static vn.worldcomesalive.generation.v2.Spatial.*;

/**
 * Bounded A* over an immutable elevation graph.
 * Runtime terrain capture is one-block resolution, but settlement routing intentionally uses
 * a coarser planning grid so one candidate does not explore hundreds of thousands of cells per road.
 */
public final class TerrainRouter {
    private record Cell(int x,int z){}
    private record Visit(Cell cell,double cost,double estimate){}

    public static List<Point> route(Point from,Point to,TerrainSnapshot terrain,GenerationCatalog.RoadDef kind){
        return route(from,to,terrain,kind,List.of());
    }

    public static List<Point> route(Point from,Point to,TerrainSnapshot terrain,GenerationCatalog.RoadDef kind,List<Rect> obstacles){
        return tryRoute(from,to,terrain,kind,obstacles).orElseThrow(
            ()->new IllegalArgumentException("No navigable terrain route between "+from+" and "+to)
        );
    }

    public static Optional<List<Point>> tryRoute(Point from,Point to,TerrainSnapshot terrain,GenerationCatalog.RoadDef kind){
        return tryRoute(from,to,terrain,kind,List.of());
    }

    public static Optional<List<Point>> tryRoute(Point from,Point to,TerrainSnapshot terrain,GenerationCatalog.RoadDef kind,List<Rect> obstacles){
        final int grid=Math.max(3,terrain.step);
        Cell start=cell(from,terrain,grid),end=cell(to,terrain,grid);
        Map<Cell,Double> costs=new HashMap<>();
        Map<Cell,Cell> previous=new HashMap<>();
        PriorityQueue<Visit> open=new PriorityQueue<>(
            Comparator.comparingDouble(Visit::estimate)
                .thenComparingInt(v->v.cell.x)
                .thenComparingInt(v->v.cell.z)
        );
        costs.put(start,0.0);
        open.add(new Visit(start,0,from.distance(to)));
        int visited=0;
        int width=Math.max(1,((terrain.size-1)*terrain.step)/grid+1);
        int budget=Math.max(4096,width*width*3);

        while(!open.isEmpty()&&visited++<budget){
            Visit v=open.remove();
            if(v.cost>costs.getOrDefault(v.cell,Double.POSITIVE_INFINITY))continue;
            if(v.cell.equals(end)){
                List<Point> result=new ArrayList<>();
                Cell c=end;
                while(c!=null){result.add(point(c,terrain,grid));c=previous.get(c);}
                Collections.reverse(result);
                if(result.isEmpty())return Optional.empty();
                result.set(0,from);
                result.set(result.size()-1,to);
                return Optional.of(List.copyOf(result));
            }

            Point p=point(v.cell,terrain,grid);
            var current=terrain.at(p.x(),p.z());
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                if(dx==0&&dz==0)continue;
                Cell n=new Cell(v.cell.x+dx,v.cell.z+dz);
                Point q=point(n,terrain,grid);
                if(!terrain.contains(q,grid)||obstacles.stream().anyMatch(r->r.contains(q.x(),q.z())))continue;
                var sample=terrain.at(q.x(),q.z());
                if(sample.water())continue;

                double rise=Math.abs(sample.height()-current.height());
                // V2 supports stepped/terraced roads; reject cliffs, not ordinary hill grades.
                double maxRise=grid*1.75;
                if(rise>maxRise)continue;

                double diagonal=(dx!=0&&dz!=0)?1.41421356237:1.0;
                double cost=v.cost+grid*diagonal+rise*kind.slopeCost()+sample.forest()*2;
                if(cost<costs.getOrDefault(n,Double.POSITIVE_INFINITY)){
                    costs.put(n,cost);
                    previous.put(n,v.cell);
                    open.add(new Visit(n,cost,cost+q.distance(to)));
                }
            }
        }
        return Optional.empty();
    }

    private static Cell cell(Point p,TerrainSnapshot t,int grid){
        return new Cell((int)Math.round((p.x()-t.minX)/grid),(int)Math.round((p.z()-t.minZ)/grid));
    }
    private static Point point(Cell c,TerrainSnapshot t,int grid){
        return new Point(t.minX+c.x*grid,t.minZ+c.z*grid);
    }

    public static Point nearest(Point p,List<SettlementPlan.Road> roads){
        Point best=null;double distance=Double.POSITIVE_INFINITY;
        for(var road:roads)for(int i=1;i<road.points().size();i++){
            Point a=road.points().get(i-1),b=road.points().get(i);
            double dx=b.x()-a.x(),dz=b.z()-a.z(),length=dx*dx+dz*dz;
            double f=length==0?0:Math.max(0,Math.min(1,((p.x()-a.x())*dx+(p.z()-a.z())*dz)/length));
            Point q=a.lerp(b,f);
            if(q.distance(p)<distance){distance=q.distance(p);best=q;}
        }
        return Objects.requireNonNull(best);
    }

    public static boolean crosses(Rect r,SettlementPlan.Road road,int clearance){
        Rect area=r.expand(clearance);
        for(int i=1;i<road.points().size();i++){
            Point a=road.points().get(i-1),b=road.points().get(i);
            int steps=Math.max(1,(int)Math.ceil(a.distance(b)));
            for(int s=0;s<=steps;s++){
                Point p=a.lerp(b,s/(double)steps);
                if(area.contains(p.x(),p.z()))return true;
            }
        }
        return false;
    }

    private TerrainRouter(){}
}
