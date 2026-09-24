package io.github.aristheg201.svarcade.engine;

/** All sources use one bounded proc pipeline, including reflect and periodic damage. */
public final class DamagePipeline {
    private DamagePipeline() {}
    public enum Type { PHYSICAL, MAGIC, TRUE }
    /** Shared deterministic scalar stage used by non-BattleUnit modes such as route defense. */
    public static double scalar(double base,double authoredMultiplier) {
        if(!Double.isFinite(base)||!Double.isFinite(authoredMultiplier))throw new IllegalArgumentException("Non-finite damage input");
        return Math.max(0,base*Math.max(0,authoredMultiplier));
    }
    public static double damage(BattleRuntime w,BattleUnit source,BattleUnit target,double base,Type type,boolean critical,double executeBelow,int depth) {
        if(!target.alive()||target.hasStatus("invulnerability")||base<=0)return 0;
        double amount=base*(critical?Math.max(1,source.stat(Stat.CRIT_MULTIPLIER)):1)*(1+source.stat(Stat.DAMAGE_AMPLIFICATION));
        if(type!=Type.TRUE){double resistance=target.stat(type==Type.PHYSICAL?Stat.ARMOR:Stat.SPDEF);
            double penetration=source.stat(type==Type.PHYSICAL?Stat.ARMOR_PENETRATION:Stat.MAGIC_PENETRATION);
            resistance*=1-Math.max(0,Math.min(1,penetration));
            amount*=resistance>=0?100/(100+resistance):2-100/(100-resistance);
            amount=Math.max(0,amount*(1-Math.max(0,Math.min(1,target.stat(Stat.DAMAGE_REDUCTION))))-target.stat(Stat.FLAT_REDUCTION));
        }
        double absorbed=Math.min(target.shield,amount);target.shield-=absorbed;amount-=absorbed;
        if(absorbed>0&&target.shield<=0)w.emit(BattleEvent.ON_SHIELD_BREAK,target,source,target,absorbed,depth+1);
        double actual=Math.min(target.hp,Math.max(0,amount));target.hp-=actual;
        if(target.alive()&&executeBelow>0&&target.healthFraction()<=executeBelow){actual+=target.hp;target.hp=0;}
        source.damageDone+=actual;
        if(actual>0){w.emit(BattleEvent.ON_DAMAGE,source,source,target,actual,depth+1);w.emit(BattleEvent.ON_DAMAGE_TAKEN,target,source,source,actual,depth+1);
            mana(w,target,target.stat(Stat.MANA_ON_HIT),depth+1);
            double vamp=source.stat(Stat.OMNIVAMP)+(type==Type.PHYSICAL?source.stat(Stat.LIFESTEAL):0);if(vamp>0)heal(w,source,source,actual*vamp,depth+1);
        }
        if(!target.alive()&&target.deadAt<0){target.deadAt=w.now();w.emit(BattleEvent.ON_DEATH,target,source,source,actual,depth+1);w.emit(BattleEvent.ON_KILL,source,source,target,actual,depth+1);w.cue("faint",target,null,"",0);}
        return actual;
    }
    public static double heal(BattleRuntime w,BattleUnit source,BattleUnit target,double amount,int depth){if(!target.alive())return 0;double actual=Math.min(target.stat(Stat.MAX_HP)-target.hp,Math.max(0,amount*(1+source.stat(Stat.HEAL_POWER))*(1-Math.max(0,Math.min(1,target.stat(Stat.HEAL_REDUCTION))))));target.hp+=actual;source.healingDone+=actual;if(actual>0)w.emit(BattleEvent.ON_HEAL,source,source,target,actual,depth+1);return actual;}
    public static void shield(BattleRuntime w,BattleUnit source,BattleUnit target,double amount,int depth){if(!target.alive()||amount<=0)return;target.shield=Math.min(1000000000,target.shield+amount);w.emit(BattleEvent.ON_SHIELD,source,source,target,amount,depth+1);}
    public static void mana(BattleRuntime w,BattleUnit target,double amount,int depth){if(!target.alive())return;double before=target.mana;target.mana=Math.max(0,Math.min(target.stat(Stat.MAX_MANA),before+amount));if(target.mana>before)w.emit(BattleEvent.ON_MANA_GAIN,target,target,target,target.mana-before,depth+1);if(before<target.stat(Stat.MAX_MANA)&&target.mana==target.stat(Stat.MAX_MANA))w.emit(BattleEvent.ON_MANA_FULL,target,target,target,target.mana,depth+1);}
}
