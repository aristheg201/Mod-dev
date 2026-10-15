package dev.svextract.match;

import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;

/** Server-thread roster construction only. No fake player UUIDs, world scans, or entity spawning.
 * Actual match admission must additionally reserve equipment and obtain a prepared arena/AI budget.
 */
public final class Matchmaker {
    public enum Mode { BATTLEGROUND_SOLO, BATTLEGROUND_SQUAD, VERSUS }
    public record Member(UUID player,int level) { public Member { Objects.requireNonNull(player);if(level<1)throw new IllegalArgumentException("Level must be authoritative and positive"); } }
    public record Bot(UUID id,int team,int slot) {}
    public record Team(int index,List<UUID> players,List<Bot> bots) { public Team { players=List.copyOf(players);bots=List.copyOf(bots); } }
    public record Match(UUID id,Mode mode,List<Team> teams,long waitedNanos) { public Match { teams=List.copyOf(teams); } }
    public record Settings(int battlegroundSlots,int maxQueuedPlayers,int levelWindow,Duration battlegroundWait) {
        public Settings {
            if(battlegroundSlots<4||battlegroundSlots>60||battlegroundSlots%4!=0)throw new IllegalArgumentException("Battleground capacity must be a multiple of four between 4 and 60");
            if(maxQueuedPlayers<60||maxQueuedPlayers>256||levelWindow<0||levelWindow>1000||battlegroundWait.isNegative()||battlegroundWait.compareTo(Duration.ofMinutes(10))>0)throw new IllegalArgumentException("Invalid bounded matchmaking settings");
        }
        public static Settings defaults(){return new Settings(60,120,20,Duration.ofMinutes(2));}
    }
    private record Ticket(UUID id,Mode mode,List<Member> members,long joined) {}
    private final Settings settings;
    private final LongSupplier clock;
    private final LinkedHashMap<UUID,Ticket> tickets=new LinkedHashMap<>();
    private final Map<UUID,UUID> playerTickets=new HashMap<>();
    public Matchmaker(Settings settings,LongSupplier monotonicClock){this.settings=settings;clock=monotonicClock;}
    public UUID join(Mode mode,List<Member> party){
        Objects.requireNonNull(mode);List<Member> members=List.copyOf(party);int teamSize=teamSize(mode);
        if(members.isEmpty()||members.size()>teamSize)throw new IllegalArgumentException("Party does not fit mode team size");
        if(playerTickets.size()+members.size()>settings.maxQueuedPlayers())throw new IllegalStateException("Matchmaking queue is full");
        Set<UUID> unique=new HashSet<>();
        for(Member member:members)if(!unique.add(member.player())||playerTickets.containsKey(member.player()))throw new IllegalStateException("Player is already queued or duplicated");
        int min=members.stream().mapToInt(Member::level).min().orElseThrow(),max=members.stream().mapToInt(Member::level).max().orElseThrow();
        if((long)max-min>settings.levelWindow())throw new IllegalArgumentException("Party level range exceeds configured suitability window");
        UUID id=UUID.randomUUID();Ticket ticket=new Ticket(id,mode,members,clock.getAsLong());tickets.put(id,ticket);
        for(Member member:members)playerTickets.put(member.player(),id);return id;
    }
    /** Disconnect/leave removes the complete party; stale members cannot enter a match. */
    public boolean leave(UUID player){UUID id=playerTickets.get(player);if(id==null)return false;remove(tickets.get(id));return true;}
    public Optional<Match> poll(Mode mode){
        Ticket oldest=tickets.values().stream().filter(t->t.mode()==mode).findFirst().orElse(null);
        if(oldest==null)return Optional.empty();
        int size=teamSize(mode),count=teamCount(mode);List<List<UUID>> teams=new ArrayList<>(count);
        for(int i=0;i<count;i++)teams.add(new ArrayList<>(size));
        int low=oldest.members().stream().mapToInt(Member::level).min().orElseThrow(),high=oldest.members().stream().mapToInt(Member::level).max().orElseThrow();
        List<Ticket> selected=new ArrayList<>();int humans=0;
        for(Ticket ticket:tickets.values()){
            if(ticket.mode()!=mode)continue;
            int minimum=ticket.members().stream().mapToInt(Member::level).min().orElseThrow(),maximum=ticket.members().stream().mapToInt(Member::level).max().orElseThrow();
            if((long)Math.max(high,maximum)-Math.min(low,minimum)>settings.levelWindow())continue;
            List<UUID> available=null;
            for(List<UUID> team:teams)if(team.size()+ticket.members().size()<=size&&(available==null||team.size()<available.size()))available=team;
            if(available==null)continue;
            for(Member member:ticket.members())available.add(member.player());selected.add(ticket);humans+=ticket.members().size();low=Math.min(low,minimum);high=Math.max(high,maximum);
            if(humans==size*count)break;
        }
        long waited=clock.getAsLong()-oldest.joined();
        long required=(mode==Mode.VERSUS?Duration.ofMinutes(2):settings.battlegroundWait()).toNanos();
        if(humans<size*count&&waited<required)return Optional.empty();
        UUID match=UUID.randomUUID();List<Team> roster=new ArrayList<>(count);
        for(int team=0;team<count;team++){
            List<Bot> bots=new ArrayList<>();
            for(int slot=teams.get(team).size();slot<size;slot++)bots.add(new Bot(UUID.nameUUIDFromBytes((match+":"+team+":"+slot).getBytes(java.nio.charset.StandardCharsets.UTF_8)),team,slot));
            roster.add(new Team(team,teams.get(team),bots));
        }
        selected.forEach(this::remove);return Optional.of(new Match(match,mode,roster,Math.max(0,waited)));
    }
    private int teamSize(Mode mode){return switch(mode){case BATTLEGROUND_SOLO->1;case BATTLEGROUND_SQUAD->4;case VERSUS->5;};}
    private int teamCount(Mode mode){return switch(mode){case BATTLEGROUND_SOLO->settings.battlegroundSlots();case BATTLEGROUND_SQUAD->settings.battlegroundSlots()/4;case VERSUS->2;};}
    private void remove(Ticket ticket){tickets.remove(ticket.id());for(Member member:ticket.members())playerTickets.remove(member.player());}
    public int queuedPlayers(){return playerTickets.size();}
    public void clear(){tickets.clear();playerTickets.clear();}
}
