package vn.svframe.svarcade.systems.reward;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.reward.RewardManager;
import vn.svframe.svarcade.runtime.SessionServices;

/** Durable typed reward-claim capability. */
public interface RewardAccess {
    SessionServices.Key<RewardAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:rewards"), RewardAccess.class);
    RewardManager.Claim claim(UUID claimId, UUID recipient, Id type, Node config);
    RewardManager.Claim apply(UUID claimId);
    Optional<RewardManager.Claim> get(UUID claimId);
    long revision();
}
