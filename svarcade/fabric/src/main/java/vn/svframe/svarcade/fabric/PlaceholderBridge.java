package vn.svframe.svarcade.fabric;

import java.util.*;
import eu.pb4.placeholders.api.*;
import net.minecraft.util.Identifier;
import vn.svframe.svarcade.runtime.*;

/** Read-only Text Placeholder API bridge over authoritative runtime state. */
final class PlaceholderBridge implements AutoCloseable {
    private static final List<Identifier> IDS = List.of(
            Identifier.of("svarcade", "session"),
            Identifier.of("svarcade", "game"),
            Identifier.of("svarcade", "arena"),
            Identifier.of("svarcade", "team"),
            Identifier.of("svarcade", "role")
    );
    private GenericGameRuntime runtime;
    private boolean registered;

    static Optional<PlaceholderBridge> discover() {
        try {
            Class.forName("eu.pb4.placeholders.api.Placeholders", false, PlaceholderBridge.class.getClassLoader());
            return Optional.of(new PlaceholderBridge());
        } catch (ClassNotFoundException | LinkageError unavailable) { return Optional.empty(); }
    }

    void register(GenericGameRuntime runtime) {
        if (registered) throw new IllegalStateException("SVArcade placeholders already registered");
        this.runtime = Objects.requireNonNull(runtime);
        Placeholders.register(IDS.get(0), (ctx, arg) -> value(ctx, Value.SESSION));
        Placeholders.register(IDS.get(1), (ctx, arg) -> value(ctx, Value.GAME));
        Placeholders.register(IDS.get(2), (ctx, arg) -> value(ctx, Value.ARENA));
        Placeholders.register(IDS.get(3), (ctx, arg) -> value(ctx, Value.TEAM));
        Placeholders.register(IDS.get(4), (ctx, arg) -> value(ctx, Value.ROLE));
        registered = true;
    }

    private PlaceholderResult value(PlaceholderContext context, Value value) {
        GenericGameRuntime current = runtime;
        if (!registered || current == null || !context.hasPlayer()) return PlaceholderResult.invalid("SVArcade player context unavailable");
        Optional<GenericSession> found = current.sessionFor(context.player().getUuid());
        if (found.isEmpty()) return PlaceholderResult.invalid("Player is not in an SVArcade session");
        GenericSession session = found.get(); Participant participant = session.participants().get(context.player().getUuid());
        if (participant == null) return PlaceholderResult.invalid("SVArcade participant missing");
        return PlaceholderResult.value(switch (value) {
            case SESSION -> session.id().toString();
            case GAME -> session.definition().id().toString();
            case ARENA -> session.lease().arena().arena();
            case TEAM -> participant.team();
            case ROLE -> participant.kind().name().toLowerCase(Locale.ROOT);
        });
    }

    @Override public void close() {
        if (!registered) { runtime = null; return; }
        for (Identifier id : IDS) Placeholders.remove(id);
        registered = false; runtime = null;
    }
    private enum Value { SESSION, GAME, ARENA, TEAM, ROLE }
}
