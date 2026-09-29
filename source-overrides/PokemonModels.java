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
import org.joml.Quaternionf;
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

        // Card Worlds renders in a 1280-wide logical canvas and scales the PoseStack down
        // to Minecraft GUI coordinates. DrawContext scissor coordinates are NOT transformed
        // by that PoseStack, so feeding 1280-space coordinates clipped every Pokemon model.
        float guiScale = MinecraftClient.getInstance().getWindow().getScaledWidth() / 1280.0f;
        int sx1 = (int)Math.floor(viewport.x() * guiScale);
        int sy1 = (int)Math.floor(viewport.y() * guiScale);
        int sx2 = (int)Math.ceil(viewport.right() * guiScale);
        int sy2 = (int)Math.ceil(viewport.bottom() * guiScale);
        ui.c.enableScissor(sx1, sy1, sx2, sy2);
        ui.c.getMatrices().push();
        try {
            Actor actor = CACHE.get(key);
            if (actor == null) {
                PokemonProperties props = PokemonProperties.Companion.parse(species);
                props.setAspects(new HashSet<>(aspects));
                actor = new Actor(props.asRenderablePokemon(), new FloatingState(), now);
                if (CACHE.size() >= 160) CACHE.remove(CACHE.keySet().iterator().next());
                CACHE.put(key, actor);
            }

            float partial = (now % 50) / 50f;
            actor.state().updateAge((int)((now - actor.born()) / 50));
            actor.state().updatePartialTicks(partial);

            ui.c.getMatrices().translate(viewport.x() + viewport.w() * 0.50f, viewport.y() + viewport.h() * 0.76f, 140);
            float scale = Math.clamp(Math.min(viewport.w() * 0.30f, viewport.h() * 0.38f), 12f, 34f);
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
