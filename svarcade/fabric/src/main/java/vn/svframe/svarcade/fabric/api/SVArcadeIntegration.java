package vn.svframe.svarcade.fabric.api;

import java.util.Set;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.reward.RewardProvider;
import vn.svframe.svarcade.runtime.GenericGameRuntime;

/**
 * Optional Fabric integration entrypoint. Implementations are discovered under the
 * {@code svarcade:integration} entrypoint key and are isolated from core game definitions.
 */
public interface SVArcadeIntegration {
    /** Integration capabilities made available only when registration completes successfully. */
    Set<String> capabilities();

    /** Register typed providers and fresh-session hooks. Must not perform blocking I/O. */
    void register(Context context);

    interface Context {
        void rewardProvider(Id id, RewardProvider provider);
        void sessionInitializer(GenericGameRuntime.SessionInitializer initializer);
    }
}
