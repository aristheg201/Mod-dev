package vn.svframe.lively.actor;

import java.util.Objects;
import java.util.UUID;

/** Stable identity shared by NPCs, players and other simulated actors. */
public record ActorId(UUID uuid, Kind kind) {
    public ActorId {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(kind);
    }

    public enum Kind { NPC, PLAYER, CREATURE, FACTION, SYSTEM }
}
