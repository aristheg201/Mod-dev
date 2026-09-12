package vn.svframe.svarcade.config;

import java.util.*;

/** A session retains this exact immutable, content-addressed definition on reload. */
public record Definition(int schema, Id id, String fingerprint, boolean enabled,
                         int minPlayers, int maxPlayers, Set<String> integrations,
                         List<SystemSpec> systems, Map<String, Node> arenas) {
    public Definition {
        if (schema != 1 || minPlayers < 1 || maxPlayers < minPlayers) throw new ConfigException("Invalid definition header");
        Objects.requireNonNull(id); Objects.requireNonNull(fingerprint);
        integrations = Set.copyOf(integrations); systems = List.copyOf(systems); arenas = Map.copyOf(arenas);
    }
    public record SystemSpec(Id id, Node config) {
        public SystemSpec { Objects.requireNonNull(id); Objects.requireNonNull(config); }
    }
}
