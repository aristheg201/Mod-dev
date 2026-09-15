package vn.svframe.svrelationships.fabric.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;

import java.util.Collection;
import java.util.List;

/**
 * Brigadier's StringArgumentType.word() deliberately stops before ':'.
 * SVRelationships Pokemon references use colon-delimited canonical forms such
 * as party:1, partner:2, storage:15 and uuid:<uuid>, so they need an argument
 * parser that consumes every non-whitespace character as one token.
 */
public final class PokemonReferenceArgumentType implements ArgumentType<String> {
    private static final PokemonReferenceArgumentType INSTANCE = new PokemonReferenceArgumentType();
    private static final Collection<String> EXAMPLES = List.of(
            "party:1",
            "partner:1",
            "storage:1",
            "uuid:123e4567-e89b-12d3-a456-426614174000"
    );

    private PokemonReferenceArgumentType() {}

    public static PokemonReferenceArgumentType reference() {
        return INSTANCE;
    }

    @Override
    public String parse(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
