package vn.svframe.svarcade.reward;

import java.util.UUID;
import vn.svframe.svarcade.config.Node;

/** Typed reward adapter. Implementations must treat claimId as an idempotency key. */
public interface RewardProvider {
    void validate(Node config);
    void grant(UUID claimId, UUID recipient, Node config);
}
