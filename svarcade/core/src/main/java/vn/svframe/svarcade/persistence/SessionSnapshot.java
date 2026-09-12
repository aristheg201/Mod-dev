package vn.svframe.svarcade.persistence;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Explicit recovery envelope. Definition mismatch is rejected before arena acquisition. */
public record SessionSnapshot(UUID id, Id definition, String fingerprint, String arena,
                              long revision, List<Participant> participants,
                              Map<Id, GenericSession.SystemState> systems) {
    public SessionSnapshot {
        Objects.requireNonNull(id); Objects.requireNonNull(definition); Objects.requireNonNull(fingerprint); Objects.requireNonNull(arena);
        if (revision < 0) throw new IllegalArgumentException("Negative revision");
        participants = List.copyOf(participants); systems = Map.copyOf(systems);
    }
    public static SessionSnapshot capture(GenericSession session) {
        return new SessionSnapshot(session.id(), session.definition().id(), session.definition().fingerprint(),
                session.lease().arena().arena(), session.revision(), new ArrayList<>(session.participants().values()), session.snapshot());
    }
    public Map<String, Object> encode() {
        List<Object> people = participants.stream().<Object>map(p -> Map.of("id", p.id().toString(), "kind", p.kind().name(), "team", p.team())).toList();
        Map<String, Object> states = new LinkedHashMap<>();
        systems.forEach((id, s) -> states.put(id.toString(), Map.of("schema", s.schema(), "data", s.data())));
        return Values.map(Map.of("schema", 1, "id", id.toString(), "definition", definition.toString(),
                "fingerprint", fingerprint, "arena", arena, "revision", revision, "participants", people, "systems", states));
    }
    public static SessionSnapshot decode(Map<String, Object> values) {
        Node n = new Node(values, "session");
        n.only("schema", "id", "definition", "fingerprint", "arena", "revision", "participants", "systems");
        n.integer("schema", 1, 1);
        List<Participant> people = new ArrayList<>(); Set<UUID> ids = new HashSet<>();
        for (Node p : n.nodes("participants")) {
            p.only("id", "kind", "team"); UUID id = UUID.fromString(p.string("id"));
            if (!ids.add(id)) throw p.error("id", "Duplicate participant");
            people.add(new Participant(id, Participant.Kind.valueOf(p.string("kind")), p.string("team")));
        }
        Map<Id, GenericSession.SystemState> systems = new LinkedHashMap<>(); Node states = n.node("systems");
        for (String key : states.values().keySet()) {
            Node state = states.node(key); state.only("schema", "data");
            systems.put(Id.of(key), new GenericSession.SystemState((int) state.integer("schema", 1, Integer.MAX_VALUE), state.node("data").values()));
        }
        return new SessionSnapshot(UUID.fromString(n.string("id")), Id.of(n.string("definition")), n.string("fingerprint"),
                n.string("arena"), n.integer("revision", 0, Long.MAX_VALUE), people, systems);
    }
    public void validateAgainst(Definition d) {
        if (!definition.equals(d.id()) || !fingerprint.equals(d.fingerprint()) || !d.arenas().containsKey(arena)) throw new ConfigException("Recovery definition identity/version/arena mismatch");
        Set<Id> expected = new HashSet<>(); d.systems().forEach(s -> expected.add(s.id()));
        if (!expected.equals(systems.keySet())) throw new ConfigException("Recovery system set mismatch");
    }
}
