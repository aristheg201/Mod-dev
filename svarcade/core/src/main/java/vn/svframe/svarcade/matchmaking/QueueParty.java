package vn.svframe.svarcade.matchmaking;

import java.util.*;
import vn.svframe.svarcade.runtime.Participant;

public record QueueParty(UUID id, List<Participant> members) {
    public QueueParty {
        Objects.requireNonNull(id); members = List.copyOf(members);
        if (members.isEmpty() || members.size() > 1024) throw new IllegalArgumentException("Party limits");
        Set<UUID> unique = new HashSet<>();
        for (Participant member : members)
            if (member.kind() != Participant.Kind.PLAYER || !unique.add(member.id())) throw new IllegalArgumentException("Queue parties require unique players");
    }
}
