package vn.svframe.svrelationships.fabric.runtime;

import net.minecraft.server.MinecraftServer;
import vn.svframe.svrelationships.fabric.config.ConfigService;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.config.RelationshipRuleService;
import vn.svframe.svrelationships.fabric.config.RewardPolicyService;
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
    private final ConfigService config; private final GameplayDefinitionService gameplay; private final RelationshipRuleService rules; private final RewardPolicyService rewardPolicies;
    private final GuiDefinitionService guiDefinitions; private final MessageService messages; private final HouseholdService households; private final ProviderHub providers;
    private final RelationshipRepository relationshipsRepository; private final DaycareRepository daycareRepository; private final LineageRepository lineageRepository; private final RewardClaimRepository rewardClaims;
    private final RelationshipService relationships; private final LifeInteractionService interactions; private final PartnershipService partnerships; private final RelationshipRewardService rewards;
    private final RuntimeMetrics metrics = new RuntimeMetrics();
    private final AtomicReference<DaycareRuntimeService> daycare = new AtomicReference<>(); private final AtomicReference<ServerGuiService> guis = new AtomicReference<>(); private final AtomicReference<HouseholdMaterializationService> materialization = new AtomicReference<>();

    public RuntimeCoordinator(ConfigService config, GameplayDefinitionService gameplay, RelationshipRuleService rules, RewardPolicyService rewardPolicies, GuiDefinitionService guiDefinitions, MessageService messages, HouseholdService households, ProviderHub providers, RelationshipRepository relationshipsRepository, DaycareRepository daycareRepository, LineageRepository lineageRepository, RewardClaimRepository rewardClaims) {
        this.config=config; this.gameplay=gameplay; this.rules=rules; this.rewardPolicies=rewardPolicies; this.guiDefinitions=guiDefinitions; this.messages=messages; this.households=households; this.providers=providers; this.relationshipsRepository=relationshipsRepository; this.daycareRepository=daycareRepository; this.lineageRepository=lineageRepository; this.rewardClaims=rewardClaims;
        this.relationships=new RelationshipService(relationshipsRepository, gameplay, providers); this.interactions=new LifeInteractionService(gameplay, relationships); this.partnerships=new PartnershipService(gameplay, rules, relationships, households, providers); this.rewards=new RelationshipRewardService(config, gameplay, relationships, providers, rewardClaims, rewardPolicies);
    }
    public void start(MinecraftServer server) { Objects.requireNonNull(server,"server"); daycare.set(new DaycareRuntimeService(server,config,gameplay,daycareRepository,lineageRepository,relationships,providers)); guis.set(new ServerGuiService(guiDefinitions,messages,providers,relationships,interactions,partnerships,rewards)); materialization.set(new HouseholdMaterializationService(server,households,relationships,metrics)); }
    public void tick(long nowMillis) { long started=System.nanoTime(); var d=daycare.get(); if(d!=null)d.tick(nowMillis); var h=materialization.get(); if(h!=null)h.tick(); metrics.add("runtime.tick_nanos",System.nanoTime()-started); metrics.increment("runtime.tick_count"); metrics.gauge("relationship.total",relationshipsRepository.size()); metrics.gauge("reward.claims",rewardClaims.size()); metrics.gauge("lineage.total",lineageRepository.size()); }
    public ReloadResult reloadAll() { var a=gameplay.reload(); if(!a.success())return new ReloadResult(false,a.detail()); var b=rules.reload(); if(!b.success())return new ReloadResult(false,b.detail()); var c=rewardPolicies.reload(); if(!c.success())return new ReloadResult(false,c.detail()); var d=guiDefinitions.reload(); if(!d.success())return new ReloadResult(false,d.detail()); var e=config.reload(); if(!e.success())return new ReloadResult(false,e.detail()); metrics.increment("config.reload.success"); return new ReloadResult(true,""); }
    public RelationshipService relationships(){return relationships;} public LifeInteractionService interactions(){return interactions;} public PartnershipService partnerships(){return partnerships;} public RelationshipRewardService rewards(){return rewards;} public Optional<DaycareRuntimeService> daycare(){return Optional.ofNullable(daycare.get());} public Optional<ServerGuiService> guis(){return Optional.ofNullable(guis.get());} public LineageRepository lineage(){return lineageRepository;} public RelationshipRuleService rules(){return rules;} public GuiDefinitionService guiDefinitions(){return guiDefinitions;} public RuntimeMetrics metrics(){return metrics;}
    @Override public void close(){ rewardClaims.close(); lineageRepository.close(); relationshipsRepository.close(); daycareRepository.close(); }
    public record ReloadResult(boolean success,String detail){}
}
