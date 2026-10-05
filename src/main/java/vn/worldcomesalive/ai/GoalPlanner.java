package vn.worldcomesalive.ai;
import vn.worldcomesalive.data.WorldContent.Action;
import java.util.*;

/** Cost-directed STRIPS search over immutable facts; independent of Minecraft. */
public final class GoalPlanner {
    private record Node(Set<String> facts,List<String> plan,int cost) {}
    public static List<String> plan(Set<String> initial,String goal,List<Action> actions){
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingInt(Node::cost));open.add(new Node(Set.copyOf(initial),List.of(),0));Map<Set<String>,Integer> visited=new HashMap<>();
        for(int expansions=0;!open.isEmpty()&&expansions<256;expansions++){
            Node n=open.remove();if(n.facts.contains(goal))return n.plan;if(visited.getOrDefault(n.facts,Integer.MAX_VALUE)<=n.cost)continue;visited.put(n.facts,n.cost);
            for(Action a:actions)if(n.facts.containsAll(a.requires())&&!n.facts.containsAll(a.adds())){Set<String> next=new TreeSet<>(n.facts);next.addAll(a.adds());List<String> p=new ArrayList<>(n.plan);p.add(a.id());open.add(new Node(Set.copyOf(next),List.copyOf(p),n.cost+a.cost()));}
        }return List.of();
    }
    private GoalPlanner(){}
}
