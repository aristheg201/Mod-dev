package vn.svframe.svarcade.config;

import java.util.*;
import java.util.function.Function;

/** Stable topological compilation; missing references and cycles fail before runtime. */
public final class DependencyGraph {
    private DependencyGraph() { }

    public static <T> List<T> order(List<T> values, Function<T, Id> identity,
                                    Function<T, Set<Id>> dependencies) {
        if (values.size() > 4096) throw new ConfigException("Dependency graph exceeds 4096 nodes");
        Map<Id, Integer> indices = new LinkedHashMap<>();
        List<Id> ids = new ArrayList<>();
        List<Set<Id>> edges = new ArrayList<>();
        for (T value : values) {
            Id id = Objects.requireNonNull(identity.apply(value));
            if (indices.putIfAbsent(id, ids.size()) != null) throw new ConfigException("Duplicate dependency node: " + id);
            ids.add(id); edges.add(Set.copyOf(dependencies.apply(value)));
        }
        int[] indegree = new int[values.size()];
        List<List<Integer>> dependents = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) dependents.add(new ArrayList<>());
        for (int i = 0; i < values.size(); i++) {
            for (Id required : edges.get(i)) {
                Integer dependency = indices.get(required);
                if (dependency == null) throw new ConfigException(ids.get(i) + " requires missing dependency " + required);
                indegree[i]++; dependents.get(dependency).add(i);
            }
        }
        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int i = 0; i < values.size(); i++) if (indegree[i] == 0) ready.add(i);
        List<T> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            int index = ready.remove(); result.add(values.get(index));
            for (int dependent : dependents.get(index)) if (--indegree[dependent] == 0) ready.add(dependent);
        }
        if (result.size() != values.size()) {
            List<Id> blocked = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) if (indegree[i] > 0) blocked.add(ids.get(i));
            throw new ConfigException("Cyclic dependencies block: " + blocked);
        }
        return List.copyOf(result);
    }
}
