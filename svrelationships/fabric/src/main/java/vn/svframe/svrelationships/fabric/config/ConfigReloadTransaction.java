package vn.svframe.svrelationships.fabric.config;

import vn.svframe.svrelationships.fabric.gui.GuiDefinitionService;
import vn.svframe.svrelationships.gameplay.RouteDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Stages the complete configuration graph before mutating any live service.
 * All live commits execute synchronously on the server command thread only after the
 * staged graph is valid and the source files are confirmed unchanged.
 */
public final class ConfigReloadTransaction {
    private final Path root;
    private final ConfigService config;
    private final GameplayDefinitionService gameplay;
    private final RelationshipRuleService rules;
    private final RewardPolicyService rewardPolicies;
    private final DaycarePolicyService daycarePolicies;
    private final CeremonyDefinitionService ceremonies;
    private final AnniversaryDefinitionService anniversaries;
    private final ScheduleDefinitionService schedules;
    private final DialogueDefinitionService dialogues;
    private final GuiDefinitionService guis;

    public ConfigReloadTransaction(Path root, ConfigService config, GameplayDefinitionService gameplay,
                                   RelationshipRuleService rules, RewardPolicyService rewardPolicies,
                                   DaycarePolicyService daycarePolicies, CeremonyDefinitionService ceremonies,
                                   AnniversaryDefinitionService anniversaries, ScheduleDefinitionService schedules,
                                   DialogueDefinitionService dialogues, GuiDefinitionService guis) {
        this.root = Objects.requireNonNull(root, "root"); this.config=config; this.gameplay=gameplay; this.rules=rules;
        this.rewardPolicies=rewardPolicies; this.daycarePolicies=daycarePolicies; this.ceremonies=ceremonies;
        this.anniversaries=anniversaries; this.schedules=schedules; this.dialogues=dialogues; this.guis=guis;
    }

