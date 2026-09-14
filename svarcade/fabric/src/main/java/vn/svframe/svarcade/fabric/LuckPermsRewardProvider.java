package vn.svframe.svarcade.fabric;

import java.util.*;
import java.util.concurrent.CompletionStage;
import net.luckperms.api.*;
import net.luckperms.api.node.Node;
import vn.svframe.svarcade.reward.RewardProvider;

/** Idempotent permission-node reward over the LuckPerms asynchronous user manager. */
final class LuckPermsRewardProvider implements RewardProvider {
    private final LuckPerms api;
    private LuckPermsRewardProvider(LuckPerms api) { this.api = Objects.requireNonNull(api); }

    static Optional<LuckPermsRewardProvider> discover() {
        try { return Optional.of(new LuckPermsRewardProvider(LuckPermsProvider.get())); }
        catch (IllegalStateException | LinkageError unavailable) { return Optional.empty(); }
    }

    @Override public void validate(vn.svframe.svarcade.config.Node config) {
        config.only("permission", "value"); String permission = config.string("permission"); config.bool("value", true);
        if (permission.length() > 256 || !permission.matches("[A-Za-z0-9_.:-]+")) throw config.error("permission", "Invalid LuckPerms permission node");
    }

    @Override public CompletionStage<Void> grant(UUID claimId, UUID recipient, vn.svframe.svarcade.config.Node config) {
        validate(config); String permission = config.string("permission"); boolean value = config.bool("value", true);
        Node node = Node.builder(permission).value(value).build();
        return api.getUserManager().modifyUser(recipient, user -> user.data().add(node)).thenApply(ignored -> null);
    }
}
