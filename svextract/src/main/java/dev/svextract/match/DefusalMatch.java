package dev.svextract.match;

import java.util.*;

/** Deterministic server-owned round rules. Time is server ticks; positions and interactions are validated by the world adapter. */
public final class DefusalMatch {
    public enum Phase { BUY, LIVE, ROUND_OVER, FINISHED }
    public record Rules(int buySeconds,int roundSeconds,int bombSeconds,int plantSeconds,int defuseSeconds,int kitDefuseSeconds,int intermissionSeconds,int roundsToWin,int halfRounds,int maxRounds,int startingCash,int maximumCash,int winCash,int lossCash,int lossStep,int maximumLossCash,int killCash,int plantCash){
        public Rules {
            if(buySeconds<1||buySeconds>60||roundSeconds<10||roundSeconds>600||bombSeconds<10||bombSeconds>120||plantSeconds<1||plantSeconds>10||defuseSeconds<1||defuseSeconds>30||kitDefuseSeconds<1||kitDefuseSeconds>defuseSeconds||intermissionSeconds<1||intermissionSeconds>30||roundsToWin<1||roundsToWin>30||halfRounds<1||halfRounds>30||maxRounds!=halfRounds*2||roundsToWin>maxRounds||startingCash<0||maximumCash<startingCash||maximumCash>100000||winCash<0||lossCash<0||lossStep<0||maximumLossCash<lossCash||killCash<0||plantCash<0)throw new IllegalArgumentException("Invalid bounded defusal rules");
        }
        public static Rules load(java.nio.file.Path path)throws java.io.IOException{
            var json=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            if(!java.nio.file.Files.exists(path)){java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,json.toJson(defaults())+"\n");}
            if(java.nio.file.Files.size(path)>16384)throw new java.io.IOException(path+": defusal rules exceed 16 KiB");
            try{Rules rules=json.fromJson(java.nio.file.Files.readString(path),Rules.class);if(rules==null)throw new IllegalArgumentException("Missing rules");return rules;}
            catch(RuntimeException e){throw new java.io.IOException(path+": "+e.getMessage(),e);}
        }
        public static Rules defaults(){return new Rules(15,115,40,3,10,5,5,13,12,24,800,16000,3250,1400,500,3400,300,300);}
    }
    private static final class Player {final int team;boolean alive=true,kit;int cash;Player(int team,int cash){this.team=team;this.cash=cash;}}
    private final Rules rules;
    private final LinkedHashMap<UUID,Player> players=new LinkedHashMap<>();
    private final int[] scores={0,0},lossStreak={0,0};
    private Phase phase=Phase.BUY;
    private int round=1,winner=-1,roundWinner=-1,site=-1;
    private long deadline,plantStarted=-1,defuseStarted=-1;
    private UUID carrier,planter,defuser;
    private boolean planted;
    public DefusalMatch(Rules rules,Map<UUID,Integer> roster,long now){
        this.rules=rules;
        if(roster.size()!=10||Collections.frequency(new ArrayList<>(roster.values()),0)!=5||Collections.frequency(new ArrayList<>(roster.values()),1)!=5)throw new IllegalArgumentException("Defusal requires exactly two teams of five seats");
        roster.forEach((id,team)->players.put(Objects.requireNonNull(id),new Player(team,rules.startingCash)));beginRound(now);
    }
    private Player player(UUID id){Player p=players.get(id);if(p==null)throw new IllegalArgumentException("Unknown match seat");return p;}
    private void beginRound(long now){
        phase=Phase.BUY;deadline=now+rules.buySeconds*20L;planted=false;site=-1;planter=null;defuser=null;plantStarted=defuseStarted=-1;roundWinner=-1;
        if(round==rules.halfRounds+1){Arrays.fill(lossStreak,0);for(Player p:players.values())p.cash=rules.startingCash;}
        for(Player p:players.values()){p.alive=true;p.kit=false;}
        carrier=players.entrySet().stream().filter(e->e.getValue().team==attackingTeam()).findFirst().orElseThrow().getKey();
    }
    public void tick(long now,UUID plantActor,int plantSite,UUID defuseActor){
        if(phase==Phase.FINISHED)return;
        if(phase==Phase.ROUND_OVER){if(now>=deadline){round++;beginRound(now);}return;}
        if(phase==Phase.BUY){if(now>=deadline){phase=Phase.LIVE;deadline=now+rules.roundSeconds*20L;}return;}
        // Detonation wins if a defuse completes on the same server tick as the fuse.
        if(planted&&now>=deadline){endRound(attackingTeam(),now);return;}
        if(!planted){
            boolean valid=plantActor!=null&&plantActor.equals(carrier)&&alive(plantActor)&&plantSite>=0&&plantSite<2;
            if(!valid){planter=null;plantStarted=-1;site=-1;}
            else{
                if(!plantActor.equals(planter)||site!=plantSite){planter=plantActor;plantStarted=now;site=plantSite;}
                if(now-plantStarted>=rules.plantSeconds*20L){planted=true;deadline=now+rules.bombSeconds*20L;credit(plantActor,rules.plantCash);carrier=null;}
            }
        }else{
            boolean valid=defuseActor!=null&&alive(defuseActor)&&player(defuseActor).team!=attackingTeam();
            if(!valid){defuser=null;defuseStarted=-1;}
            else{
                if(!defuseActor.equals(defuser)){defuser=defuseActor;defuseStarted=now;}
                int seconds=player(defuseActor).kit?rules.kitDefuseSeconds:rules.defuseSeconds;
                if(now-defuseStarted>=seconds*20L){endRound(1-attackingTeam(),now);return;}
            }
        }
        int[] living={0,0};for(Player p:players.values())if(p.alive)living[p.team]++;
        if(living[1-attackingTeam()]==0){endRound(attackingTeam(),now);return;}
        if(!planted&&(living[attackingTeam()]==0||now>=deadline))endRound(1-attackingTeam(),now);
    }
    private void endRound(int team,long now){
        scores[team]++;roundWinner=team;lossStreak[team]=0;lossStreak[1-team]++;
        for(var e:players.entrySet())credit(e.getKey(),e.getValue().team==team?rules.winCash:(int)Math.min(rules.maximumLossCash,(long)rules.lossCash+(long)(lossStreak[1-team]-1)*rules.lossStep));
        if(scores[team]>=rules.roundsToWin||round>=rules.maxRounds){phase=Phase.FINISHED;winner=scores[0]==scores[1]?-1:scores[0]>scores[1]?0:1;}
        else{phase=Phase.ROUND_OVER;deadline=now+rules.intermissionSeconds*20L;}
    }
    public boolean eliminate(UUID victim,UUID killer){
        if(phase!=Phase.LIVE)return false;Player dead=player(victim);if(!dead.alive)return false;dead.alive=false;
        if(victim.equals(carrier))carrier=null;
        if(killer!=null&&players.containsKey(killer)&&player(killer).team!=dead.team)credit(killer,rules.killCash);
        return true;
    }
    public boolean pickUpBomb(UUID id){if(phase!=Phase.LIVE||planted||carrier!=null||!alive(id)||player(id).team!=attackingTeam())return false;carrier=id;return true;}
    public boolean purchase(UUID id,int price){Player p=player(id);if(price<0||phase!=Phase.BUY||!p.alive||p.cash<price)return false;p.cash-=price;return true;}
    public boolean hasKit(UUID id){return player(id).kit;}
    public void giveKit(UUID id){player(id).kit=true;}
    private void credit(UUID id,int amount){Player p=player(id);p.cash=(int)Math.min(rules.maximumCash,(long)p.cash+amount);}
    public boolean alive(UUID id){return players.containsKey(id)&&player(id).alive;}
    public boolean channeling(UUID id){return id!=null&&phase==Phase.LIVE&&(!planted&&id.equals(planter)&&plantStarted>=0||planted&&id.equals(defuser)&&defuseStarted>=0);}
    public int cash(UUID id){return player(id).cash;}
    public int team(UUID id){return player(id).team;}
    public int score(int team){return scores[team];}
    public int attackingTeam(){return round<=rules.halfRounds?0:1;}
    public int round(){return round;}
    public int winner(){return winner;}
    public int roundWinner(){return roundWinner;}
    public int site(){return site;}
    public UUID carrier(){return carrier;}
    public boolean planted(){return planted;}
    public Phase phase(){return phase;}
    public int remainingSeconds(long now){return (int)Math.max(0,(deadline-now+19)/20);}
    public int channelSeconds(long now){return phase!=Phase.LIVE?0:planted&&defuseStarted>=0?(int)Math.max(0,(player(defuser).kit?rules.kitDefuseSeconds:rules.defuseSeconds)-(now-defuseStarted)/20):!planted&&plantStarted>=0?(int)Math.max(0,rules.plantSeconds-(now-plantStarted)/20):0;}
}
