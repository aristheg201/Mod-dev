package vn.svframe.svrelationships.integration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class IntegrationRegistry {
    private final Map<String, IntegrationDescriptor> descriptors = new ConcurrentHashMap<>();

    public void put(IntegrationDescriptor descriptor) {
        descriptors.put(descriptor.id(), descriptor);
    }

    public Optional<IntegrationDescriptor> get(String id) {
        return Optional.ofNullable(descriptors.get(id));
    }

    public List<IntegrationDescriptor> snapshot() {
        var values = new ArrayList<>(descriptors.values());
        values.sort(Comparator.comparing(IntegrationDescriptor::id));
        return List.copyOf(values);
    }
}
