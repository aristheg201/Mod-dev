package vn.svframe.svarcade.matchmaking;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Generic bounded queue matcher with optional bot fill and arena allocation. */
public final class MatchmakingManager {
    public interface BotFactory { Participant create(Id definition, String team, int ordinal); }
    public record Match(Id queue, UUID session, List<UUID> parties, int players, int bots) { }

    private final ThreadGuard thread;
    private final DefinitionRegistry definitions;
    private final GenericGameRuntime runtime;
    private final BotFactory bots;
    private final Map<Id, QueueDefinition> queues;
    private final Map<Id, ArrayDeque<QueueParty>> waiting = new LinkedHashMap<>();
    private final Map<UUID, Id> partyQueue = new HashMap<>();
    private final Map<UUID, UUID> playerParty = new HashMap<>();
    private final ArrayDeque<Match> completed = new ArrayDeque<>();
    private final int eventCapacity;

    public MatchmakingManager(ThreadGuard thread, DefinitionRegistry definitions, GenericGameRuntime runtime,
                              Collection<QueueDefinition> queues, BotFactory bots, int eventCapacity) {
        this.thread = Objects.requireNonNull(thread); this.definitions = Objects.requireNonNull(definitions); this.runtime = Objects.requireNonNull(runtime); this.bots = Objects.requireNonNull(bots);
        if (eventCapacity < 1 || eventCapacity > 100000) throw new IllegalArgumentException("Match event capacity"); this.eventCapacity = eventCapacity;
        Map<Id,QueueDefinition> compiled = new LinkedHashMap<>();
        for (QueueDefinition queue : queues) {
            Definition definition = definitions.snapshot().requireAvailable(queue.definition());
            if (queue.targetPlayers() < definition.minPlayers() || queue.targetPlayers() > definition.maxPlayers()) throw new ConfigException("Queue target outside definition limits");
            if (compiled.putIfAbsent(queue.id(), queue) != null) throw new ConfigException("Duplicate queue: " + queue.id()); waiting.put(queue.id(), new ArrayDeque<>());
        }
        if (compiled.isEmpty()) throw new IllegalArgumentException("At least one queue required"); this.queues = Map.copyOf(compiled);
    }

