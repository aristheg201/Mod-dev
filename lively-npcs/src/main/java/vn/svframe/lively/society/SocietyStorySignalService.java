package vn.svframe.lively.society;

import net.minecraft.server.MinecraftServer;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.economy.DebtEngine;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.law.LawEnforcementEngine;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts persistent society pressure into bounded world events consumed by the existing Story Director.
 * It does not manufacture crimes or outcomes; it exposes consequences already present in debt/gambling/business/law state.
 */
public final class SocietyStorySignalService {
    private static final long PULSE_TICKS = 1200L;
    private static final int MAX_NEW_EVENTS_PER_PULSE = 2;
    private final MinecraftServer server;
    private long lastPulse;

    public SocietyStorySignalService(MinecraftServer server) { this.server = server; }

    public void tick(long tick) {
        if (tick - lastPulse < PULSE_TICKS) return;
        lastPulse = tick;
        int started = 0;
        if (started < MAX_NEW_EVENTS_PER_PULSE && emitDebtCrisis()) started++;
        if (started < MAX_NEW_EVENTS_PER_PULSE && emitBusinessFailure()) started++;
        if (started < MAX_NEW_EVENTS_PER_PULSE && emitGamblingWave()) started++;
        if (started < MAX_NEW_EVENTS_PER_PULSE) emitLawPressure();
    }

    private boolean emitDebtCrisis() {
        List<DebtEngine.Contract> pressured = SocietyApi.debts().snapshot().contracts().values().stream()
                .filter(value -> value.status() == DebtEngine.Status.DELINQUENT || value.status() == DebtEngine.Status.COLLECTION)
                .toList();
        double intensity = clamp(pressured.size() / 8D);
        if (intensity < .50D) return false;
        LinkedHashSet<ActorId> participants = new LinkedHashSet<>();
        for (DebtEngine.Contract debt : pressured) {
            participants.add(debt.debtor());
            participants.add(debt.creditor());
            if (participants.size() >= 24) break;
        }
        return start("debt_crisis", WorldEventEngine.Category.ECONOMIC, intensity, participants,
                Duration.ofHours(3), Map.of("kind", "debt_crisis", "delinquent_contracts", Integer.toString(pressured.size()),
                        "semantic_only", "true"));
    }

    private boolean emitBusinessFailure() {
        var businesses = LivelyApi.economy().snapshot().businesses().values();
        List<vn.svframe.lively.economy.EconomyEngine.Business> failed = businesses.stream()
                .filter(value -> Boolean.parseBoolean(value.facts().getOrDefault("bankrupt", "false"))).toList();
        double intensity = clamp(failed.size() / 4D);
        if (intensity < .50D) return false;
        LinkedHashSet<ActorId> participants = new LinkedHashSet<>();
        failed.stream().map(vn.svframe.lively.economy.EconomyEngine.Business::owner).limit(24).forEach(participants::add);
        String structure = failed.stream().map(vn.svframe.lively.economy.EconomyEngine.Business::locationId)
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
        return start("business_failure", WorldEventEngine.Category.ECONOMIC, intensity, participants, structure,
                Duration.ofHours(4), Map.of("kind", "business_failure", "failed_businesses", Integer.toString(failed.size()),
                        "semantic_only", "true"));
    }

    private boolean emitGamblingWave() {
        var habits = SocietyApi.gambling().snapshot().habits();
        List<Map.Entry<ActorId, vn.svframe.lively.economy.GamblingEngine.Habit>> severe = habits.entrySet().stream()
                .filter(entry -> entry.getValue().compulsion() >= .65D).toList();
        double intensity = clamp(severe.size() / 8D);
        if (intensity < .50D) return false;
        Set<ActorId> participants = severe.stream().map(Map.Entry::getKey).limit(24)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return start("gambling_wave", WorldEventEngine.Category.SOCIAL, intensity, participants,
                Duration.ofHours(2), Map.of("kind", "gambling_wave", "high_compulsion_actors", Integer.toString(severe.size()),
                        "semantic_only", "true"));
    }

    private boolean emitLawPressure() {
        LawEnforcementEngine.Snapshot snapshot = SocietyApi.law().snapshot();
        long activeWarrants = snapshot.warrants().values().stream()
                .filter(value -> value.status() == LawEnforcementEngine.WarrantStatus.ACTIVE).count();
        long fugitives = snapshot.wanted().values().stream()
                .filter(value -> value.level() == LawEnforcementEngine.WantedLevel.FUGITIVE
                        || value.level() == LawEnforcementEngine.WantedLevel.HIGH_RISK).count();
        double intensity = clamp(activeWarrants / 8D + fugitives / 6D);
        if (intensity < .50D) return false;
        Set<ActorId> participants = snapshot.wanted().values().stream()
                .sorted(java.util.Comparator.comparingInt(LawEnforcementEngine.WantedRecord::points).reversed())
                .map(LawEnforcementEngine.WantedRecord::subject).limit(24)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return start("law_and_order", WorldEventEngine.Category.POLITICAL, intensity, participants,
                Duration.ofHours(3), Map.of("kind", "law_and_order", "active_warrants", Long.toString(activeWarrants),
                        "high_risk_wanted", Long.toString(fugitives), "semantic_only", "true"));
    }

    private boolean start(String seed, WorldEventEngine.Category category, double intensity, Set<ActorId> participants,
                          Duration duration, Map<String, String> facts) {
        return start(seed, category, intensity, participants, null, duration, facts);
    }

    private boolean start(String seed, WorldEventEngine.Category category, double intensity, Set<ActorId> participants,
                          String structureId, Duration duration, Map<String, String> facts) {
        if (LivelyApi.events().activeEvents().stream().anyMatch(event -> event.seed().equals(seed))) return false;
        return LivelyApi.events().start(new WorldEventEngine.EventProposal(category, seed, structureId,
                Set.copyOf(participants), intensity, duration, facts)).isPresent();
    }

    private static double clamp(double value) { return Math.max(0D, Math.min(1D, value)); }
}
