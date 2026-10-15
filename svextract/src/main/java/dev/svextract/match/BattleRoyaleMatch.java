package dev.svextract.match;

import java.util.*;

/** Pure, authoritative BR match rules. World adapters perform damage, movement, and rendering.
 * State transitions are monotonic and independent of client-provided positions or timestamps.
 */
public final class BattleRoyaleMatch {
    public enum Phase { DEPLOYING, ACTIVE, FINISHED }
    public record Zone(int x,int z,double radius,int holdSeconds,int shrinkSeconds) {
        public Zone {
            if(!Double.isFinite(radius)||radius<4||radius>20000||holdSeconds<0||holdSeconds>1800||shrinkSeconds<1||shrinkSeconds>1800)
                throw new IllegalArgumentException("Invalid safe zone");
        }
    }
    public record Rules(int maxParticipants,int teamSize,int deploymentSeconds,int timeLimitSeconds,
                        int outOfZoneDamage,int zoneDamageIntervalTicks,List<Zone> zones) {
        public Rules {
            Objects.requireNonNull(zones);
            zones=List.copyOf(zones);
            if(maxParticipants<2||maxParticipants>64||teamSize<1||teamSize>4||maxParticipants%teamSize!=0
               ||deploymentSeconds<1||deploymentSeconds>180||timeLimitSeconds<60||timeLimitSeconds>7200
               ||outOfZoneDamage<1||outOfZoneDamage>100||zoneDamageIntervalTicks<10||zoneDamageIntervalTicks>200
               ||zones.size()<2||zones.size()>12) throw new IllegalArgumentException("Invalid BR rules");
            for(int i=1;i<zones.size();i++) {
                Zone prior=zones.get(i-1), next=zones.get(i);
                if(next.radius()>=prior.radius())throw new IllegalArgumentException("BR zones must shrink");
                // A target circle must fit inside the previous safe region, otherwise the safe zone teleports into danger.
                if(Math.hypot(next.x()-prior.x(),next.z()-prior.z()) + next.radius()>prior.radius()+0.00001)
                    throw new IllegalArgumentException("Next BR safe zone must fit inside previous zone");
            }
        }
        public static Rules defaults(){return new Rules(16,4,15,1500,3,20,List.of(
            new Zone(0,0,160,60,90),new Zone(20,-10,105,50,80),new Zone(34,-8,68,45,65),
            new Zone(37,0,40,35,55),new Zone(38,4,18,20,40),new Zone(38,5,7,0,35)));
        }
    }
    public record Participant(UUID id,int team,boolean bot) {
        public Participant {Objects.requireNonNull(id);if(team<0||team>63)throw new IllegalArgumentException("Invalid team");}
    }
    public record SafeZone(double x,double z,double radius,int stage,boolean shrinking,int remainingSeconds) {
        public boolean contains(double px,double pz){return Math.hypot(px-x,pz-z)<=radius;}
    }
    public record Elimination(UUID victim,UUID attacker,long tick) {}
    private final Rules rules;
    private final LinkedHashMap<UUID,Participant> roster=new LinkedHashMap<>();
    private final Set<UUID> alive=new LinkedHashSet<>();
    private final List<Elimination> events=new ArrayList<>();
    private final long createdAt;
    private Phase phase=Phase.DEPLOYING;
    private long lastTick;
    private int winner=-2;
    private int lastDamageTick=-1;

