package vn.svframe.lively.dialogue;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DialogueSession {
    public enum Mode { CHOICE, FREE_TEXT, HYBRID }
    public record Choice(int id, String label, String semanticAction) {}
    public record Turn(boolean player, String text, Instant at) {}

    private final UUID sessionId = UUID.randomUUID();
    private final UUID playerId;
    private final UUID npcId;
    private final String npcName;
    private final Deque<Turn> history = new ArrayDeque<>();
    private final int historyLimit;
    private volatile long nonce = nextNonce();
    private volatile Instant expiresAt;
    private volatile Mode mode;
    private volatile List<Choice> choices = List.of();

    public DialogueSession(UUID playerId, UUID npcId, String npcName, Mode mode, int historyLimit, Instant expiresAt) {
        this.playerId = Objects.requireNonNull(playerId);
        this.npcId = Objects.requireNonNull(npcId);
        this.npcName = Objects.requireNonNull(npcName);
        this.mode = Objects.requireNonNull(mode);
        this.historyLimit = Math.max(4, historyLimit);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public UUID sessionId() { return sessionId; }
    public UUID playerId() { return playerId; }
    public UUID npcId() { return npcId; }
    public String npcName() { return npcName; }
    public long nonce() { return nonce; }
    public Instant expiresAt() { return expiresAt; }
    public Mode mode() { return mode; }
    public List<Choice> choices() { return choices; }
    public boolean expired() { return !expiresAt.isAfter(Instant.now()); }

    public synchronized void setChoices(List<Choice> next) {
        choices = List.copyOf(next);
        nonce = nextNonce();
    }

    public synchronized boolean consumeChoice(long suppliedNonce, int choiceId) {
        if (suppliedNonce != nonce || expired()) return false;
        boolean exists = choices.stream().anyMatch(choice -> choice.id() == choiceId);
        if (!exists) return false;
        nonce = nextNonce();
        return true;
    }

    public synchronized void record(boolean player, String text) {
        history.addLast(new Turn(player, text, Instant.now()));
        while (history.size() > historyLimit) history.removeFirst();
    }

    public synchronized List<Turn> history() { return List.copyOf(history); }

    private static long nextNonce() {
        long value = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        return value == 0L ? 1L : value;
    }
}
