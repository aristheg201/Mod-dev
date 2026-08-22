package vn.svframe.lively.animation;

import net.minecraft.server.MinecraftServer;
import vn.svframe.lively.npc.NpcBody;
import vn.svframe.lively.npc.NpcRuntime;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-authoritative animation dispatcher.
 *
 * <p>It never sends custom assets and never assumes a client-side Lively installation. Each physical body translates
 * the shared request into animation/state already understood by the vanilla or integration client.</p>
 */
public final class LivelyAnimationEngine {
    private final NpcRuntime runtime;

    public LivelyAnimationEngine(NpcRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime);
    }

    public AnimationResult play(MinecraftServer server, UUID npcId, String animation) {
        return play(server, npcId, AnimationRequest.named(animation));
    }

    public AnimationResult play(MinecraftServer server, UUID npcId, AnimationRequest request) {
        Objects.requireNonNull(server);
        Objects.requireNonNull(npcId);
        Objects.requireNonNull(request);
        NpcBody body = runtime.body(npcId).orElse(null);
        if (body == null || !body.spawned()) {
            return AnimationResult.unsupported(request.name(), "NPC body is not spawned");
        }
        return body.animate(server, request);
    }
}
