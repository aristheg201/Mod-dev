package vn.worldcomesalive.domestic;
import java.util.*;
import vn.worldcomesalive.model.LivingWorld.Pos;

/** Shared persistent lifecycle for visible and abstract meals; no per-fork entities. */
public final class DomesticState {
    public Map<String,Container> containers=new LinkedHashMap<>();
    public static final class Container {public UUID owner;public Map<String,Integer> items=new LinkedHashMap<>();}
    public Map<String,Pos> cleanup=new LinkedHashMap<>();
    public Map<UUID,Meal> meals=new LinkedHashMap<>();
    public Map<UUID,Batch> batches=new LinkedHashMap<>();
    public static final class Meal {
        public UUID id,actor,server;
        public String settlement,building,food="",drink="",state="Prepared";
        public Pos table;
        public long due,created;
        public int quality=1;
        public boolean player,foodConsumed,drinkConsumed;
    }
    public static final class Batch {
        public UUID id;
        public String settlement,building,recipe,output;
        public int count,quality;
        public long due;
    }
}
