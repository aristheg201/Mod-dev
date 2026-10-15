package dev.svextract.mission;

import java.util.*;

/** Bounded tactical intent planner for rival Extractors; the world adapter owns movement and fire.
 * One decision per configured interval, no chunk scans, no client-authoritative AI actions.
 */
public final class ExtractorBotBrain {
    public enum Goal { DEPLOY, LOOT, INVESTIGATE, ENGAGE, TAKE_COVER, RELOAD, HEAL, EXTRACT, EVACUATED }
    public record Context(double healthFraction,int magazineRounds,int reserveRounds,boolean enemyVisible,
                          boolean underFire,boolean coverAvailable,boolean lootNearby,int carriedValue,
                          int remainingSeconds,boolean extractionAvailable,boolean nearExtraction,boolean gunfireHeard) {
        public Context {
            if(!Double.isFinite(healthFraction)||healthFraction<0||healthFraction>1
               ||magazineRounds<0||reserveRounds<0||carriedValue<0||remainingSeconds<0)
               throw new IllegalArgumentException("Invalid bot tactical context");
        }
    }
    public record Decision(Goal goal,long validUntilTick) {}
    private final int decisionIntervalTicks;
    private final int valueToExtract;
    private Decision decision;
    private long lastDecisionTick=Long.MIN_VALUE;
    private boolean evacuated;

    public ExtractorBotBrain(int decisionIntervalTicks,int valueToExtract){
        if(decisionIntervalTicks<5||decisionIntervalTicks>200||valueToExtract<1||valueToExtract>1_000_000)
            throw new IllegalArgumentException("Invalid bot brain budget");
        this.decisionIntervalTicks=decisionIntervalTicks;this.valueToExtract=valueToExtract;
    }
    public Decision decide(Context input,long tick){
        Objects.requireNonNull(input);
        if(evacuated)return new Decision(Goal.EVACUATED,tick);
        if(decision!=null&&tick<lastDecisionTick)throw new IllegalArgumentException("Non-monotonic bot clock");
        if(decision!=null&&tick<decision.validUntilTick())return decision;
        lastDecisionTick=tick;
        Goal goal;
        if(input.nearExtraction()&&input.extractionAvailable()&&(input.remainingSeconds()<150||input.carriedValue()>=valueToExtract)){
            evacuated=true;goal=Goal.EVACUATED;
        }else if(input.underFire()&&input.healthFraction()<0.45&&input.coverAvailable())goal=Goal.TAKE_COVER;
        else if(input.healthFraction()<0.30&&!input.enemyVisible())goal=Goal.HEAL;
        else if(input.enemyVisible()&&input.magazineRounds()==0&&input.reserveRounds()>0)goal=Goal.RELOAD;
        else if(input.enemyVisible()&&input.magazineRounds()>0)goal=Goal.ENGAGE;
        else if(input.extractionAvailable()&&(input.remainingSeconds()<180||input.carriedValue()>=valueToExtract))goal=Goal.EXTRACT;
        else if(input.magazineRounds()==0&&input.reserveRounds()>0)goal=Goal.RELOAD;
        else if(input.lootNearby())goal=Goal.LOOT;
        else if(input.gunfireHeard())goal=Goal.INVESTIGATE;
        else goal=Goal.DEPLOY;
        decision=new Decision(goal,tick+decisionIntervalTicks);return decision;
    }
    public boolean evacuated(){return evacuated;}
}
