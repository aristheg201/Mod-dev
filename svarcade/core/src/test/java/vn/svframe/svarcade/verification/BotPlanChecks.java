package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.bot.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.ActionSystem;

public final class BotPlanChecks {
    private BotPlanChecks() { }

    private static Map<String, Object> profile() {
        return Map.of("strategy", "test:search", "parameters", Map.of(), "think_nanos", 1_000_000,
                "operations", 1000, "delay_ticks", 1, "retry_ticks", 2,
                "pending_timeout_ticks", 20, "controller_ttl_ticks", 10);
    }

    public static void main(String[] args) {
        ThreadGuard thread = new ThreadGuard();
        BotRuntime workers = new BotRuntime(thread, new Registry<>(Map.of()), 1, 2);
        try {
            Node config = new Node(Map.of("source_system", "svarcade:board_bot_source",
                    "profiles", Map.of("EASY", profile(), "NORMAL", profile(), "HARD", profile()),
                    "default_difficulty", "EASY", "team_difficulties", Map.of(),
                    "max_bots_per_tick", 2, "seed", 7), "bot-plan");
            BotSystem.Plan plan = new BotSystem.Plan(workers); plan.validate(config);
            Checks.equal(Set.of(ActionSystem.ID, Id.of("svarcade:board_bot_source")), plan.dependencies(config));
            Checks.equal(Set.of(ActionSystem.ACCESS, BotDecisionSource.ACCESS), plan.requires(config));
        } finally { workers.close(); }
        System.out.println("BotPlanChecks passed");
    }
}
