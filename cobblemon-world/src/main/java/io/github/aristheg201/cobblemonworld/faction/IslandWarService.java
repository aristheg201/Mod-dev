package io.github.aristheg201.cobblemonworld.faction;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class IslandWarService {
    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Bangkok");
    private static long ticks;
    private static String activeWeek = "";

    private IslandWarService() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(IslandWarService::tick);
    }

    private static void tick(MinecraftServer server) {
        if (++ticks % 1200 != 0) return;
        ZonedDateTime now = ZonedDateTime.now(SERVER_ZONE);
        String week = now.getYear() + "-" + now.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY && !week.equals(activeWeek)) {
            activeWeek = week;
            CobblemonWorldMod.LOGGER.info("Faction island war window activated for {}", week);
        }
    }

    public static String activeWeek() { return activeWeek; }
}
