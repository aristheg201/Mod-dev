package vn.svframe.mythicmobsfabric.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SkillContext {
    private final String trigger;
    private final UUID caster;
    private UUID triggerEntity;
    private Vec3 origin;
    private final Map<String, String> parameters;
    private final Map<String, Object> variables;
    private final Map<String, Object> metadata;
    private List<UUID> entityTargets;
    private List<Vec3> locationTargets;
    private float power;
    private boolean async;
    private boolean executeAfterDeath;
    private final AtomicBoolean terminated;

    public SkillContext(String trigger, UUID caster, UUID triggerEntity, Vec3 origin, float power,
                        Map<String, String> parameters, Map<String, Object> variables) {
        this(trigger, caster, triggerEntity, origin, power, parameters, variables,
                new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(), false, false, new AtomicBoolean());
    }

    private SkillContext(String trigger, UUID caster, UUID triggerEntity, Vec3 origin, float power,
                         Map<String, String> parameters, Map<String, Object> variables,
                         Map<String, Object> metadata, List<UUID> entityTargets, List<Vec3> locationTargets,
                         boolean async, boolean executeAfterDeath, AtomicBoolean terminated) {
        this.trigger = trigger == null ? "API" : trigger;
        this.caster = caster;
        this.triggerEntity = triggerEntity;
        this.origin = origin == null ? new Vec3(0, 0, 0) : origin;
        this.power = power;
        this.parameters = new LinkedHashMap<>(parameters == null ? Map.of() : parameters);
        this.variables = new LinkedHashMap<>(variables == null ? Map.of() : variables);
        this.metadata = metadata;
        this.entityTargets = entityTargets;
        this.locationTargets = locationTargets;
        this.async = async;
        this.executeAfterDeath = executeAfterDeath;
        this.terminated = terminated;
    }

    public String trigger() { return trigger; }
    public UUID caster() { return caster; }
    public UUID triggerEntity() { return triggerEntity; }
    public void triggerEntity(UUID value) { triggerEntity = value; }
    public Vec3 origin() { return origin; }
    public void origin(Vec3 value) { origin = value; }
    public float power() { return power; }
    public void power(float value) { power = value; }
    public Map<String, String> parameters() { return parameters; }
    public Map<String, Object> variables() { return variables; }
    public Map<String, Object> metadata() { return metadata; }
    public boolean async() { return async; }
    public void async(boolean value) { async = value; }
    public boolean executeAfterDeath() { return executeAfterDeath; }
    public void executeAfterDeath(boolean value) { executeAfterDeath = value; }
    public boolean terminated() { return terminated.get(); }
    public void terminate() { terminated.set(true); }

    public List<UUID> entityTargets() { return List.copyOf(entityTargets); }
    public void entityTargets(Collection<UUID> values) { entityTargets = new ArrayList<>(values == null ? List.of() : values); }
    public void entityTarget(UUID value) { entityTargets = value == null ? new ArrayList<>() : new ArrayList<>(List.of(value)); }
    public List<Vec3> locationTargets() { return List.copyOf(locationTargets); }
    public void locationTargets(Collection<Vec3> values) { locationTargets = new ArrayList<>(values == null ? List.of() : values); }
    public void locationTarget(Vec3 value) { locationTargets = value == null ? new ArrayList<>() : new ArrayList<>(List.of(value)); }

    public SkillContext deepClone() {
        return new SkillContext(trigger, caster, triggerEntity, origin, power, parameters, variables,
                new LinkedHashMap<>(metadata), new ArrayList<>(entityTargets), new ArrayList<>(locationTargets),
                async, executeAfterDeath, terminated);
    }

    public SkillContext deeperClone() {
        Map<String, Object> vars = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : variables.entrySet()) vars.put(entry.getKey(), copyValue(entry.getValue()));
        Map<String, Object> meta = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) meta.put(entry.getKey(), copyValue(entry.getValue()));
        return new SkillContext(trigger, caster, triggerEntity, origin, power, new LinkedHashMap<>(parameters), vars,
                meta, new ArrayList<>(entityTargets), new ArrayList<>(locationTargets), async, executeAfterDeath, terminated);
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) return new LinkedHashMap<>(map);
        if (value instanceof List<?> list) return new ArrayList<>(list);
        return value;
    }
}
