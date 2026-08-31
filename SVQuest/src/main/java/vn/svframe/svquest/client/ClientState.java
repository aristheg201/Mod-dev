package vn.svframe.svquest.client;

import java.util.HashMap;
import java.util.Map;

public final class ClientState {
    private int questIndex;
    private final Map<String, Integer> progress = new HashMap<>();
    private boolean serverAvailable;

    public int questIndex() { return questIndex; }
    public int progress(String key) { return Math.max(0, progress.getOrDefault(key, 0)); }
    public boolean serverAvailable() { return serverAvailable; }

    public void markServerUnavailable() {
        serverAvailable = false;
    }

    public void apply(String encoded) {
        try {
            int nextIndex = 0;
            Map<String, Integer> next = new HashMap<>();
            for (String line : encoded.split("\\n")) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq);
                String value = line.substring(eq + 1);
                if (key.equals("questIndex")) nextIndex = parse(value, 0);
                else if (key.startsWith("p.")) next.put(key.substring(2), parse(value, 0));
            }
            questIndex = Math.max(0, nextIndex);
            progress.clear();
            progress.putAll(next);
            serverAvailable = true;
        } catch (Throwable ignored) {
        }
    }

    private static int parse(String v, int fallback) {
        try { return Integer.parseInt(v); }
        catch (Exception ignored) { return fallback; }
    }
}
