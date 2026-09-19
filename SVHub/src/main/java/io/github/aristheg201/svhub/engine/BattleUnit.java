package io.github.aristheg201.svhub.engine;

import java.util.*;

/** Runtime combat state. Cosmetic identity is retained as a definition reference. */
public final class BattleUnit {
    public final String id, owner;
    public String definitionId, summoner, target;
    public final int team;
    public int cell;
    public double hp, mana, shield;
    public long attackReadyAt, castReadyAt, deadAt = -1;
    public int casts;
    public double damageDone, healingDone;
    public final EnumMap<Stat, Double> stats = new EnumMap<>(Stat.class);
    public final Set<String> traits = new LinkedHashSet<>(), tags = new LinkedHashSet<>();
    public final List<String> items = new ArrayList<>();
    public final Map<String, Integer> stacks = new LinkedHashMap<>();
    public final Map<String, StatusState> statuses = new LinkedHashMap<>();
    public final Map<String, Modifier> modifiers = new LinkedHashMap<>();
    public final List<TriggerDefinition> triggers = new ArrayList<>();

    public BattleUnit(String id, String owner, int team, String definitionId, int cell, Map<Stat, Double> stats) {
        this.id = Objects.requireNonNull(id); this.owner = Objects.requireNonNull(owner); this.team = team;
        this.definitionId = Objects.requireNonNull(definitionId); this.cell = cell; this.stats.putAll(stats);
        if (stat(Stat.MAX_HP) <= 0) throw new IllegalArgumentException("Unit maximum HP must be positive");
        hp = stat(Stat.MAX_HP);
    }
    public boolean alive() { return hp > 0; }
    public double stat(Stat stat) {
        double flat=stats.getOrDefault(stat,0.0), multiplier=1;
        for(var modifier:modifiers.values()) if(modifier.stat()==stat){flat+=modifier.flat();multiplier+=modifier.multiplier();}
        return flat*multiplier;
    }
    public record Modifier(Stat stat,double flat,double multiplier,long expiresAt) {}
    public boolean hasStatus(String id) { return statuses.containsKey(id); }
    public double healthFraction() { return hp / Math.max(1, stat(Stat.MAX_HP)); }
    public record StatusState(String id, String source, long expiresAt, int stacks, double intensity,
                              long nextTickAt, long intervalMs, List<EffectDefinition> periodic) {
        public StatusState { periodic = periodic == null ? List.of() : List.copyOf(periodic); }
    }
    public Snapshot snapshot() {
        return new Snapshot(id, owner, team, definitionId, cell, Map.copyOf(stats), hp, mana, shield,
            summoner, target, attackReadyAt, castReadyAt, deadAt, casts, damageDone, healingDone,
            Set.copyOf(traits), Set.copyOf(tags), List.copyOf(items), Map.copyOf(stacks), Map.copyOf(statuses), Map.copyOf(modifiers), List.copyOf(triggers));
    }
    public static BattleUnit restore(Snapshot s) {
        BattleUnit u = new BattleUnit(s.id, s.owner, s.team, s.definitionId, s.cell, s.stats);
        u.hp=s.hp; u.mana=s.mana; u.shield=s.shield; u.summoner=s.summoner; u.target=s.target;
        u.attackReadyAt=s.attackReadyAt; u.castReadyAt=s.castReadyAt; u.deadAt=s.deadAt; u.casts=s.casts;
        u.damageDone=s.damageDone; u.healingDone=s.healingDone; u.traits.addAll(s.traits); u.tags.addAll(s.tags);
        u.items.addAll(s.items); u.stacks.putAll(s.stacks); u.statuses.putAll(s.statuses); u.modifiers.putAll(s.modifiers); u.triggers.addAll(s.triggers);
        return u;
    }
    public record Snapshot(String id, String owner, int team, String definitionId, int cell, Map<Stat, Double> stats,
                           double hp, double mana, double shield, String summoner, String target,
                           long attackReadyAt, long castReadyAt, long deadAt, int casts, double damageDone, double healingDone,
                           Set<String> traits, Set<String> tags, List<String> items, Map<String,Integer> stacks,
                           Map<String,StatusState> statuses, Map<String,Modifier> modifiers, List<TriggerDefinition> triggers) {}
}
