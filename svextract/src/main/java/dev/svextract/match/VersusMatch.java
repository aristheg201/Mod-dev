package dev.svextract.match;

import java.util.*;

/** Server-owned score and objective rules for Team Deathmatch and Domination.
 * Search-and-Destroy is governed by the existing DefusalMatch class.
 */
public final class VersusMatch {
    public enum Mode { TEAM_DEATHMATCH, DOMINATION }
    public enum Phase { COUNTDOWN, ACTIVE, FINISHED }
    public record Rules(Mode mode,int playersPerTeam,int scoreLimit,int matchSeconds,
                        int countdownSeconds,int respawnSeconds,int dominationScoreIntervalTicks) {
        public Rules {
            Objects.requireNonNull(mode);
            if(playersPerTeam<1||playersPerTeam>5||scoreLimit<1||scoreLimit>1000||matchSeconds<30||matchSeconds>3600
               ||countdownSeconds<0||countdownSeconds>120||respawnSeconds<1||respawnSeconds>60
               ||dominationScoreIntervalTicks<10||dominationScoreIntervalTicks>200)
                throw new IllegalArgumentException("Invalid Versus rules");
        }
        public static Rules tdm(){return new Rules(Mode.TEAM_DEATHMATCH,5,50,600,10,5,20);}
        public static Rules domination(){return new Rules(Mode.DOMINATION,5,200,900,10,5,20);}
    }
    public record Seat(UUID player,int team,boolean bot){
        public Seat{Objects.requireNonNull(player);if(team<0||team>1)throw new IllegalArgumentException("Invalid team");}
    }
    public record Score(int teamA,int teamB,int winner,Phase phase,long remainingTicks){ }
    private final Rules rules;
    private final Map<UUID,Seat> roster=new LinkedHashMap<>();
    private final Map<UUID,Long> respawnAt=new HashMap<>();
    private final int[] scores={0,0};
    private final int[] objectives={-1,-1,-1};
    private final long started;
    private long lastTick,lastScoringTick;
    private Phase phase=Phase.COUNTDOWN;
    private int winner=-2;

    public VersusMatch(Rules rules,List<Seat> seats,long now){
        this.rules=Objects.requireNonNull(rules);started=lastTick=lastScoringTick=now;
        int[] counts={0,0};
        for(Seat seat:seats){
            if(roster.putIfAbsent(seat.player(),seat)!=null)throw new IllegalArgumentException("Duplicate player seat");
            if(++counts[seat.team()]>rules.playersPerTeam())throw new IllegalArgumentException("Team size exceeded");
        }
        if(counts[0]!=rules.playersPerTeam()||counts[1]!=rules.playersPerTeam())throw new IllegalArgumentException("Both Versus teams must have exact roster size");
    }
    public Phase phase(){return phase;}
    public Rules rules(){return rules;}
    public int winner(){return winner;}
    public int score(int team){if(team<0||team>1)throw new IllegalArgumentException("Invalid team");return scores[team];}
    public boolean active(UUID id,long now){return roster.containsKey(id)&&phase==Phase.ACTIVE&&respawnAt.getOrDefault(id,0L)<=now;}
    public boolean isRegistered(UUID id){return roster.containsKey(id);}
    public boolean elimination(UUID victim,UUID attacker,long now){
        tick(now);
        if(phase!=Phase.ACTIVE||!active(victim,now))return false;
        if(attacker!=null&&!roster.containsKey(attacker))return false;
        int victimTeam=roster.get(victim).team();
        // Friendly fire and team eliminations are never scored as player kills.
        if(attacker!=null&&roster.get(attacker).team()==victimTeam)return false;
        respawnAt.put(victim,now+rules.respawnSeconds()*20L);
        if(attacker!=null&&roster.get(attacker).team()!=victimTeam&&rules.mode()==Mode.TEAM_DEATHMATCH)credit(roster.get(attacker).team(),1);
        return true;
    }
    public boolean capture(int point,int team){
        if(rules.mode()!=Mode.DOMINATION||phase!=Phase.ACTIVE||point<0||point>=3||team<0||team>1)return false;
        if(objectives[point]==team)return false;
        objectives[point]=team;return true;
    }
    public int owner(int point){if(point<0||point>=3)throw new IllegalArgumentException("Invalid objective");return objectives[point];}
    public void tick(long now){
        if(now<lastTick)throw new IllegalArgumentException("Non-monotonic server tick");
        lastTick=now;
        if(phase==Phase.FINISHED)return;
        long playStart=started+rules.countdownSeconds()*20L;
        if(now<playStart)return;
        phase=Phase.ACTIVE;
        if(rules.mode()==Mode.DOMINATION){
            long periods=(now-Math.max(lastScoringTick,playStart))/rules.dominationScoreIntervalTicks();
            if(periods>0){
                lastScoringTick=now-(now-Math.max(lastScoringTick,playStart))%rules.dominationScoreIntervalTicks();
                // Cap catch-up following a long server stall; scoring never creates an unbounded loop.
                int steps=(int)Math.min(10,periods);
                for(int point:objectives){if(phase==Phase.FINISHED)break;if(point>=0)credit(point,steps);}
            }
        }
        if(phase==Phase.FINISHED)return;
        if(now-playStart>=rules.matchSeconds()*20L){phase=Phase.FINISHED;winner=Integer.compare(scores[0],scores[1])>0?0:scores[1]>scores[0]?1:-1;}
    }
    private void credit(int team,int points){
        scores[team]=Math.min(rules.scoreLimit(),scores[team]+points);
        if(scores[team]>=rules.scoreLimit()){phase=Phase.FINISHED;winner=team;}
    }
    public Score snapshot(long now){
        long remaining=Math.max(0,started+(rules.countdownSeconds()+rules.matchSeconds())*20L-now);
        return new Score(scores[0],scores[1],winner,phase,remaining);
    }
}
