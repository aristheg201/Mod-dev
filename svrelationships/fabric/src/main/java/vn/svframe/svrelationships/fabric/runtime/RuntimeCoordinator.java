package vn.svframe.svrelationships.fabric.runtime;

import net.minecraft.server.MinecraftServer;
import vn.svframe.svrelationships.fabric.config.ConfigService;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.config.RelationshipRuleService;
import vn.svframe.svrelationships.fabric.diagnostics.RuntimeMetrics;
import vn.svframe.svrelationships.fabric.family.DaycareRuntimeService;
import vn.svframe.svrelationships.fabric.gui.GuiDefinitionService;
import vn.svframe.svrelationships.fabric.gui.ServerGuiService;
import vn.svframe.svrelationships.fabric.household.HouseholdMaterializationService;
import vn.svframe.svrelationships.fabric.household.HouseholdService;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.fabric.persistence.DaycareRepository;
import vn.svframe.svrelationships.fabric.persistence.LineageRepository;
import vn.svframe.svrelationships.fabric.persistence.RelationshipRepository;
import vn.svframe.svrelationships.fabric.persistence.RewardClaimRepository;
import vn.svframe.svrelationships.fabric.relationship.LifeInteractionService;
import vn.svframe.svrelationships.fabric.relationship.PartnershipService;
import vn.svframe.svrelationships.fabric.relationship.RelationshipService;
import vn.svframe.svrelationships.fabric.reward.RelationshipRewardService;
import vn.svframe.svrelationships.integration.ProviderHub;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeCoordinator implements AutoCloseable {
    private final ConfigService config;
    private final GameplayDefinitionService gameplay;
    private final RelationshipRuleService rules;
    private final GuiDefinitionService guiDefinitions;
    private final MessageService messages;
    private final HouseholdService households;
    private final ProviderHub providers;
    private final RelationshipRepository relationshipsRepository;
    private final DaycareRepository daycareRepository;
    private final LineageRepository lineageRepository;
    private final RewardClaimRepository rewardClaims;
    private final RelationshipService relationships;
    private final LifeInteractionService interactions;
    private final PartnershipService partnerships;
    private final RelationshipRewardService rewards;
    private final RuntimeMetrics metrics = new RuntimeMetrics();
    private final AtomicReference<DaycareRuntimeService> daycare = new AtomicReference<>();
    private final AtomicReference<ServerGuiService> guis = new AtomicReference<>();
    private final AtomicReference<HouseholdMaterializationService> materialization = new AtomicReference<>();

    public RuntimeCoordinator(ConfigService config, GameplayDefinitionService gameplay, RelationshipRuleService rules,
                              GuiDefinitionService guiDefinitions, MessageService messages, HouseholdService households,
                              ProviderHub providers, RelationshipRepository relationshipsRepository,
                              DaycareRepository daycareRepository, LineageRepository lineageRepository,
                              RewardClaimRepository rewardClaims) {
        this.config = config;
        this.gameplay = gameplay;
        this.rules = rules;
        this.guiDefinitions = guiDefinitions;
        this.messages = messages;
        this.households = households;
        this.providers = providers;
        this.relationshipsRepository = relationshipsRepository;
        this.daycareRepository = daycareRepository;
        this.lineageRepository = lineageRepository;
        this.rewardClaims = rewardClaims;
        this.relationships = new RelationshipService(relationshipsRepository, gameplay, providers);
        this.interactions = new LifeInteractionService(gameplay, relationships);
        this.partnerships = new PartnershipService(gameplay, rules, relationships, households, providers);
        this.rewards = new RelationshipRewardService(config, gameplay, relationships, providers, rewardClaims);
    }

    public void start(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        daycare.set(new DaycareRuntimeService(server, config, gameplay, daycareRepository, lineageRepository, relationships, providers));
        guis.set(new ServerGuiService(guiDefinitions, messages, providers, relationships, interactions, partnerships, rewards));
        materialization.set(new HouseholdMaterializationService(server, households, relationships, metrics));
    }

    public void tick(long nowMillis) {
        long started = System.nanoTime();
        DaycareRuntimeService daycareService = daycare.get();
        if (daycareService != null) daycareService.tick(nowMillis);
        HouseholdMaterializationService householdService = materialization.get();
        if (householdService != null) householdService.tick();
        metrics.add("runtime.tick_nanos", System.nanoTime() - started);
        metrics.increment("runtime.tick_count");
        metrics.gauge("relationship.total", relationshipsRepository.snapshot().size());
    }

    public ReloadResult reloadAll() {
        var gameplayResult = gameplay.reload();
        if (!gameplayResult.success()) return new ReloadResult(false, gameplayResult.detail());
        var rulesResult = rules.reload();
        if (!rulesResult.success()) return new ReloadResult(false, rulesResult.detail());
        var guiResult = guiDefinitions.reload();
        if (!guiResult.success()) return new ReloadResult(false, guiResult.detail());
        var configResult = config.reload();
        if (!configResult.success()) return new ReloadResult(false, configResult.detail());
        metrics.increment("config.reload.success");
        return new ReloadResult(true, "");
    }

    public RelationshipService relationships() { return relationships; }
    public LifeInteractionService interactions() { return interactions; }
    public PartnershipService partnerships() { return partnerships; }
    public RelationshipRewardService rewards() { return rewards; }
    public Optional<DaycareRuntimeService> daycare() { return Optional.ofNullable(daycare.get()); }
    public Optional<ServerGuiService> guis() { return Optional.ofNullable(guis.get()); }
    public LineageRepository lineage() { return lineageRepository; }
    public RelationshipRuleService rules() { return rules; }
    public GuiDefinitionService guiDefinitions() { return guiDefinitions; }
    public RuntimeMetrics metrics() { return metrics; }

    @Override
    public void close() {
        relationshipsRepository.close();
        daycareRepository.close();
    }

    public record ReloadResult(boolean success, String detail) {}
}
