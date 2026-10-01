package vn.svarcade.tcg.data;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Upgrades persisted catalog fields without replacing operator-authored content. */
final class CatalogMigration {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    static Catalog load(Path override) throws IOException {
        JsonObject bundled;
        try(var in=Catalog.class.getResourceAsStream("/data/svarcade_tcg/catalog.json");
            var reader=new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)) {
            bundled=JsonParser.parseReader(reader).getAsJsonObject();
        }
        boolean persisted=Files.exists(override);
        byte[] original=persisted?Files.readAllBytes(override):null;
        JsonObject document=persisted?JsonParser.parseString(new String(original,StandardCharsets.UTF_8)).getAsJsonObject():bundled;
        int changed=0;
        for(var entry:document.getAsJsonObject("cards").entrySet()) {
            JsonObject card=entry.getValue().getAsJsonObject();
            if(!"pokemon".equals(card.get("category").getAsString())
                    ||card.has("level")&&!card.get("level").isJsonNull())continue;
            JsonObject defaults=bundled.getAsJsonObject("cards").getAsJsonObject(entry.getKey());
            if(defaults==null||!"pokemon".equals(defaults.get("category").getAsString())
                    ||!Objects.equals(card.get("species"),defaults.get("species")))
                throw new IllegalArgumentException("Catalog card "+entry.getKey()+": missing level; set an authored integer from 1 to 12 for this custom Pokemon.");
            card.add("level",defaults.get("level").deepCopy());changed++;
        }
        Catalog catalog=JSON.fromJson(document,Catalog.class);
        catalog.validate();catalog=EffectContent.apply(catalog);catalog.validate();
        // Validation must complete before either the backup or the override is written.
        if(persisted&&changed>0) {
            Path target=override.toAbsolutePath();Path directory=target.getParent();
            Path backup=Files.createTempFile(directory,target.getFileName()+".pre-level-migration-",".bak");
            Files.write(backup,original);
            Path temporary=Files.createTempFile(directory,target.getFileName()+".migration-",".tmp");
            try {
                Files.writeString(temporary,JSON.toJson(document)+"\n",StandardCharsets.UTF_8);
                try {Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
            } finally {Files.deleteIfExists(temporary);}
            org.slf4j.LoggerFactory.getLogger("svarcade_tcg").info("CARDWORLDS_CATALOG_MIGRATED missingLevels={} backup={}",changed,backup);
        }
        return catalog;
    }
    private CatalogMigration() {}
}
