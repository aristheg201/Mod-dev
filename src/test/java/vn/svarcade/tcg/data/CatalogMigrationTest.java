package vn.svarcade.tcg.data;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.InputStreamReader;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogMigrationTest {
    @TempDir Path dir;
    JsonObject bundled() throws Exception {
        try(var in=Catalog.class.getResourceAsStream("/data/svarcade_tcg/catalog.json")) {
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    Path save(JsonObject document) throws Exception {
        Path file=dir.resolve("catalog.json");Files.writeString(file,new GsonBuilder().serializeNulls().create().toJson(document));return file;
    }
    @Test void oldCatalogGetsAuthoredLevelsWithExactBackupAndCustomFieldsPreserved() throws Exception {
        var doc=bundled();var expected=doc.deepCopy();
        for(var entry:doc.getAsJsonObject("cards").entrySet())entry.getValue().getAsJsonObject().remove("level");
        doc.getAsJsonObject("cards").getAsJsonObject("charmander").addProperty("power",1234);
        doc.addProperty("operatorNote","Giữ cấu hình <custom>");
        Path file=save(doc);byte[] original=Files.readAllBytes(file);
        var result=Catalog.load(file);
        for(var entry:expected.getAsJsonObject("cards").entrySet()) {
            var card=entry.getValue().getAsJsonObject();
            if("pokemon".equals(card.get("category").getAsString()))assertEquals(card.get("level").getAsInt(),result.card(entry.getKey()).level());
        }
        assertEquals(1234,result.card("charmander").power());
        var migrated=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(doc.get("operatorNote"),migrated.get("operatorNote"));
        for(var entry:doc.getAsJsonObject("cards").entrySet())
            for(var field:entry.getValue().getAsJsonObject().entrySet())
                assertEquals(field.getValue(),migrated.getAsJsonObject("cards").getAsJsonObject(entry.getKey()).get(field.getKey()));
        for(String section:List.of("rules","banners","rewards","dealers","starters"))assertEquals(doc.get(section),migrated.get(section));
        try(var files=Files.list(dir)) {
            var backups=files.filter(p->p.toString().endsWith(".bak")).toList();assertEquals(1,backups.size());
            assertArrayEquals(original,Files.readAllBytes(backups.getFirst()));
        }
        byte[] first=Files.readAllBytes(file);Catalog.load(file);assertArrayEquals(first,Files.readAllBytes(file));
        try(var files=Files.list(dir)){assertEquals(2,files.count());}
    }
    @Test void validCustomLevelIsNotOverwritten() throws Exception {
        var doc=bundled();doc.getAsJsonObject("cards").getAsJsonObject("charmander").addProperty("level",6);
        Path file=save(doc);byte[] original=Files.readAllBytes(file);
        assertEquals(6,Catalog.load(file).card("charmander").level());assertArrayEquals(original,Files.readAllBytes(file));
        try(var files=Files.list(dir)){assertEquals(1,files.count());}
    }
    @Test void invalidExplicitLevelStillFailsWithoutWritingAPartialMigration() throws Exception {
        for(int invalid:List.of(0,13)) {
            var doc=bundled();doc.getAsJsonObject("cards").getAsJsonObject("charmander").addProperty("level",invalid);
            doc.getAsJsonObject("cards").getAsJsonObject("squirtle").remove("level");
            Path file=save(doc);byte[] original=Files.readAllBytes(file);
            assertThrows(IllegalArgumentException.class,()->Catalog.load(file));assertArrayEquals(original,Files.readAllBytes(file));
            try(var files=Files.list(dir)){assertEquals(1,files.count());}
        }
    }
    @Test void customSpeciesWithoutAnAuthoredLevelIsNotSilentlyAssignedABaseLevel() throws Exception {
        var doc=bundled();var card=doc.getAsJsonObject("cards").getAsJsonObject("charmander");
        card.remove("level");card.addProperty("species","addon:custom");Path file=save(doc);
        byte[] original=Files.readAllBytes(file);
        var error=assertThrows(IllegalArgumentException.class,()->Catalog.load(file));
        assertTrue(error.getMessage().contains("charmander: missing level"));assertArrayEquals(original,Files.readAllBytes(file));
    }
    @Test void freshInstallUsesBundledLevelsWithoutCreatingAnOverride() throws Exception {
        Path file=dir.resolve("absent.json");assertEquals(4,Catalog.load(file).card("charmander").level());assertFalse(Files.exists(file));
    }
}
