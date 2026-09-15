package vn.svframe.svrelationships.fabric.command;

import com.mojang.brigadier.StringReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PokemonReferenceArgumentTypeTest {
    private final PokemonReferenceArgumentType type = PokemonReferenceArgumentType.reference();

    @Test
    void parsesPartyReferenceIncludingColonAsOneArgument() {
        StringReader reader = new StringReader("party:5 bond 5000");
        assertEquals("party:5", type.parse(reader));
        assertEquals(' ', reader.peek());
    }

    @Test
    void parsesAllSupportedReferencePrefixesWithoutSplittingColon() {
        assertParsed("partner:12", "partner:12");
        assertParsed("storage:27", "storage:27");
        assertParsed("uuid:123e4567-e89b-12d3-a456-426614174000", "uuid:123e4567-e89b-12d3-a456-426614174000");
    }

    private void assertParsed(String input, String expected) {
        StringReader reader = new StringReader(input);
        assertEquals(expected, type.parse(reader));
        assertEquals(input.length(), reader.getCursor());
    }
}
