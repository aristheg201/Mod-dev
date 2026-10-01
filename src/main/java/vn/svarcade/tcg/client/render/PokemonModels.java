package vn.svarcade.tcg.client.render;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svarcade.tcg.client.component.Rect;
import vn.svarcade.tcg.client.component.Ui;

import java.util.*;

/** Cobblemon poser/texture/aspect pipeline used as actual card art. */
public final class PokemonModels {
    private static final Logger LOG = LoggerFactory.getLogger("cardworlds");
    private record Actor(RenderablePokemon pokemon, FloatingState state, long born) {}
    private static final Map<String, Actor> CACHE = new LinkedHashMap<>();
    private static final Map<String, Long> RETRY_AFTER = new HashMap<>();

    public static void initialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return Identifier.of("svarcade_tcg", "pokemon_card_models"); }
            @Override public void reload(ResourceManager manager) { CACHE.clear(); RETRY_AFTER.clear(); }
        });
    }

    public static void draw(Ui ui, String species, List<String> aspects, Rect viewport, String instance) {
        if (species == null || species.isBlank()) { fallback(ui, viewport, "NO SPECIES"); return; }
        String key = species + '|' + String.join(",", aspects) + '|' + instance;
        long now = System.currentTimeMillis();
        if (RETRY_AFTER.getOrDefault(key, 0L) > now) { fallback(ui, viewport, species.toUpperCase(Locale.ROOT)); return; }

        // DrawContext scissor is screen-space while Card Worlds cards live in a transformed
        // 1280 logical canvas. Pack reveal adds another per-card X flip transform. Transform
        // all four artwork corners through the CURRENT matrix so the clip follows the card.
        Matrix4f clipMatrix = new Matrix4f(ui.c.getMatrices().peek().getPositionMatrix());
        Vector4f p1 = clipMatrix.transform(new Vector4f(viewport.x(), viewport.y(), 0f, 1f));
        Vector4f p2 = clipMatrix.transform(new Vector4f(viewport.right(), viewport.y(), 0f, 1f));
        Vector4f p3 = clipMatrix.transform(new Vector4f(viewport.right(), viewport.bottom(), 0f, 1f));
        Vector4f p4 = clipMatrix.transform(new Vector4f(viewport.x(), viewport.bottom(), 0f, 1f));
        float minX = Math.min(Math.min(p1.x, p2.x), Math.min(p3.x, p4.x));
        float maxX = Math.max(Math.max(p1.x, p2.x), Math.max(p3.x, p4.x));
        float minY = Math.min(Math.min(p1.y, p2.y), Math.min(p3.y, p4.y));
        float maxY = Math.max(Math.max(p1.y, p2.y), Math.max(p3.y, p4.y));
        ui.c.enableScissor(
            (int)Math.floor(minX), (int)Math.floor(minY),
            (int)Math.ceil(maxX), (int)Math.ceil(maxY)
        );
        ui.c.getMatrices().push();
        try {
            Actor actor = CACHE.get(key);
            if (actor == null) {
                var descriptor=vn.svarcade.tcg.integration.CobblemonBridge.resolve(species,"",aspects,"",false,"");
                if(!descriptor.available())return;
                PokemonProperties props=vn.svarcade.tcg.integration.CobblemonBridge.properties(descriptor);
                actor = new Actor(props.asRenderablePokemon(), new FloatingState(), now);
                if (CACHE.size() >= 160) CACHE.remove(CACHE.keySet().iterator().next());
                CACHE.put(key, actor);
            }

            float partial = (now % 50) / 50f;
            actor.state().updateAge((int)((now - actor.born()) / 50));
            actor.state().updatePartialTicks(partial);

            // Cobblemon's profile transform already applies its own vertical profile offset.
            // Anchor near the top of the artwork window (as Cobblemon's own ModelWidget does),
            // rather than near the card bottom, otherwise every model sits on the footer.
            ui.c.getMatrices().translate(
                viewport.x() + viewport.w() * 0.50f,
                viewport.y() + Math.max(6f, viewport.h() * 0.16f),
                140
            );
            float basis = Math.min(viewport.w(), viewport.h());
            float scale = Math.clamp(basis * 0.42f, 26f, 52f);
            Quaternionf rotation = new Quaternionf().rotationXYZ(
                (float)Math.toRadians(13),
                (float)Math.toRadians(-35),
                0f
            );

            PokemonGuiUtilsKt.drawProfilePokemon(
                actor.pokemon(), ui.c.getMatrices(), rotation,
                PoseType.PROFILE, actor.state(), partial, scale,
                ProfileTransformType.PROFILE, false,
                1f, 1f, 1f, 1f, 0f, 0f, 13
            );
        } catch (RuntimeException ex) {
            if (!RETRY_AFTER.containsKey(key)) LOG.warn("Could not render Cobblemon card model {} aspects {}", species, aspects, ex);
            RETRY_AFTER.put(key, now + 5000);
            CACHE.remove(key);
            fallback(ui, viewport, species.toUpperCase(Locale.ROOT));
        } finally {
            ui.c.getMatrices().pop();
            ui.c.disableScissor();
        }
    }

    private static void fallback(Ui ui, Rect r, String label) {
        int cx = r.x() + r.w() / 2, cy = r.y() + r.h() / 2;
        ui.fill(new Rect(cx - 1, r.y() + 10, 2, Math.max(1, r.h() - 20)), 0x2249C9F5);
        ui.fill(new Rect(r.x() + 10, cy - 1, Math.max(1, r.w() - 20), 2), 0x2249C9F5);
        ui.fit(label, new Rect(r.x() + 6, r.bottom() - 18, r.w() - 12, 12), 10, Ui.MUTED);
    }
}
