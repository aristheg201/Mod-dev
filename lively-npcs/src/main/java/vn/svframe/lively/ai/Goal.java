package vn.svframe.lively.ai;

import java.util.Map;
import java.util.Objects;

public record Goal(String type, double priority, Map<String, String> context) {
    public Goal {
        Objects.requireNonNull(type);
        priority = Math.max(0D, Math.min(1D, priority));
        context = Map.copyOf(context);
    }
}
