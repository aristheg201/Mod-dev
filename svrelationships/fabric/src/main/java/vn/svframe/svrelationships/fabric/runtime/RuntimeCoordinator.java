package vn.svframe.svrelationships.fabric.runtime;

import net.minecraft.server.MinecraftServer;
import vn.svframe.svrelationships.fabric.config.*;
import vn.svframe.svrelationships.fabric.diagnostics.RuntimeMetrics;
import vn.svframe.svrelationships.fabric.family.DaycareRuntimeService;
import vn.svframe.svrelationships.fabric.gui.GuiDefinitionService;
import vn.svframe.svrelationships.fabric.gui.ServerGuiService;
import vn.svframe.svrelationships.fabric.household.HouseholdMaterializationService;
import vn.svframe.svrelationships.fabric.household.HouseholdService;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.fabric.persistence.*;
import vn.svframe.svrelationships.fabric.relationship.*;
import vn.svframe.svrelationships.fabric.reward.RelationshipRewardService;
import vn.svframe.svrelationships.integration.ProviderHub;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeCoordinator implements AutoCloseable {
    private final ConfigService config;
    private final GameplayDefinitionService gameplay;
    private final RelationshipRuleService rules;
    private final RewardPolicyService rewardPolicies;
    private final DaycarePolicyService daycarePolicies;
    private final CeremonyDefinitionService ceremonyDefinitions;
    private final AnniversaryDefinitionService anniversaryDefinitions;
    private final ScheduleDefinitionService scheduleDefinitions;
    private final DialogueDefinitionService dialogueDefinitions;
    private final GuiDefinitionService guiDefinitions;
    private final MessageService messages;
    private final HouseholdService households;
    private final ProviderHub providers;
    private final RelationshipRepository relationshipsRepository;
    private final DaycareRepository daycareRepository;
    private final LineageRepository lineageRepository;
    private final RewardClaimRepository rewardClaims;
    private final ConfigReloadTransaction reloadTransaction;
    private final RelationshipService relationships;
    private final LifeInteractionService interactions;
    private final PartnershipService partnerships;
    private final RelationshipRewardService rewards;
    private final CeremonyService ceremonies;
    private final AnniversaryService anniversaries;
    private final ScheduleService schedules;
    private final DialogueService dialogues;
    private final RuntimeMetrics metrics = new RuntimeMetrics();
    private final AtomicReference<DaycareRuntimeService> daycare = new AtomicReference<>();
    private final AtomicReference<ServerGuiService> guis = new AtomicReference<>();
    private final AtomicReference<HouseholdMaterializationService> materialization = new AtomicReference<>();

    public RuntimeCoordinator(Path root, ConfigService config, GameplayDefinitionService gameplay, RelationshipRuleService rules,
                              RewardPolicyService rewardPolicies, DaycarePolicyService daycarePolicies,
                              CeremonyDefinitionService ceremonyDefinitions, AnniversaryDefinitionService anniversaryDefinitions,
                              ScheduleDefinitionService scheduleDefinitions, DialogueDefinitionService dialogueDefinitions,
                              GuiDefinitionService guiDefinitions, MessageService messages, HouseholdService households,
                              ProviderHub providers, RelationshipRepository relationshipsRepository,
                              DaycareRepository daycareRepository, LineageRepository lineageRepository,
                              RewardClaimRepository rewardClaims) {
        this.config=config;this.gameplay=gameplay;this.rules=rules;this.rewardPolicies=rewardPolicies;this.daycarePolicies=daycarePolicies;
        this.ceremonyDefinitions=ceremonyDefinitions;this.anniversaryDefinitions=anniversaryDefinitions;this.scheduleDefinitions=scheduleDefinitions;this.dialogueDefinitions=dialogueDefinitions;
        this.guiDefinitions=guiDefinitions;this.messages=messages;this.households=households;this.providers=providers;this.relationshipsRepository=relationshipsRepository;this.daycareRepository=daycareRepository;this.lineageRepository=lineageRepository;this.rewardClaims=rewardClaims;
        this.reloadTransaction=new ConfigReloadTransaction(root,config,gameplay,rules,rewardPolicies,daycarePolicies,ceremonyDefinitions,anniversaryDefinitions,scheduleDefinitions,dialogueDefinitions,guiDefinitions);
        this.relationships=new RelationshipService(relationshipsRepository,gameplay,providers);
        this.interactions=new LifeInteractionService(gameplay,relationships);
        this.partnerships=new PartnershipService(gameplay,rules,ceremonyDefinitions,relationships,households,providers);
        this.rewards=new RelationshipRewardService(config,gameplay,relationships,providers,rewardClaims,rewardPolicies);
        this.ceremonies=new CeremonyService(ceremonyDefinitions,relationships,partnerships,households);
        this.anniversaries=new AnniversaryService(anniversaryDefinitions,relationships,rewards);
        this.schedules=new ScheduleService(scheduleDefinitions,gameplay,relationships);
        this.dialogues=new DialogueService(dialogueDefinitions,gameplay,relationships);
    }

    public void start(MinecraftServer server){Objects.requireNonNull(server,"server");daycare.set(new DaycareRuntimeService(server,config,gameplay,daycarePolicies,daycareRepository,lineageRepository,relationships,providers,rewards));guis.set(new ServerGuiService(guiDefinitions,messages,providers,relationships,interactions,partnerships,rewards));materialization.set(new HouseholdMaterializationService(server,households,relationships,schedules,metrics));}
    public void tick(long nowMillis){long started=System.nanoTime();var d=daycare.get();if(d!=null)d.tick(nowMillis);var h=materialization.get();if(h!=null)h.tick();metrics.add("runtime.tick_nanos",System.nanoTime()-started);metrics.increment("runtime.tick_count");metrics.gauge("relationship.total",relationshipsRepository.size());metrics.gauge("reward.claims",rewardClaims.size());metrics.gauge("lineage.total",lineageRepository.size());}
    public ReloadResult reloadAll(){ConfigReloadTransaction.Result result=reloadTransaction.reload();if(result.success())metrics.increment("config.reload.success");else metrics.increment("config.reload.failure");return new ReloadResult(result.success(),result.detail());}
    public RelationshipService relationships(){return relationships;}public LifeInteractionService interactions(){return interactions;}public PartnershipService partnerships(){return partnerships;}public RelationshipRewardService rewards(){return rewards;}public CeremonyService ceremonies(){return ceremonies;}public AnniversaryService anniversaries(){return anniversaries;}public ScheduleService schedules(){return schedules;}public DialogueService dialogues(){return dialogues;}public Optional<DaycareRuntimeService> daycare(){return Optional.ofNullable(daycare.get());}public Optional<ServerGuiService> guis(){return Optional.ofNullable(guis.get());}public LineageRepository lineage(){return lineageRepository;}public RelationshipRuleService rules(){return rules;}public GuiDefinitionService guiDefinitions(){return guiDefinitions;}public RuntimeMetrics metrics(){return metrics;}
    @Override public void close(){rewardClaims.close();lineageRepository.close();relationshipsRepository.close();daycareRepository.close();}
    public record ReloadResult(boolean success,String detail){}
}
