package vn.worldcomesalive.ai;

import vn.worldcomesalive.model.LivingWorld.*;
import java.util.*;

/**
 * Daily-life policy layered above generic GOAP.
 * It prevents every citizen from collapsing into the same tavern/work/home loop and makes
 * profession, age, household, needs, personality and time of day visibly matter.
 */
public final class NpcRoutineEngine {
    public record Choice(String execution,String reason,int duration){}

    public static Optional<Choice> choose(Npc n,Settlement s,long clock){
        int time=(int)Math.floorMod(clock,24000);
        int day=(int)(clock/24000);

        if(n.interruptUntil>clock)return Optional.empty();

        // Children live through household/community routines, never adult nightlife/work shifts.
        if(n.age<18){
            if(time>=12500||n.need("fatigue")>.72)return Optional.of(new Choice("home","child bedtime",900));
            if(n.need("hunger")>.42)return Optional.of(new Choice("home_meal","child meal",420));
            if(time>=8500&&time<12000)return Optional.of(new Choice("family_social","family time",360));
            if(Math.floorMod(n.id.hashCode()+day,3)==0&&time>=4000&&time<8500)return Optional.of(new Choice("visit_neighbor","child visiting household",500));
            return Optional.of(new Choice("home_leisure","child home activity",420));
        }

        int workStart=n.schedule.getOrDefault("work_start",2000);
        int workEnd=n.schedule.getOrDefault("work_end",9000);
        boolean workHours=time>=workStart&&time<workEnd&&!n.workplace.isBlank();

        // Basic needs outrank optional leisure, but not imminent danger (handled before this layer).
        if(n.need("fatigue")>.82||time>=n.schedule.getOrDefault("sleep",13000))
            return Optional.of(new Choice("sleep","sleep need",1200));
        if(n.need("hunger")>.62)
            return Optional.of(new Choice("home_meal","hunger",420));

        if(workHours){
            if(n.profession.equals("guard"))return Optional.of(new Choice("patrol","guard patrol",500));
            if(n.profession.equals("merchant"))return Optional.of(new Choice("work","trade shift",900));
            if(n.profession.equals("farmer"))return Optional.of(new Choice("work","field work",900));
            if(n.profession.equals("innkeeper")||n.profession.equals("cook")||n.profession.equals("baker"))
                return Optional.of(new Choice("work","service shift",900));
            return Optional.of(new Choice("work","profession shift",900));
        }

        // Evening domestic life has priority over the tavern for family-oriented citizens.
        if(time>=9000&&time<12500){
            if(n.lastFamilyDay!=day&&(n.trait("loyalty")>.45||n.trait("kindness")>.55))
                return Optional.of(new Choice("family_social","family evening",420));
            if(!n.pokemon.isEmpty()&&n.trait("curiosity")>.58&&Math.floorMod(n.id.hashCode()+day,4)==0)
                return Optional.of(new Choice("train","partner training",500));
            if(n.need("loneliness")>.45||n.trait("sociability")>.72)
                return Optional.of(new Choice("tavern","social evening",700));
            return Optional.of(new Choice("home_leisure","quiet evening",500));
        }

        // Daytime errands and visits. Deterministic per citizen/day, not random wandering.
        int cadence=Math.floorMod(n.id.hashCode()*31+day,7);
        if(time>=2500&&time<9000){
            if(cadence==0&&n.money>5)return Optional.of(new Choice("market","household errand",420));
            if(cadence==1&&n.trait("sociability")>.45)return Optional.of(new Choice("visit_neighbor","neighbor visit",500));
            if(cadence==2&&!n.pokemon.isEmpty())return Optional.of(new Choice("train","partner care",420));
            if(cadence==3&&n.profession.equals("resident"))return Optional.of(new Choice("community","community activity",500));
        }

        return Optional.empty();
    }

    private NpcRoutineEngine(){}
}
