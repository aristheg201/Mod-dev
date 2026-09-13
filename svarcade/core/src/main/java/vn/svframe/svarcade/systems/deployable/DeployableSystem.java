package vn.svframe.svarcade.systems.deployable;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.currency.*;
import vn.svframe.svarcade.systems.deployable.DeployableAccess.*;

/** Canonical-source anti-duplication, ownership and match-local deployment transactions. */
public final class DeployableSystem implements SessionSystem, DeployableAccess {
    public static final Id ID = Id.of("svarcade:deployables");
    public record Config(Id currency, int maxTotal, double coordinateLimit, Map<Id, Profile> profiles) {
        public Config {
            profiles = Map.copyOf(profiles); Objects.requireNonNull(currency);
            if (maxTotal < 1 || maxTotal > 100_000 || !Double.isFinite(coordinateLimit) || coordinateLimit <= 0 || coordinateLimit > 30_000_000 || profiles.isEmpty() || profiles.size() > 4096)
                throw new ConfigException("Deployable definition limits");
        }
        public static Config parse(Node n) {
            n.only("currency", "max_total", "coordinate_limit", "profiles");
            Map<Id, Profile> profiles = new LinkedHashMap<>(); Node values = n.node("profiles");
            for (String raw : values.values().keySet()) {
                Node p = values.node(raw); p.only("deploy_cost", "move_cost", "recall_refund", "max_per_actor", "tags");
                Set<Id> tags = new LinkedHashSet<>(); if (p.has("tags")) p.strings("tags").forEach(tag -> tags.add(Id.of(tag)));
                Id id = Id.of(raw); Profile profile = new Profile(id, p.integer("deploy_cost", 0, Long.MAX_VALUE), p.integer("move_cost", 0, Long.MAX_VALUE),
                        p.integer("recall_refund", 0, Long.MAX_VALUE), (int) p.integer("max_per_actor", 1, 100_000), tags);
                if (profiles.putIfAbsent(id, profile) != null) throw new ConfigException("Duplicate deployable profile: " + id);
            }
            return new Config(Id.of(n.string("currency")), (int) n.integer("max_total", 1, 100_000),
                    Numbers.decimal(n, "coordinate_limit", Double.MIN_NORMAL, 30_000_000), profiles);
        }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<Id> dependencies() { return Set.of(CurrencySystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires() { return Set.of(CurrencySystem.ACCESS); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            DeployableSystem system = new DeployableSystem(Config.parse(config), session.services().require(CurrencySystem.ACCESS), session);
            session.services().provide(ACCESS, system); return system;
        }
    }
    private final Config config;
    private final CurrencyAccess currency;
    private final GenericSession session;
    private final ThreadGuard thread;
    private final NavigableMap<Long, Deployment> deployments = new TreeMap<>();
    private final Map<String, Long> sources = new HashMap<>();
    private long nextId = 1, revision;
    private boolean active, closed;
    private DeployableSystem(Config config, CurrencyAccess currency, GenericSession session) {
        this.config = config; this.currency = currency; this.session = session; thread = session.thread();
        for (Participant p : session.participants().values()) if (p.kind() != Participant.Kind.SPECTATOR) currency.balance(p.id(), config.currency());
    }
    private void requireActive() { thread.check(); if (!active || closed) throw new IllegalStateException("Deployables inactive"); }
    private Participant owner(UUID actor) {
        Participant participant = session.participants().get(actor);
        if (participant == null || participant.kind() == Participant.Kind.SPECTATOR) throw new IllegalArgumentException("Invalid deployable owner");
        return participant;
    }
    private void validatePoint(Point point) {
        if (Math.abs(point.x()) > config.coordinateLimit() || Math.abs(point.y()) > config.coordinateLimit() || Math.abs(point.z()) > config.coordinateLimit())
            throw new IllegalArgumentException("Deployable position outside configured bounds");
    }
    private static String source(String source) {
        Objects.requireNonNull(source); String value = source.trim();
        if (value.isEmpty() || value.length() > 256 || !value.equals(source)) throw new IllegalArgumentException("Invalid canonical source id"); return value;
    }
    @Override public void start() { thread.check(); if (active || closed) throw new IllegalStateException("Deployables already initialized"); active = true; }
    @Override public Optional<Deployment> deployment(long id) { requireActive(); return Optional.ofNullable(deployments.get(id)); }
    @Override public Optional<Deployment> bySource(String sourceId) { requireActive(); Long id = sources.get(source(sourceId)); return id == null ? Optional.empty() : Optional.of(deployments.get(id)); }
    @Override public List<Deployment> owned(UUID actor) { requireActive(); owner(actor); return deployments.values().stream().filter(d -> d.owner().equals(actor)).toList(); }
    @Override public Map<Id, Profile> profiles() { requireActive(); return config.profiles(); }
    @Override public long revision() { requireActive(); return revision; }
    @Override public StateChange prepareDeploy(UUID actor, String sourceId, Id profileId, Point point) {
        requireActive(); owner(actor); validatePoint(point); sourceId = source(sourceId); Profile profile = config.profiles().get(profileId);
        if (profile == null || sources.containsKey(sourceId) || deployments.size() >= config.maxTotal() || ownedCount(actor, profileId) >= profile.maxPerActor() || nextId == Long.MAX_VALUE || revision == Long.MAX_VALUE)
            throw new IllegalStateException("Deployment unavailable");
        long id = nextId, expected = revision; String canonical = sourceId;
        CurrencyAccess.Change payment = currency.prepare(List.of(new CurrencyAccess.Delta(actor, config.currency(), -profile.deployCost())));
        StateChange local = new StateChange() {
            private int state;
            @Override public void apply() {
                requireActive(); if (state != 0 || revision != expected || nextId != id || sources.containsKey(canonical)) throw new IllegalStateException("Stale deploy transaction");
                Deployment deployment = new Deployment(id, actor, canonical, profileId, point, 0);
                deployments.put(id, deployment); sources.put(canonical, id); nextId++; revision++; state = 1;
            }
            @Override public void rollback() {
                requireActive(); Deployment current = deployments.get(id);
                if (state != 1 || revision != expected + 1 || current == null || current.version() != 0) throw new IllegalStateException("Deploy rollback conflict");
                deployments.remove(id); sources.remove(canonical); nextId = id; revision = expected; state = 2;
            }
        };
        return new CompositeChange(thread, List.of(payment, local));
    }
    @Override public StateChange prepareMove(UUID actor, long id, Point point) {
        requireActive(); owner(actor); validatePoint(point); Deployment before = requireOwned(actor, id); Profile profile = config.profiles().get(before.profile()); long expected = revision;
        CurrencyAccess.Change payment = currency.prepare(List.of(new CurrencyAccess.Delta(actor, config.currency(), -profile.moveCost())));
        Deployment after = new Deployment(before.id(), before.owner(), before.sourceId(), before.profile(), point, Math.addExact(before.version(), 1));
        return new CompositeChange(thread, List.of(payment, replacement(expected, before, after)));
    }
    @Override public StateChange prepareRecall(UUID actor, long id) {
        requireActive(); owner(actor); Deployment before = requireOwned(actor, id); Profile profile = config.profiles().get(before.profile()); long expected = revision;
        CurrencyAccess.Change refund = currency.prepare(List.of(new CurrencyAccess.Delta(actor, config.currency(), profile.recallRefund())));
        StateChange local = new StateChange() {
            private int state;
            @Override public void apply() {
                requireActive(); if (state != 0 || revision != expected || deployments.get(id) != before) throw new IllegalStateException("Stale recall transaction");
                deployments.remove(id); sources.remove(before.sourceId()); revision++; state = 1;
            }
            @Override public void rollback() {
                requireActive(); if (state != 1 || revision != expected + 1 || deployments.containsKey(id) || sources.containsKey(before.sourceId())) throw new IllegalStateException("Recall rollback conflict");
                deployments.put(id, before); sources.put(before.sourceId(), id); revision = expected; state = 2;
            }
        };
        return new CompositeChange(thread, List.of(refund, local));
    }
    private StateChange replacement(long expected, Deployment before, Deployment after) {
        return new StateChange() {
            private int state;
            @Override public void apply() {
                requireActive(); if (state != 0 || revision != expected || deployments.get(before.id()) != before) throw new IllegalStateException("Stale deployment transaction");
                deployments.put(before.id(), after); revision++; state = 1;
            }
            @Override public void rollback() {
                requireActive(); if (state != 1 || revision != expected + 1 || deployments.get(before.id()) != after) throw new IllegalStateException("Deployment rollback conflict");
                deployments.put(before.id(), before); revision = expected; state = 2;
            }
        };
    }
    private Deployment requireOwned(UUID actor, long id) { Deployment d = deployments.get(id); if (d == null || !d.owner().equals(actor)) throw new IllegalArgumentException("Deployment ownership"); return d; }
    private long ownedCount(UUID actor, Id profile) { return deployments.values().stream().filter(d -> d.owner().equals(actor) && d.profile().equals(profile)).count(); }
    @Override public void tick(long tick) { requireActive(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() {
        requireActive(); List<Object> values = new ArrayList<>();
        for (Deployment d : deployments.values()) values.add(Map.of("id", d.id(), "owner", d.owner().toString(), "source", d.sourceId(), "profile", d.profile().toString(),
                "x", d.position().x(), "y", d.position().y(), "z", d.position().z(), "version", d.version()));
        return Values.map(Map.of("revision", revision, "next_id", nextId, "deployments", values));
    }
    @Override public void restore(int schema, Map<String, Object> state) {
        thread.check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid deployable restore");
        Node n = new Node(state, "deployable-state"); n.only("revision", "next_id", "deployments"); long restoredRevision = n.integer("revision", 0, Long.MAX_VALUE - 1);
        long restoredNext = n.integer("next_id", 1, Long.MAX_VALUE); NavigableMap<Long, Deployment> restored = new TreeMap<>(); Map<String, Long> restoredSources = new HashMap<>();
        for (Node d : n.nodes("deployments")) {
            d.only("id", "owner", "source", "profile", "x", "y", "z", "version"); long id = d.integer("id", 1, Long.MAX_VALUE - 1); UUID owner = UUID.fromString(d.string("owner")); owner(owner);
            String source = source(d.string("source")); Id profile = Id.of(d.string("profile")); if (!config.profiles().containsKey(profile)) throw new ConfigException("Unknown restored deployable profile");
            Point point = new Point(Numbers.decimal(d, "x", -config.coordinateLimit(), config.coordinateLimit()), Numbers.decimal(d, "y", -config.coordinateLimit(), config.coordinateLimit()), Numbers.decimal(d, "z", -config.coordinateLimit(), config.coordinateLimit()));
            Deployment value = new Deployment(id, owner, source, profile, point, d.integer("version", 0, Long.MAX_VALUE));
            if (restored.putIfAbsent(id, value) != null || restoredSources.putIfAbsent(source, id) != null) throw new ConfigException("Duplicate restored deployment identity");
        }
        if (restored.size() > config.maxTotal() || !restored.isEmpty() && restored.lastKey() >= restoredNext) throw new ConfigException("Invalid restored deployment bounds");
        for (Profile profile : config.profiles().values()) for (Participant participant : session.participants().values()) if (participant.kind() != Participant.Kind.SPECTATOR) {
            long count = restored.values().stream().filter(d -> d.owner().equals(participant.id()) && d.profile().equals(profile.id())).count(); if (count > profile.maxPerActor()) throw new ConfigException("Restored deployable cap exceeded");
        }
        deployments.putAll(restored); sources.putAll(restoredSources); revision = restoredRevision; nextId = restoredNext; active = true;
    }
    @Override public void close() { thread.check(); active = false; closed = true; deployments.clear(); sources.clear(); }
}
