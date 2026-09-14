package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.reward.*;
import vn.svframe.svarcade.runtime.ThreadGuard;
import static org.junit.jupiter.api.Assertions.*;

class RewardManagerTest {
    @Test void pendingClaimsRetryByStableIdWithoutDoubleGrant() {
        ThreadGuard thread = new ThreadGuard(); Id type = Id.of("test:item"); Set<UUID> granted = new HashSet<>(); int[] calls = {0};
        RewardProvider provider = new RewardProvider() {
            @Override public void validate(Node config) { config.only("amount"); config.integer("amount", 1, 64); }
            @Override public void grant(UUID claimId, UUID recipient, Node config) { calls[0]++; granted.add(claimId); }
        };
        Registry<RewardProvider> providers = new Registry.Builder<RewardProvider>().add(type, provider).build(); RewardManager first = new RewardManager(thread, providers, 32);
        UUID claimId = UUID.randomUUID(), player = UUID.randomUUID(); first.claim(claimId, player, type, new Node(Map.of("amount", 3), "reward"));
        assertEquals(RewardManager.Status.APPLIED, first.get(claimId).orElseThrow().status()); assertEquals(1, granted.size());
        Map<String,Object> saved = first.snapshot(); RewardManager restored = new RewardManager(thread, providers, 32); restored.restore(saved);
        restored.apply(claimId); assertEquals(1, granted.size()); assertEquals(1, calls[0]);
    }
}
