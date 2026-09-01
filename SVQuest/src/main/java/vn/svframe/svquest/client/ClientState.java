package vn.svframe.svquest.client;

import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Client mirror of quest state plus chunked server catalog synchronisation. */
public final class ClientState {
    private final Map<String, Long> progress = new HashMap<>();
    private final Set<String> claimed = new HashSet<>();
    private final Map<Integer, String> catalogParts = new HashMap<>();
    private int catalogTotal;
    private boolean serverAvailable;

    public long progress(String key) { return Math.max(0, progress.getOrDefault(key, 0L)); }
    public boolean claimed(String questId) { return claimed.contains(questId); }
    public int claimedCount() { return claimed.size(); }
    public Set<String> claimedView() { return Set.copyOf(claimed); }
    public boolean serverAvailable() { return serverAvailable; }
    public void markServerUnavailable() { serverAvailable = false; }

    public void acceptCatalogChunk(String encoded) {
        try {
            if (encoded == null) return;
            int colon = encoded.indexOf(':');
            int slash = colon < 0 ? -1 : encoded.lastIndexOf('/', colon);
            if (colon <= 0 || slash <= 0) return;
            int index = Integer.parseInt(encoded.substring(0, slash));
            int total = Integer.parseInt(encoded.substring(slash + 1, colon));
            if (total <= 0 || total > 4096 || index < 0 || index >= total) return;

            if (catalogTotal != total) {
                catalogParts.clear();
                catalogTotal = total;
            }
            catalogParts.put(index, encoded.substring(colon + 1));
            if (catalogParts.size() != catalogTotal) return;

            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < catalogTotal; i++) {
                String part = catalogParts.get(i);
                if (part == null) return;
                joined.append(part);
            }
            int count = QuestCatalog.installRemoteCompressedBase64(joined.toString());
            catalogParts.clear();
            catalogTotal = 0;
            SVQuest.LOGGER.info("Installed server SVQuest catalog on client: {} quests.", count);
        } catch (Throwable t) {
            catalogParts.clear();
            catalogTotal = 0;
            SVQuest.LOGGER.warn("Could not install SVQuest server catalog: {}", t.toString());
        }
    }

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
