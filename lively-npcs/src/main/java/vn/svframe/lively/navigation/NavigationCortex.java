package vn.svframe.lively.navigation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/** Immutable graph A* planner. World/block sampling is owned by the platform integration. */
public final class NavigationCortex {
    public record Node(long id, double x, double y, double z, double traversalCost, int flags) {
        public Node { traversalCost = Math.max(0.01D, Math.min(100D, traversalCost)); }
    }
    public record Edge(long to, double cost) { public Edge { cost = Math.max(0.01D, Math.min(1000D, cost)); } }
    public record Graph(long revision, Map<Long, Node> nodes, Map<Long, List<Edge>> edges) {
        public Graph {
            nodes = Map.copyOf(nodes);
            Map<Long, List<Edge>> copy = new HashMap<>();
            edges.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            edges = Map.copyOf(copy);
        }
    }
    public record Path(long graphRevision, List<Long> nodes, double cost, int visited) {
        public Path { nodes = List.copyOf(nodes); }
    }
    public record Budget(int maxVisited, long timeoutNanos) {
        public Budget {
            maxVisited = Math.max(32, Math.min(1_000_000, maxVisited));
            timeoutNanos = Math.max(100_000L, Math.min(50_000_000L, timeoutNanos));
        }
    }

    public Optional<Path> findPath(Graph graph, long start, long goal, Budget budget) {
        Objects.requireNonNull(graph); Objects.requireNonNull(budget);
        Node startNode = graph.nodes().get(start); Node goalNode = graph.nodes().get(goal);
        if (startNode == null || goalNode == null) return Optional.empty();
        if (start == goal) return Optional.of(new Path(graph.revision(), List.of(start), 0D, 1));

        long deadline = System.nanoTime() + budget.timeoutNanos();
        PriorityQueue<State> open = new PriorityQueue<>(Comparator.comparingDouble(State::f));
        Map<Long, Double> gScore = new HashMap<>();
        Map<Long, Long> cameFrom = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        gScore.put(start, 0D); open.add(new State(start, heuristic(startNode, goalNode)));
        int visited = 0;

        while (!open.isEmpty() && visited < budget.maxVisited() && System.nanoTime() <= deadline) {
            State current = open.poll();
            if (!closed.add(current.node())) continue;
            visited++;
            if (current.node() == goal) return Optional.of(reconstruct(graph.revision(), cameFrom, gScore, goal, visited));
            for (Edge edge : graph.edges().getOrDefault(current.node(), List.of())) {
                Node next = graph.nodes().get(edge.to());
                if (next == null || closed.contains(edge.to())) continue;
                double tentative = gScore.getOrDefault(current.node(), Double.POSITIVE_INFINITY)
                        + edge.cost() * next.traversalCost();
                if (tentative < gScore.getOrDefault(edge.to(), Double.POSITIVE_INFINITY)) {
                    cameFrom.put(edge.to(), current.node());
                    gScore.put(edge.to(), tentative);
                    open.add(new State(edge.to(), tentative + heuristic(next, goalNode)));
                }
            }
        }
        return Optional.empty();
    }

    private Path reconstruct(long revision, Map<Long, Long> cameFrom, Map<Long, Double> gScore, long goal, int visited) {
        ArrayList<Long> path = new ArrayList<>();
        long current = goal; path.add(current);
        while (cameFrom.containsKey(current)) { current = cameFrom.get(current); path.add(current); }
        java.util.Collections.reverse(path);
        return new Path(revision, path, gScore.getOrDefault(goal, 0D), visited);
    }

    private static double heuristic(Node a, Node b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    private record State(long node, double f) {}
}
