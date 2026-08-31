package vn.svframe.svquest.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Client mirror of v3 quest-id based state. */
public final class ClientState {
    private final Map<String, Long> progress = new HashMap<>();
    private final Set<String> claimed = new HashSet<>();
    private boolean serverAvailable;

    public long progress(String key) { return Math.max(0, progress.getOrDefault(key, 0L)); }
    public boolean claimed(String questId) { return claimed.contains(questId); }
    public int claimedCount() { return claimed.size(); }
    public Set<String> claimedView() { return Set.copyOf(claimed); }
    public boolean serverAvailable() { return serverAvailable; }
    public void markServerUnavailable() { serverAvailable = false; }

    public void apply(String encoded) {
        try {
            Map<String, Long> nextProgress = new HashMap<>();
            Set<String> nextClaimed = new HashSet<>();
            for (String line : encoded.split("\\n")) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq);
                String value = line.substring(eq + 1);
                if (key.equals("claimed")) {
                    for (String id : value.split(",")) if (!id.isBlank()) nextClaimed.add(id.trim());
                } else if (key.startsWith("p.")) {
                    nextProgress.put(key.substring(2), parse(value, 0));
                }
            }
            progress.clear(); progress.putAll(nextProgress);
            claimed.clear(); claimed.addAll(nextClaimed);
            serverAvailable = true;
        } catch (Throwable ignored) { }
    }

    private static long parse(String v, long fallback) {
        try { return Long.parseLong(v); }
        catch (Exception ignored) { return fallback; }
    }
}
