package vn.svframe.svarcade.systems.board;

import java.util.*;
import java.util.function.Function;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.board.MovementRules.Move;

/** Owns the logical board, compact move archive and effective-position repetition counts. */
public final class BoardSystem implements SessionSystem, BoardAccess {
    public static final Id ID = Id.of("svarcade:board");
    public static final SessionServices.Key<BoardAccess> ACCESS = new SessionServices.Key<>(ID, BoardAccess.class);
    public record Config(GridPosition initial, int historyLimit) {
        public Config { Objects.requireNonNull(initial); if (historyLimit < 1 || historyLimit > 32_768) throw new ConfigException("Board history limit"); }
        public static Config parse(Node n) { n.only("initial", "history_limit"); return new Config(GridPosition.decode(n.node("initial")), (int) n.integer("history_limit", 1, 32_768)); }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<Id> dependencies() { return Set.of(MovementSystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires() { return Set.of(MovementSystem.ACCESS); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            BoardSystem system = new BoardSystem(Config.parse(config), session.services().require(MovementSystem.ACCESS), session.thread(), actor -> session.participants().get(actor));
            session.services().provide(ACCESS, system); return system;
        }
    }
    private final Config config;
    private final MovementAccess movement;
    private final ThreadGuard thread;
    private final Function<UUID, Participant> participants;
    private final List<Entry> history = new ArrayList<>();
    private final Map<String, Integer> counts = new HashMap<>();
    private final String initialKey;
    private GridPosition position;
    private GridPosition countSnapshotPosition;
    private Map<String, Integer> countSnapshot = Map.of();
    private long revision;
    private boolean active, closed;
    public BoardSystem(Config config, MovementAccess movement, ThreadGuard thread, Function<UUID, Participant> participants) {
        this.config = Objects.requireNonNull(config); this.movement = Objects.requireNonNull(movement); this.thread = Objects.requireNonNull(thread); this.participants = Objects.requireNonNull(participants);
        movement.validate(config.initial()); initialKey = digest(movement.repetitionKey(config.initial()));
        long longestType = movement.definition().profiles().keySet().stream().mapToInt(id -> id.toString().length()).max().orElse(0);
        long longestCompound = movement.definition().compounds().stream().mapToInt(c -> c.id().toString().length()).max().orElse(0);
        if ((longestType + longestCompound + 90) * config.historyLimit() > 6_000_000) throw new ConfigException("Configured move archive exceeds persistence byte budget");
    }
    private void requireActive() { thread.check(); if (!active) throw new IllegalStateException("Board inactive"); }
    @Override public void start() {
        thread.check(); if (active || closed) throw new IllegalStateException("Board already started/closed"); position = config.initial(); counts.put(initialKey, 1); active = true;
    }
    @Override public GridPosition position() { requireActive(); return position; }
    @Override public long revision() { requireActive(); return revision; }
    @Override public int repetitions() { requireActive(); return counts.getOrDefault(history.isEmpty() ? initialKey : history.getLast().key(), 0); }
    @Override public Map<String, Integer> repetitionCounts() {
        requireActive();
        if (countSnapshotPosition != position) { countSnapshot = Map.copyOf(counts); countSnapshotPosition = position; }
        return countSnapshot;
    }
    @Override public int repetitions(String canonicalKey) { requireActive(); return counts.getOrDefault(digest(canonicalKey), 0); }
    @Override public List<Entry> history(int from, int maximum) {
        requireActive(); if (from < 0 || from > history.size() || maximum < 1 || maximum > 256) throw new IllegalArgumentException("History page limits");
        return List.copyOf(history.subList(from, Math.min(history.size(), from + maximum)));
    }
    @Override public Archive archive() { requireActive(); return new Archive(config.initial(), history); }
    @Override public MoveChange prepareMove(UUID actor, Move move) {
        requireActive(); Participant participant = participants.apply(actor);
        if (participant == null || participant.kind() == Participant.Kind.SPECTATOR || !participant.team().equals(position.turn())) throw new IllegalArgumentException("Board turn or ownership");
        if (history.size() >= config.historyLimit() || revision == Long.MAX_VALUE) throw new IllegalStateException("Board history/revision capacity");
        GridPosition before = position, after = movement.apply(before, move);
        Entry entry = new Entry(move, digest(movement.repetitionKey(after))); long expected = revision;
        return new MoveChange() {
            private int state;
            @Override public GridPosition result() { return after; }
            @Override public void apply() {
                requireActive(); if (state != 0 || revision != expected || position != before) throw new IllegalStateException("Stale or reused board move");
                history.add(entry); counts.merge(entry.key(), 1, Integer::sum); position = after; revision++; state = 1;
            }
            @Override public void rollback() {
                requireActive(); if (state != 1 || revision != expected + 1 || position != after) throw new IllegalStateException("Board rollback conflict");
                history.removeLast(); counts.compute(entry.key(), (key, value) -> value == 1 ? null : value - 1); position = before; revision = expected; state = 2;
            }
        };
    }
    public static String digest(String canonicalKey) { return PositionKeys.digest(canonicalKey); }
    private static String encode(Entry e) {
        Move m = e.move(); return m.from() + ";" + m.to() + ";" + (m.promotion() == null ? "-" : m.promotion()) + ";" + (m.compound() == null ? "-" : m.compound()) + ";" + e.key();
    }
    private Entry decode(String value) {
        if (value.length() > 512) throw new ConfigException("Oversized move record"); String[] fields = value.split(";", -1);
        if (fields.length != 5 || !fields[4].matches("[a-f0-9]{64}")) throw new ConfigException("Invalid compact move record");
        int from = Integer.parseInt(fields[0]), to = Integer.parseInt(fields[1]);
        if (from < 0 || to < 0 || from >= config.initial().size() || to >= config.initial().size() || from == to) throw new ConfigException("Invalid archived move square");
        Id promotion = fields[2].equals("-") ? null : Id.of(fields[2]), compound = fields[3].equals("-") ? null : Id.of(fields[3]);
        if (promotion != null && !movement.definition().profiles().containsKey(promotion) || compound != null && movement.definition().compounds().stream().noneMatch(c -> c.id().equals(compound))) throw new ConfigException("Unknown archived move reference");
        return new Entry(new Move(from, to, promotion, compound), fields[4]);
    }
    /** Pure replay for off-thread history viewing/recovery preflight; never run long archives in a tick handler. */
    public static GridPosition replay(Archive archive, MovementRules rules, int count, Runnable budgetCheckpoint) {
        if (count < 0 || count > archive.moves().size()) throw new IllegalArgumentException("Replay range");
        rules.validate(archive.initial()); GridPosition result = archive.initial();
        for (int i = 0; i < count; i++) {
            budgetCheckpoint.run(); Entry entry = archive.moves().get(i); result = rules.apply(result, entry.move());
            if (!digest(rules.repetitionKey(result)).equals(entry.key())) throw new ConfigException("Archived move position mismatch");
        }
        return result;
    }
    @Override public void tick(long tick) { requireActive(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() {
        requireActive(); return Values.map(Map.of("revision", revision, "initial_key", initialKey, "position", position.encode(), "history", history.stream().map(BoardSystem::encode).toList()));
    }
    @Override public void restore(int schema, Map<String, Object> saved) {
        thread.check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid board restore");
        Node n = new Node(saved, "board-state"); n.only("revision", "initial_key", "position", "history");
        if (!n.string("initial_key").equals(initialKey) || n.list("history").size() > config.historyLimit()) throw new ConfigException("Board definition/history mismatch");
        long restoredRevision = n.integer("revision", 0, Long.MAX_VALUE - 1);
        List<Entry> restored = new ArrayList<>(); Map<String, Integer> restoredCounts = new HashMap<>(); restoredCounts.put(initialKey, 1);
        for (Object raw : n.list("history")) {
            if (!(raw instanceof String text)) throw new ConfigException("Expected compact move record");
            Entry entry = decode(text); restored.add(entry); restoredCounts.merge(entry.key(), 1, Integer::sum);
        }
        GridPosition current = GridPosition.decode(n.node("position")); movement.validate(current);
        String expected = restored.isEmpty() ? initialKey : restored.getLast().key();
        if (restoredRevision != restored.size() || current.ply() != Math.addExact(config.initial().ply(), restored.size())
                || !digest(movement.repetitionKey(current)).equals(expected)) throw new ConfigException("Board current state/history mismatch");
        position = current; revision = restoredRevision; history.clear(); history.addAll(restored); counts.clear(); counts.putAll(restoredCounts); active = true;
    }
    @Override public void close() { thread.check(); active = false; closed = true; history.clear(); counts.clear(); position = null; countSnapshotPosition = null; countSnapshot = Map.of(); }
}
