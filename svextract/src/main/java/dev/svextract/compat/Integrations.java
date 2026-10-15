package dev.svextract.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.util.*;

/** Discovery only. Unknown SVFrame APIs are never replaced with guessed class lists or damage engines. */
public final class Integrations {
    public record Mod(String id,String version) {}
    public static List<Mod> installed() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(m->new Mod(m.getMetadata().getId(),m.getMetadata().getVersion().getFriendlyString()))
                .sorted(Comparator.comparing(Mod::id)).toList();
    }
    public static boolean pointBlankLoaded(){return FabricLoader.getInstance().isModLoaded("pointblank");}
    public static List<String> pointBlankItems(){
        return Registries.ITEM.getIds().stream().filter(id->id.getNamespace().equals("pointblank"))
                .map(Identifier::toString).sorted().toList();
    }
}
