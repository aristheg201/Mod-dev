package vn.svframe.svarcade.bot;

import java.util.Objects;
import vn.svframe.svarcade.config.*;

/** Definition-authored search parameters and scheduling budgets; difficulty has no numeric defaults. */
public record BotProfile(Id strategy, Node parameters, long thinkNanos, long operations,
                         long delayTicks, long retryTicks, long pendingTimeoutTicks, long controllerTtlTicks) {
    public BotProfile {
        Objects.requireNonNull(strategy); Objects.requireNonNull(parameters);
        if (thinkNanos < 1 || thinkNanos > 30_000_000_000L || operations < 1 || operations > 1_000_000_000L
                || delayTicks < 0 || delayTicks > 72_000 || retryTicks < 1 || retryTicks > 72_000
                || pendingTimeoutTicks < 1 || pendingTimeoutTicks > 72_000 || controllerTtlTicks < 1 || controllerTtlTicks > 72_000) {
            throw new ConfigException("Invalid bot scheduling/budget limits");
        }
    }
    public static BotProfile parse(Node n) {
        n.only("strategy", "parameters", "think_nanos", "operations", "delay_ticks", "retry_ticks", "pending_timeout_ticks", "controller_ttl_ticks");
        return new BotProfile(Id.of(n.string("strategy")), n.node("parameters"), n.integer("think_nanos", 1, 30_000_000_000L),
                n.integer("operations", 1, 1_000_000_000L), n.integer("delay_ticks", 0, 72_000), n.integer("retry_ticks", 1, 72_000),
                n.integer("pending_timeout_ticks", 1, 72_000), n.integer("controller_ttl_ticks", 1, 72_000));
    }
}
