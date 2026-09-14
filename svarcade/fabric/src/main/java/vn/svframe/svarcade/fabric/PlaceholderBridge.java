package vn.svframe.svarcade.fabric;

import java.util.*;
import eu.pb4.placeholders.api.*;
import net.minecraft.util.Identifier;
import vn.svframe.svarcade.runtime.*;

/** Read-only Text Placeholder API bridge over authoritative runtime state. */
final class PlaceholderBridge implements AutoCloseable {
    private final List<Identifier> ids;
    private GenericGameRuntime runtime;
    private boolean registered;

    private PlaceholderBridge(String namespace) {
        ids = List.of(
                Identifier.of(namespace, "session"), Identifier.of(namespace, "game"), Identifier.of(namespace, "arena"),
                Identifier.of(namespace, "team"), Identifier.of(namespace, "role"));
    }

    static Optional<PlaceholderBridge> discover(String namespace) {
        Objects.requireNonNull(namespace);
        try {
            Class.forName("eu.pb4.placeholders.api.Placeholders", false, PlaceholderBridge.class.getClassLoader());
            return Optional.of(new PlaceholderBridge(namespace));
        } catch (ClassNotFoundException | LinkageError unavailable) { return Optional.empty(); }
    }

    void register(GenericGameRuntime runtime) {
        if (registered) throw new IllegalStateException("SVArcade placeholders already registered");
        this.runtime = Objects.requireNonNull(runtime);
        Placeholders.register(ids.get(0), (ctx, arg) -> value(ctx, Value.SESSION));
        Placeholders.register(ids.get(1), (ctx, arg) -> value(ctx, Value.GAME));
        Placeholders.register(ids.get(2), (ctx, arg) -> value(ctx, Value.ARENA));
        Placeholders.register(ids.get(3), (ctx, arg) -> value(ctx, Value.TEAM));
        Placeholders.register(ids.get(4), (ctx, arg) -> value(ctx, Value.ROLE));
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
        for (Identifier id : ids) Placeholders.remove(id);
        registered = false; runtime = null;
    }
    private enum Value { SESSION, GAME, ARENA, TEAM, ROLE }
}
