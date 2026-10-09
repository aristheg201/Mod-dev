package io.github.aristheg201.cobblemonworld.narrative;

import java.util.Arrays;
import java.util.Set;

/** Save-only migration. Never grants items, currency, rewards or battle wins. */
public final class NarrativeMigration {
    public static final int CURRENT_SCHEMA = 2;
    public static final String OPTIONAL_SUPPLIES = "ren_optional_supplies";
    private NarrativeMigration() {}

    public static boolean apply(NarrativeState state, Set<String> flags, NarrativeRegistry.Stage[] campaign) {
        if (state.schema >= CURRENT_SCHEMA) return false;
        state.normalize();
        if (state.schema < 1) {
            int skip = 0;
            for (int i = 0; i < campaign.length; i++) {
                String flag = campaign[i].legacyFlag();
                if (flag != null && !flag.isBlank() && flags.contains(flag)) skip = i + 1;
            }
            if (flags.contains("main_story_complete")) skip = campaign.length;
            for (int i = 0; i < skip; i++) {
                var stage = campaign[i];
                state.finished.add(stage.id());
                if (stage.type().equals("battle")) state.finished.add("battle:" + stage.target());
                if (stage.flags() != null) flags.addAll(Arrays.asList(stage.flags()));
            }
            state.main = skip < campaign.length ? campaign[skip].id() : "";
        }
        // This ID is deliberately retained in the optional chain so existing history remains valid.
        if ("ren_supplies".equals(state.main)) state.main = "lan_errand";
        if (state.finished.contains("ren_supplies")) {
            state.chains.remove(OPTIONAL_SUPPLIES);
            state.completedChains.add(OPTIONAL_SUPPLIES);
        }
        state.schema = CURRENT_SCHEMA;
        return true;
    }
}
