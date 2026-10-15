package dev.svextract.match;

import dev.svextract.persistence.RaidJournal;
import java.util.*;

/** Durable membership proposals and bounded session invitations. UUIDs are real server players. */
public final class LobbyParties {
    private record Invite(UUID leader,long expires) {}
    private RaidJournal.Lobby state=new RaidJournal.Lobby(0,List.of());
    private final Map<UUID,Invite> invites=new HashMap<>();
    public void restore(RaidJournal.Lobby state){this.state=state;}
    public RaidJournal.Lobby state(){return state;}
    public Optional<RaidJournal.Squad> squad(UUID player){return state.squads().stream().filter(s->s.members().contains(player)).findFirst();}
    public void invite(UUID leader,UUID target,long now){
        expire(now);if(leader.equals(target)||squad(target).isPresent())throw new IllegalArgumentException("Target is already in a squad or is yourself");
        RaidJournal.Squad current=squad(leader).orElse(null);
        if(current!=null&&(!current.leader().equals(leader)||current.members().size()>=5))throw new IllegalArgumentException("Only a leader with a free squad slot can invite");
        if(invites.size()>=256&&!invites.containsKey(target))throw new IllegalStateException("Invite budget reached");
        invites.put(target,new Invite(leader,now+120_000_000_000L));
    }
    public UUID inviter(UUID target,long now){expire(now);Invite invite=invites.get(target);if(invite==null)throw new IllegalStateException("Invitation expired or is unavailable");return invite.leader();}
    public RaidJournal.Lobby accept(UUID target,long now){
        UUID leader=inviter(target,now);if(squad(target).isPresent())throw new IllegalStateException("Player already belongs to a squad");
        List<RaidJournal.Squad> squads=new ArrayList<>(state.squads());RaidJournal.Squad current=squad(leader).orElse(null);
        if(current!=null&&(!current.leader().equals(leader)||current.members().size()>=5))throw new IllegalStateException("Inviter changed squad or the squad is full");
        List<UUID> members=new ArrayList<>(current==null?List.of(leader):current.members());members.add(target);
        if(current!=null)squads.remove(current);
        squads.add(new RaidJournal.Squad(current==null?UUID.randomUUID():current.id(),leader,members));return new RaidJournal.Lobby(state.revision(),squads);
    }
    public RaidJournal.Lobby leave(UUID player){
        RaidJournal.Squad current=squad(player).orElseThrow(()->new IllegalStateException("Player is not in a squad"));
        List<RaidJournal.Squad> squads=new ArrayList<>(state.squads());squads.remove(current);List<UUID> members=new ArrayList<>(current.members());members.remove(player);
        if(!members.isEmpty())squads.add(new RaidJournal.Squad(current.id(),current.leader().equals(player)?members.getFirst():current.leader(),members));
        return new RaidJournal.Lobby(state.revision(),squads);
    }
    public void committed(RaidJournal.Lobby durable,UUID changedPlayer){state=durable;invites.remove(changedPlayer);invites.entrySet().removeIf(e->e.getValue().leader().equals(changedPlayer));}
    public void expire(long now){invites.entrySet().removeIf(e->now-e.getValue().expires()>=0);}
    public void clear(){invites.clear();state=new RaidJournal.Lobby(0,List.of());}
}
