package io.github.aristheg201.cobblemonworld.faction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IslandWarState {
    public int schemaVersion = 1;
    public String weekId = "";
    public IslandWarPhase phase = IslandWarPhase.DORMANT;
    public String affinity = "fire";
    public String ownerFaction = "";
    public long phaseStartedEpochMillis = 0L;
    public boolean islandBuilt = false;

    public Map<String, Integer> gateScores = new LinkedHashMap<>();
    public Set<String> qualifiedFactions = new LinkedHashSet<>();
    public List<CapturePoint> capturePoints = new ArrayList<>();

    public void normalize() {
        if (weekId == null) weekId = "";
        if (phase == null) phase = IslandWarPhase.DORMANT;
        if (affinity == null || affinity.isBlank()) affinity = "fire";
        if (ownerFaction == null) ownerFaction = "";
        if (gateScores == null) gateScores = new LinkedHashMap<>();
        if (qualifiedFactions == null) qualifiedFactions = new LinkedHashSet<>();
        if (capturePoints == null) capturePoints = new ArrayList<>();
        while (capturePoints.size() < 5) capturePoints.add(new CapturePoint());
        while (capturePoints.size() > 5) capturePoints.remove(capturePoints.size() - 1);
        capturePoints.forEach(CapturePoint::normalize);
    }

    public static final class CapturePoint {
        public String ownerFaction = "";
        public String contestingFaction = "";
        public int progressSeconds = 0;

        public void normalize() {
            if (ownerFaction == null) ownerFaction = "";
            if (contestingFaction == null) contestingFaction = "";
            if (progressSeconds < 0) progressSeconds = 0;
        }
    }
}
