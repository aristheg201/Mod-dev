package dev.svextract;

import com.google.gson.*;
import dev.svextract.match.GameModes;
import java.nio.file.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GameModesTest {
    @TempDir Path dir;
    @Test void unsupportedEnginesAndUnpreparedMapsCannotBeEnabled() throws Exception{
        Path file=dir.resolve("modes.json");GameModes modes=GameModes.load(file);
        assertEquals("blackglass_industrial",modes.get("operations").map());assertThrows(IllegalArgumentException.class,()->modes.get("battle_royale"));
        JsonArray values=JsonParser.parseString(Files.readString(file)).getAsJsonArray();
        for(JsonElement value:values)if(value.getAsJsonObject().get("id").getAsString().equals("battle_royale"))value.getAsJsonObject().addProperty("enabled",true);
        Files.writeString(file,values.toString());assertThrows(IOException.class,()->GameModes.load(file));
        for(JsonElement value:values){JsonObject d=value.getAsJsonObject();if(d.get("id").getAsString().equals("battle_royale"))d.addProperty("enabled",false);if(d.get("id").getAsString().equals("operations"))d.addProperty("map","missing_map");}
        Files.writeString(file,values.toString());assertThrows(IOException.class,()->GameModes.load(file));
    }
}