    public BattleRoyaleMatch(Rules rules,List<Participant> seats,long now) {
        this.rules=Objects.requireNonNull(rules);
        if(seats.size()<2||seats.size()>rules.maxParticipants())throw new IllegalArgumentException("Invalid BR roster size");
        Map<Integer,Integer> teamCounts=new HashMap<>();
        for(Participant participant:seats) {
            if(roster.putIfAbsent(participant.id(),participant)!=null)throw new IllegalArgumentException("Duplicate BR participant");
            if(teamCounts.merge(participant.team(),1,Integer::sum)>rules.teamSize())throw new IllegalArgumentException("BR squad exceeds capacity");
            alive.add(participant.id());
        }
        if(teamCounts.size()<2)throw new IllegalArgumentException("At least two rival teams required");
        createdAt=lastTick=now;
    }
    public Phase phase(){return phase;}
    public int participants(){return roster.size();}
    public int aliveCount(){return alive.size();}
    public boolean alive(UUID id){return alive.contains(id);}
    public int survivingTeams(){return (int)alive.stream().map(id->roster.get(id).team()).distinct().count();}
    /** -2 means unfinished; -1 means draw; otherwise the winning squad index. */
    public int winner(){return winner;}
    public List<Elimination> eliminations(){return List.copyOf(events);}
    public Collection<Participant> participantsSnapshot(){return List.copyOf(roster.values());}
    public void tick(long now){
        if(now<lastTick)throw new IllegalArgumentException("Non-monotonic server tick");
        lastTick=now;
        if(phase==Phase.FINISHED)return;
        if(now-createdAt>=rules.timeLimitSeconds()*20L){phase=Phase.FINISHED;winner=-1;return;}
        if(phase==Phase.DEPLOYING&&now-createdAt>=rules.deploymentSeconds()*20L)phase=Phase.ACTIVE;
        if(phase==Phase.ACTIVE)decideWinner();
    }
    public boolean eliminate(UUID victim,UUID attacker,long now){
        tick(now);
        if(phase!=Phase.ACTIVE||!alive.contains(victim))return false;
        if(attacker!=null&&!roster.containsKey(attacker))throw new IllegalArgumentException("Unregistered attacker");
        alive.remove(victim);
        if(attacker!=null&&Objects.equals(roster.get(victim).team(),roster.get(attacker).team()))attacker=null;
        events.add(new Elimination(victim,attacker,now));decideWinner();return true;
    }
    private void decideWinner(){
        Set<Integer> remaining=new HashSet<>();for(UUID id:alive)remaining.add(roster.get(id).team());
        if(remaining.size()<=1){phase=Phase.FINISHED;winner=remaining.isEmpty()?-1:remaining.iterator().next();}
    }
    /** Current zone is derived from the server clock, avoiding one timer/task per phase. */
    public SafeZone zone(long now){
        long remaining=Math.max(0,now-createdAt-rules.deploymentSeconds()*20L);
        for(int i=0;i<rules.zones().size()-1;i++){
            Zone from=rules.zones().get(i),to=rules.zones().get(i+1);
            long hold=from.holdSeconds()*20L,shrink=from.shrinkSeconds()*20L;
            if(remaining<hold)return new SafeZone(from.x(),from.z(),from.radius(),i,false,(int)((hold-remaining+19)/20));
            remaining-=hold;
            if(remaining<shrink){
                double ratio=(double)remaining/shrink;
                return new SafeZone(from.x()+(to.x()-from.x())*ratio,from.z()+(to.z()-from.z())*ratio,
                    from.radius()+(to.radius()-from.radius())*ratio,i,true,(int)((shrink-remaining+19)/20));
            }
            remaining-=shrink;
        }
        Zone last=rules.zones().getLast();return new SafeZone(last.x(),last.z(),last.radius(),rules.zones().size()-1,false,0);
    }
    /** World adapters call at most once per damage interval, never once per player per game tick. */
    public boolean shouldApplyZoneDamage(long now){
        if(phase!=Phase.ACTIVE||now%rules.zoneDamageIntervalTicks()!=0)return false;
        int interval=(int)(now/rules.zoneDamageIntervalTicks());
        if(interval==lastDamageTick)return false;
        lastDamageTick=interval;return true;
    }
    public int zoneDamage(double x,double z,long now){
        if(phase!=Phase.ACTIVE)return 0;
        return zone(now).contains(x,z)?0:rules.outOfZoneDamage();
    }
}
