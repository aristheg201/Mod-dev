package vn.svframe.svarcade.reward;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import vn.svframe.svarcade.config.Node;

/** Typed reward adapter. Implementations must treat claimId as an idempotency key and never block the server thread. */
public interface RewardProvider {
    void validate(Node config);
    CompletionStage<Void> grant(UUID claimId, UUID recipient, Node config);
}
