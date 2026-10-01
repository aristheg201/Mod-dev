package vn.svarcade.tcg.integration;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaceholderComponentsTest {
    @Test void realMessagePipelineTreatsProviderMarkupAsLiteralText(){
        var mini=MiniMessage.miniMessage();var input=mini.deserialize("<gold>Xin chào %cardworlds:player%</gold>");
        var result=PlaceholderComponents.resolve(input,key->"<red>Player</red>");
        String serialized=mini.serialize(result);assertTrue(serialized.contains("\\<red>Player\\</red>"),serialized);assertFalse(serialized.contains("%cardworlds:player%"));
        var absent=PlaceholderComponents.resolve(input,key->null);assertTrue(mini.serialize(absent).contains("—"));
    }
}
