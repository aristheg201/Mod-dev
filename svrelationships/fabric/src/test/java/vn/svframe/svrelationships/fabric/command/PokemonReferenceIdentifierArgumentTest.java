package vn.svframe.svrelationships.fabric.command;

import com.mojang.brigadier.StringReader;
import net.minecraft.command.argument.IdentifierArgumentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PokemonReferenceIdentifierArgumentTest {
    @Test
    void parsesPartyReferenceIncludingColonAndLeavesFollowingArgument() throws Exception {
        StringReader reader = new StringReader("party:5 bond 5000");
        assertEquals("party:5", IdentifierArgumentType.identifier().parse(reader).toString());
        assertEquals(' ', reader.peek());
    }

    @Test
    void parsesEverySupportedReferenceFormAsVanillaIdentifier() throws Exception {
        assertParsed("party:5");
        assertParsed("partner:12");
        assertParsed("storage:27");
        assertParsed("uuid:123e4567-e89b-12d3-a456-426614174000");
    }

    private static void assertParsed(String input) throws Exception {
        StringReader reader = new StringReader(input);
        assertEquals(input, IdentifierArgumentType.identifier().parse(reader).toString());
        assertEquals(input.length(), reader.getCursor());
    }
}
