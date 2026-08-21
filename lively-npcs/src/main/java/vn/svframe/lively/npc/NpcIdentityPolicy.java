package vn.svframe.lively.npc;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.model.NpcState;
import vn.svframe.lively.social.SocialEngine;

/**
 * Hidden identity policy. The persisted NPC name is the public alias; a real identity lives only in metadata and
 * becomes visible after evidence-backed social trust/reputation crosses configured thresholds.
 */
public final class NpcIdentityPolicy {
    public record Resolution(String displayName, boolean revealed, double trust, double reputation) {}

    private NpcIdentityPolicy() {}

    public static Resolution resolve(NpcDefinition definition, NpcState state, ServerPlayerEntity viewer) {
        if (definition == null) return new Resolution("NPC", false, 0D, 0D);
        String realName = definition.metadata().getOrDefault("identity.real_name", "").trim();
        if (realName.isBlank() || viewer == null) return new Resolution(definition.name(), false, 0D, 0D);
        double trust = state == null ? 0D : state.snapshot(1).relationship(viewer.getUuid()).trust();
        ActorId viewerActor = new ActorId(viewer.getUuid(), ActorId.Kind.PLAYER);
        double reputation = LivelyApi.social().reputation(viewerActor, SocialEngine.ReputationScope.GLOBAL, "");
        double trustThreshold = number(definition.metadata().get("identity.reveal_trust"), .65D);
        double reputationThreshold = number(definition.metadata().get("identity.reveal_reputation"), .75D);
        boolean revealed = trust >= trustThreshold || reputation >= reputationThreshold;
        return new Resolution(revealed ? realName : definition.name(), revealed, trust, reputation);
    }

    private static double number(String raw, double fallback) {
        try { return Math.max(-1D, Math.min(1D, Double.parseDouble(raw == null ? Double.toString(fallback) : raw.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