    public void enqueue(Id queueId, QueueParty party) {
        thread.check(); QueueDefinition queue = queue(queueId); Objects.requireNonNull(party); ensureFresh(party);
        if (party.members().size() > queue.targetPlayers() || partyQueue.containsKey(party.id())) throw new IllegalStateException("Party cannot enter queue");
        waiting.get(queueId).addLast(party); partyQueue.put(party.id(), queueId); party.members().forEach(p -> playerParty.put(p.id(), party.id()));
    }
    public boolean cancel(UUID partyId) {
        thread.check(); Id queueId = partyQueue.remove(partyId); if (queueId == null) return false; QueueParty found = null;
        for (QueueParty party : waiting.get(queueId)) if (party.id().equals(partyId)) { found = party; break; }
        if (found == null) throw new IllegalStateException("Queue index corruption"); waiting.get(queueId).remove(found); found.members().forEach(p -> playerParty.remove(p.id(), partyId)); return true;
    }
    /** Direct challenge uses the same definition limits, bot-fill policy and arena allocator as queued matching. */
    public GenericSession challenge(Id queueId, QueueParty left, QueueParty right) {
        thread.check(); QueueDefinition queue = queue(queueId); Objects.requireNonNull(left); Objects.requireNonNull(right); ensureFresh(left); ensureFresh(right);
        Set<UUID> unique = new HashSet<>(); List<Participant> participants = new ArrayList<>(); List<UUID> parties = new ArrayList<>();
        for (QueueParty party : List.of(left, right)) {
            if (!parties.add(party.id())) throw new IllegalArgumentException("Challenge party duplicated");
            for (Participant participant : party.members()) {
                if (!unique.add(participant.id())) throw new IllegalArgumentException("Challenge participant duplicated"); participants.add(participant);
            }
        }
        if (participants.size() > queue.targetPlayers()) throw new IllegalArgumentException("Challenge exceeds target players");
        fillBots(queue, participants); if (participants.size() != queue.targetPlayers()) throw new IllegalStateException("Challenge cannot satisfy player target");
        GenericSession session = runtime.open(queue.definition(), freeArena(queue.definition()), participants);
        append(new Match(queue.id(), session.id(), List.copyOf(parties),
                (int)participants.stream().filter(p -> p.kind() == Participant.Kind.PLAYER).count(), (int)participants.stream().filter(p -> p.kind() == Participant.Kind.BOT).count()));
        return session;
    }
    public int tick(int maxMatches) {
        thread.check(); if (maxMatches < 1 || maxMatches > 1024) throw new IllegalArgumentException("Matchmaking work limit"); int made = 0;
        for (QueueDefinition queue : queues.values()) while (made < maxMatches) {
            List<QueueParty> selected = select(queue); if (selected.isEmpty()) break; List<Participant> participants = new ArrayList<>(); selected.forEach(p -> participants.addAll(p.members()));
            fillBots(queue, participants); if (participants.size() != queue.targetPlayers()) break; String arena;
            try { arena = freeArena(queue.definition()); } catch (IllegalStateException noArena) { break; }
            selected.forEach(this::removeSelected); GenericSession session;
            try { session = runtime.open(queue.definition(), arena, participants); }
            catch (RuntimeException failure) { for (int i = selected.size() - 1; i >= 0; i--) restoreFront(queue.id(), selected.get(i)); throw failure; }
            append(new Match(queue.id(), session.id(), selected.stream().map(QueueParty::id).toList(),
                    (int)participants.stream().filter(p -> p.kind() == Participant.Kind.PLAYER).count(), (int)participants.stream().filter(p -> p.kind() == Participant.Kind.BOT).count())); made++;
        }
        return made;
    }
    private void ensureFresh(QueueParty party) {
        if (partyQueue.containsKey(party.id())) throw new IllegalStateException("Party already queued");
        for (Participant member : party.members()) if (playerParty.containsKey(member.id())) throw new IllegalStateException("Player already queued");
    }
    private List<QueueParty> select(QueueDefinition queue) {
        int total = 0; List<QueueParty> selected = new ArrayList<>();
        for (QueueParty party : waiting.get(queue.id())) {
            if (total + party.members().size() > queue.targetPlayers()) continue; selected.add(party); total += party.members().size(); if (total == queue.targetPlayers()) break;
        }
        if (total == 0 || total < queue.targetPlayers() && !queue.botFill()) return List.of(); return List.copyOf(selected);
    }
    private void fillBots(QueueDefinition queue, List<Participant> participants) {
        if (!queue.botFill()) return; int ordinal = 0;
        while (participants.size() < queue.targetPlayers()) {
            Participant bot = bots.create(queue.definition(), queue.botTeam(), ordinal++);
            if (bot.kind() != Participant.Kind.BOT || participants.stream().anyMatch(p -> p.id().equals(bot.id()))) throw new IllegalStateException("Invalid bot factory output"); participants.add(bot);
        }
    }
    private void removeSelected(QueueParty party) {
        Id queueId = partyQueue.remove(party.id()); if (queueId == null || !waiting.get(queueId).remove(party)) throw new IllegalStateException("Queue selection corruption");
        party.members().forEach(p -> playerParty.remove(p.id(), party.id()));
    }
    private void restoreFront(Id queueId, QueueParty party) {
        waiting.get(queueId).addFirst(party); partyQueue.put(party.id(), queueId); party.members().forEach(p -> playerParty.put(p.id(), party.id()));
    }
    private String freeArena(Id definitionId) {
        Definition definition = definitions.snapshot().requireAvailable(definitionId); Set<ArenaRuntime.Key> owned = runtime.arenas().snapshot().keySet();
        return definition.arenas().keySet().stream().sorted().filter(arena -> !owned.contains(new ArenaRuntime.Key(definitionId, arena))).findFirst().orElseThrow(() -> new IllegalStateException("No free arena"));
    }
    private QueueDefinition queue(Id id) { QueueDefinition queue = queues.get(id); if (queue == null) throw new IllegalArgumentException("Unknown queue"); return queue; }
    private void append(Match match) { if (completed.size() == eventCapacity) completed.removeFirst(); completed.addLast(match); }
    public List<Match> drainMatches(int maximum) {
        thread.check(); if (maximum < 1 || maximum > 4096) throw new IllegalArgumentException("Match drain limit"); List<Match> result = new ArrayList<>();
        while (!completed.isEmpty() && result.size() < maximum) result.add(completed.removeFirst()); return List.copyOf(result);
    }
    public Map<Id,Integer> queuedParties() { thread.check(); Map<Id,Integer> result = new LinkedHashMap<>(); waiting.forEach((id, parties) -> result.put(id, parties.size())); return Map.copyOf(result); }
}
