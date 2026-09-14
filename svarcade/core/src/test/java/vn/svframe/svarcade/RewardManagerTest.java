package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.reward.*;
import vn.svframe.svarcade.runtime.ThreadGuard;
import static org.junit.jupiter.api.Assertions.*;

class RewardManagerTest {
    @Test void appliedClaimDoesNotGrantAgainAfterRestart() {
        ThreadGuard thread = new ThreadGuard(); Id type = Id.of("test:item"); Set<UUID> granted = new HashSet<>(); int[] calls = {0};
        RewardProvider provider = provider(granted, calls); Registry<RewardProvider> providers = new Registry.Builder<RewardProvider>().add(type, provider).build();
        RewardManager first = new RewardManager(thread, providers, 32); UUID claimId = UUID.randomUUID(), player = UUID.randomUUID();
        first.claim(claimId, player, type, new Node(Map.of("amount", 3), "reward")); assertEquals(RewardManager.Status.APPLIED, first.get(claimId).orElseThrow().status());
        RewardManager restored = new RewardManager(thread, providers, 32); restored.restore(first.snapshot()); restored.apply(claimId);
        assertEquals(1, granted.size()); assertEquals(1, calls[0]);
    }

    @Test void pendingCrashClaimRetriesWithSameIdempotencyKey() {
        ThreadGuard thread = new ThreadGuard(); Id type = Id.of("test:item"); Set<UUID> granted = new HashSet<>(); int[] calls = {0};
        Registry<RewardProvider> providers = new Registry.Builder<RewardProvider>().add(type, provider(granted, calls)).build(); UUID claimId = UUID.randomUUID(), player = UUID.randomUUID();
        Map<String,Object> pending = Map.of("schema", 1, "revision", 1, "claims", List.of(Map.of(
                "id", claimId.toString(), "recipient", player.toString(), "type", type.toString(), "config", Map.of("amount", 4), "status", "PENDING")));
        RewardManager restored = new RewardManager(thread, providers, 32); restored.restore(pending); assertEquals(1, restored.retryPending(8));
        assertEquals(RewardManager.Status.APPLIED, restored.get(claimId).orElseThrow().status()); assertEquals(Set.of(claimId), granted); assertEquals(1, calls[0]);
        restored.apply(claimId); assertEquals(1, calls[0]);
    }

    private static RewardProvider provider(Set<UUID> granted, int[] calls) {
        return new RewardProvider() {
            @Override public void validate(Node config) { config.only("amount"); config.integer("amount", 1, 64); }
            @Override public void grant(UUID claimId, UUID recipient, Node config) { calls[0]++; granted.add(claimId); }
        };
    }
}
