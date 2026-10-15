package dev.svextract.mission;

import java.util.*;

/** Events come from verified server interactions, never objective-complete client packets. */
public final class ObjectiveGraph {
    public enum Kind { REPAIR, INVESTIGATION, RECOVERY, EXTRACTION }
    public record Node(String id, Kind kind, List<String> prerequisites, int x, int y, int z) {
        public Node {
            Objects.requireNonNull(id); Objects.requireNonNull(kind);
            prerequisites = List.copyOf(prerequisites);
            if (!id.matches("[a-z_]{1,48}")) throw new IllegalArgumentException("Invalid objective id: " + id);
            if (Math.abs(x)>64 || y<1 || y>128 || Math.abs(z)>64)
                throw new IllegalArgumentException("Objective outside map bounds: " + id);
        }
    }
    private final Map<String,Node> nodes;
    private final Set<String> completed = new LinkedHashSet<>();
    public ObjectiveGraph(List<Node> definitions) {
        if (definitions.isEmpty() || definitions.size() > 32) throw new IllegalArgumentException("Need 1–32 objectives");
        Map<String,Node> index = new LinkedHashMap<>();
        for (Node n : definitions) if (index.putIfAbsent(n.id,n)!=null) throw new IllegalArgumentException("Duplicate " + n.id);
        for (Node n : definitions) for (String dep : n.prerequisites) {
            if (!index.containsKey(dep)) throw new IllegalArgumentException("Missing prerequisite " + dep);
        }
        for (String id : index.keySet()) visit(id,index,new HashSet<>(),new HashSet<>());
        nodes = Collections.unmodifiableMap(index);
    }
    private static void visit(String id,Map<String,Node> index,Set<String> visiting,Set<String> done) {
        if (done.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("Objective cycle at " + id);
        for (String dep : index.get(id).prerequisites) visit(dep,index,visiting,done);
        visiting.remove(id); done.add(id);
    }
    public boolean complete(String id) {
        Node n=nodes.get(id);
        return n!=null && !completed.contains(id) && completed.containsAll(n.prerequisites) && completed.add(id);
    }
    public boolean available(String id) {
        Node n=nodes.get(id);
        return n!=null && !completed.contains(id) && completed.containsAll(n.prerequisites);
    }
    public boolean completed(String id) { return completed.contains(id); }
    public Collection<Node> nodes() { return nodes.values(); }
    public Set<String> completedIds() { return Set.copyOf(completed); }
    public String next() { return nodes.keySet().stream().filter(this::available).findFirst().orElse("complete"); }
    /** Independent contracts for Operations. Extraction is always optional and immediately possible. */
    public static List<Node> blackglassOperations() {
        return List.of(
            new Node("power",Kind.REPAIR,List.of(),-12,65,8),
            new Node("research_train",Kind.INVESTIGATION,List.of(),16,65,16),
            new Node("data_core",Kind.RECOVERY,List.of("research_train"),24,65,16),
            new Node("security_manifest",Kind.INVESTIGATION,List.of(),18,66,-42),
            new Node("relay_intelligence",Kind.INVESTIGATION,List.of(),-44,65,-38),
            new Node("clinic_records",Kind.INVESTIGATION,List.of(),-38,65,11),
            new Node("substation_logs",Kind.INVESTIGATION,List.of(),47,66,11),
            new Node("evacuation",Kind.EXTRACTION,List.of(),-19,65,-12)
        );
    }
    public static List<Node> deadSignal() {
        return List.of(new Node("power",Kind.REPAIR,List.of(),-12,65,8),
                new Node("research_train",Kind.INVESTIGATION,List.of("power"),16,65,16),
                new Node("data_core",Kind.RECOVERY,List.of("research_train"),24,65,16),
                new Node("evacuation",Kind.EXTRACTION,List.of("data_core"),-19,65,-12));
    }
}
