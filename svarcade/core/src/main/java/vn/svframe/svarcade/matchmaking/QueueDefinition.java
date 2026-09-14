package vn.svframe.svarcade.matchmaking;

import java.util.Objects;
import vn.svframe.svarcade.config.Id;

public record QueueDefinition(Id id, Id definition, int targetPlayers, boolean botFill, String botTeam) {
    public QueueDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(definition); Objects.requireNonNull(botTeam);
        if (targetPlayers < 1 || targetPlayers > 1024 || botTeam.length() > 80) throw new IllegalArgumentException("Queue limits");
    }
}