    public synchronized Result reload() {
        try {
            String fingerprint = fingerprint();
            Staged staged = stage();
            if (!fingerprint.equals(fingerprint())) return new Result(false, "Configuration changed while reload was being staged");
            validateGraph(staged);
            Result committed = commitLive();
            if (!committed.success()) return committed;
            return new Result(true, "");
        } catch (Exception exception) {
            return new Result(false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private Staged stage() {
        GameplayDefinitionService g = new GameplayDefinitionService(root); require(g.reload(), "gameplay");
        RelationshipRuleService r = new RelationshipRuleService(root); require(r.reload(), "relationship rules");
        RewardPolicyService rp = new RewardPolicyService(root); require(rp.reload(), "reward policies");
        DaycarePolicyService dp = new DaycarePolicyService(root); require(dp.reload(), "daycare policies");
        CeremonyDefinitionService c = new CeremonyDefinitionService(root); require(c.reload(), "ceremonies");
        AnniversaryDefinitionService a = new AnniversaryDefinitionService(root); require(a.reload(), "anniversaries");
        ScheduleDefinitionService s = new ScheduleDefinitionService(root); require(s.reload(), "schedules");
        DialogueDefinitionService d = new DialogueDefinitionService(root); require(d.reload(), "dialogues");
        GuiDefinitionService ui = new GuiDefinitionService(root); require(ui.reload(), "GUI definitions");
        ConfigService cfg = new ConfigService(root); require(cfg.reload(), "main configuration");
        return new Staged(cfg,g,r,rp,dp,c,a,s,d,ui);
    }

    private void validateGraph(Staged staged) {
        var gp=staged.gameplay.snapshot(); var rr=staged.rules.snapshot();
        String partnershipRoute=rr.partnership().routeId();
        if(!gp.routes().containsKey(partnershipRoute)) throw new IllegalArgumentException("Partnership references unknown route: "+partnershipRoute);
        for(var ceremony:staged.ceremonies.snapshot().definitions().values()){
            if(!rr.partnership().milestones().containsKey(ceremony.partnershipMilestone())) throw new IllegalArgumentException("Ceremony "+ceremony.id()+" references unknown milestone "+ceremony.partnershipMilestone());
            RouteDefinition route=gp.routes().get(ceremony.requiredRoute()); if(route==null) throw new IllegalArgumentException("Ceremony "+ceremony.id()+" references unknown route "+ceremony.requiredRoute());
            if(!ceremony.requiredState().isBlank()&&!route.states().containsKey(ceremony.requiredState())) throw new IllegalArgumentException("Ceremony "+ceremony.id()+" references unknown route state "+ceremony.requiredState());
        }
        for(var anniversary:staged.anniversaries.snapshot().definitions().values()) if(!anniversary.rewardProfile().isBlank()&&!gp.rewardProfiles().containsKey(anniversary.rewardProfile())) throw new IllegalArgumentException("Anniversary "+anniversary.id()+" references unknown reward profile "+anniversary.rewardProfile());
        staged.schedules.snapshot().personalityProfiles().forEach((personality,profile)->{if(!gp.personalities().containsKey(personality))throw new IllegalArgumentException("Schedule mapping references unknown personality "+personality);if(!staged.schedules.snapshot().profiles().containsKey(profile))throw new IllegalArgumentException("Schedule mapping references unknown profile "+profile);});
        for(var dialogue:staged.dialogues.snapshot().definitions().values()) if(!dialogue.requiredRoute().isBlank()){RouteDefinition route=gp.routes().get(dialogue.requiredRoute());if(route==null)throw new IllegalArgumentException("Dialogue "+dialogue.id()+" references unknown route "+dialogue.requiredRoute());if(!dialogue.requiredState().isBlank()&&!route.states().containsKey(dialogue.requiredState()))throw new IllegalArgumentException("Dialogue "+dialogue.id()+" references unknown route state "+dialogue.requiredState());}
        gp.daycareDefinitions().forEach((id,definition)->{var policy=staged.daycarePolicies.snapshot().policy(id);String source=policy.offspringSpeciesSource();if(source.startsWith("participant:")){String role=source.substring("participant:".length());if(!"first".equals(role)&&!definition.participantRoles().contains(role))throw new IllegalArgumentException("Daycare "+id+" species source references unknown participant role "+role);}});
    }

    private Result commitLive() {
        var g=gameplay.reload(); if(!g.success())return new Result(false,"gameplay commit: "+g.detail());
        var r=rules.reload(); if(!r.success())return new Result(false,"rules commit: "+r.detail());
        var rp=rewardPolicies.reload(); if(!rp.success())return new Result(false,"reward policy commit: "+rp.detail());
        var dp=daycarePolicies.reload(); if(!dp.success())return new Result(false,"daycare policy commit: "+dp.detail());
        var c=ceremonies.reload(); if(!c.success())return new Result(false,"ceremony commit: "+c.detail());
        var a=anniversaries.reload(); if(!a.success())return new Result(false,"anniversary commit: "+a.detail());
        var s=schedules.reload(); if(!s.success())return new Result(false,"schedule commit: "+s.detail());
        var d=dialogues.reload(); if(!d.success())return new Result(false,"dialogue commit: "+d.detail());
        var ui=guis.reload(); if(!ui.success())return new Result(false,"GUI commit: "+ui.detail());
        var cfg=config.reload(); if(!cfg.success())return new Result(false,"main config commit: "+cfg.detail());
        return new Result(true,"");
    }

    private String fingerprint() throws IOException, NoSuchAlgorithmException {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var paths=Files.walk(root)){
            for(Path path:paths.filter(Files::isRegularFile).filter(p->!p.startsWith(root.resolve("state"))).filter(p->!p.getFileName().toString().endsWith(".tmp")).sorted(Comparator.comparing(Path::toString)).toList()){
                digest.update(root.relativize(path).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                try(InputStream input=Files.newInputStream(path)){input.transferTo(new java.io.OutputStream(){@Override public void write(int b){digest.update((byte)b);}@Override public void write(byte[] b,int off,int len){digest.update(b,off,len);}});}
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void require(GameplayDefinitionService.ReloadResult result,String label){if(!result.success())throw new IllegalArgumentException(label+": "+result.detail());}
    private static void require(RelationshipRuleService.ReloadResult result,String label){if(!result.success())throw new IllegalArgumentException(label+": "+result.detail());}
    private static void require(RewardPolicyService.ReloadResult result,String label){if(!result.success())throw new IllegalArgumentException(label+": "+result.detail());}
    private static void require(DaycarePolicyService.ReloadResult result,String label){if(!result.success())throw new IllegalArgumentException(label+": "+result.detail());}
    private static void require(CeremonyDefinitionService.ReloadResult result,String label){if(!result.success())throw new IllegalArgumentException(label+": "+result.detail());}
    private static void require(AnniversaryDefinitionService.ReloadResult result,String label){if(!result.success())throw new IllegalArgumentException(label+": "+result.detail());}
    private static void require(ScheduleDefinitionService.ReloadResult result,String label){if(!result.success())throw new IllegalArgumentException(label+": "+result.detail());}
    private static void require(DialogueDefinitionService.ReloadResult result,String label){if(!result.success())throw new IllegalArgumentException(label+": "+result.detail());}
    private static void require(GuiDefinitionService.ReloadResult result,String label){if(!result.success())throw new IllegalArgumentException(label+": "+result.detail());}
    private static void require(ConfigService.ReloadResult result,String label){if(!result.success())throw new IllegalArgumentException(label+": "+result.detail());}

    private record Staged(ConfigService config,GameplayDefinitionService gameplay,RelationshipRuleService rules,RewardPolicyService rewardPolicies,DaycarePolicyService daycarePolicies,CeremonyDefinitionService ceremonies,AnniversaryDefinitionService anniversaries,ScheduleDefinitionService schedules,DialogueDefinitionService dialogues,GuiDefinitionService guis){}
    public record Result(boolean success,String detail){}
}
