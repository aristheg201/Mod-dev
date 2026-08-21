package vn.svframe.lively.integration;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.economy.BusinessAccessPolicy;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Optional HoloDisplays projection for semantic state. Nothing is projected unless the structure/business/event
 * explicitly opts in, and hidden/illegal businesses are never leaked through public holograms.
 */
final class HologramProjectionService {
    private static final int UPDATE_TICKS = 100;
    private static final int MAX_PROJECTIONS = 256;
    private final Set<String> activeIds = new HashSet<>();
    private MinecraftServer server;
    private boolean installed;

    synchronized void install() {
        if (installed) return;
        installed = true;
        ServerLifecycleEvents.SERVER_STARTED.register(next -> server = next);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::stop);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer active) {
        if (server != active || !LivelyApi.holograms().available() || active.getTicks() % UPDATE_TICKS != 0) return;
        Set<String> next = new HashSet<>();
        projectStructures(next);
        projectBusinesses(next);
        projectEvents(next);
        for (String old : Set.copyOf(activeIds)) {
            if (!next.contains(old)) LivelyApi.holograms().remove(old);
        }
        activeIds.clear();
        activeIds.addAll(next);
    }

    private void projectStructures(Set<String> next) {
        for (SemanticStructureRegistry.Structure structure : LivelyApi.structures().snapshot().structures().values()) {
            if (next.size() >= MAX_PROJECTIONS) return;
            boolean optedIn = structure.capabilities().contains("hologram")
                    || structure.capabilities().contains("hologram_status")
                    || structure.points().containsKey("hologram");
            if (!optedIn) continue;
            Position position = position(structure, "hologram", "entrance");
            String id = safeId("structure:" + structure.id());
            List<String> lines = new ArrayList<>();
            lines.add(structure.id());
            lines.add(structure.type() + " • " + structure.state().name().toLowerCase(Locale.ROOT));
            if (structure.townId() != null && !structure.townId().isBlank()) lines.add("Town: " + structure.townId());
            if (LivelyApi.holograms().upsertText(id, structure.bounds().world(), position.x(), position.y(), position.z(), lines)) next.add(id);
        }
    }

    private void projectBusinesses(Set<String> next) {
        Map<String, SemanticStructureRegistry.Structure> structures = LivelyApi.structures().snapshot().structures();
        for (EconomyEngine.Business business : LivelyApi.economy().snapshot().businesses().values()) {
            if (next.size() >= MAX_PROJECTIONS) return;
            if (!Boolean.parseBoolean(business.facts().getOrDefault("hologram", "false"))) continue;
            if (BusinessAccessPolicy.hidden(business) || business.locationId() == null) continue;
            SemanticStructureRegistry.Structure structure = structures.get(business.locationId());
            if (structure == null) continue;
            Position position = position(structure, "business_hologram", "hologram", "entrance");
            String id = safeId("business:" + business.id());
            List<String> lines = List.of(business.name(), business.open() ? "OPEN" : "CLOSED");
            if (LivelyApi.holograms().upsertText(id, structure.bounds().world(), position.x(), position.y(), position.z(), lines)) next.add(id);
        }
    }

    private void projectEvents(Set<String> next) {
        Map<String, SemanticStructureRegistry.Structure> structures = LivelyApi.structures().snapshot().structures();
        for (WorldEventEngine.WorldEvent event : LivelyApi.events().activeEvents()) {
            if (next.size() >= MAX_PROJECTIONS) return;
            if (!Boolean.parseBoolean(event.facts().getOrDefault("hologram", "false")) || event.structureId() == null) continue;
            SemanticStructureRegistry.Structure structure = structures.get(event.structureId());
            if (structure == null) continue;
            Position position = position(structure, "event_hologram", "hologram", "entrance");
            String id = safeId("event:" + event.id());
            List<String> lines = List.of(event.seed(), event.category().name() + " • " + event.phase().name());
            if (LivelyApi.holograms().upsertText(id, structure.bounds().world(), position.x(), position.y(), position.z(), lines)) next.add(id);
        }
    }

    private void stop(MinecraftServer stopping) {
        if (server != stopping) return;
        if (LivelyApi.holograms().available()) for (String id : Set.copyOf(activeIds)) LivelyApi.holograms().remove(id);
        activeIds.clear();
        server = null;
    }

    private static Position position(SemanticStructureRegistry.Structure structure, String... points) {
        for (String point : points) {
            Position parsed = parse(structure.points().get(point));
            if (parsed != null) return parsed;
        }
        SemanticStructureRegistry.Bounds b = structure.bounds();
        return new Position((b.minX() + b.maxX() + 1D) / 2D, b.maxY() + 2.5D, (b.minZ() + b.maxZ() + 1D) / 2D);
    }

    private static Position parse(String value) {
        if (value == null || value.isBlank()) return null;
        String[] p = value.split(",");
        if (p.length < 3) return null;
        try { return new Position(Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim())); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static String safeId(String raw) {
        return "lively_" + raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.:-]", "_");
    }

    private record Position(double x, double y, double z) {}
}
