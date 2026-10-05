package vn.worldcomesalive.ai;
import java.util.*;

/** Due time controls eligibility; priority controls execution. Urgent events bypass background backlog. */
public final class CognitiveQueue {
    public record Wake(UUID npc,long due,long version,int priority) {}
    private final PriorityQueue<Wake> future=new PriorityQueue<>(Comparator.comparingLong(Wake::due));
    private final PriorityQueue<Wake> ready=new PriorityQueue<>(Comparator.comparingInt(Wake::priority).thenComparingLong(Wake::due));
    public void add(Wake wake,long now){if(wake.due<=now)ready.add(wake);else future.add(wake);}
    public boolean hasReady(long now){for(int i=0;i<256&&!future.isEmpty()&&future.peek().due<=now;i++)ready.add(future.remove());return !ready.isEmpty();}
    public Wake remove(){return ready.remove();}
    public int size(){return ready.size()+future.size();}
}
