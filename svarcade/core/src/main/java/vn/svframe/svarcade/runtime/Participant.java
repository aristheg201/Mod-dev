package vn.svframe.svarcade.runtime;

import java.util.*;

public record Participant(UUID id, Kind kind, String team) {
    public enum Kind { PLAYER, BOT, SPECTATOR }
    public Participant { Objects.requireNonNull(id); Objects.requireNonNull(kind); Objects.requireNonNull(team); }
}
