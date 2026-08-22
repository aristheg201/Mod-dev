package vn.svframe.lively.admin;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.law.LawEnforcementEngine;
import vn.svframe.lively.society.SocietyApi;

import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Read-only operational view for wanted, warrant, custody and court state. */
public final class LawStatusCommandsBootstrap implements ModInitializer {
    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("livelylaw").requires(source -> LivelyApi.permissions().has(source, "lively.admin.law", 2))
                        .then(literal("status").executes(context -> status(context.getSource())))
                        .then(literal("wanted").executes(context -> wanted(context.getSource())))
                        .then(literal("warrants").executes(context -> warrants(context.getSource())))
                        .then(literal("custody").executes(context -> custody(context.getSource())))
                        .then(literal("cases").executes(context -> cases(context.getSource())))
                        .then(literal("inspect").then(argument("subject", StringArgumentType.word())
                                .executes(context -> inspect(context.getSource(), StringArgumentType.getString(context, "subject")))))));
    }

    private static int status(ServerCommandSource source) {
        LawEnforcementEngine.Snapshot snapshot = SocietyApi.law().snapshot();
        long activeWarrants = snapshot.warrants().values().stream()
                .filter(value -> value.status() == LawEnforcementEngine.WarrantStatus.ACTIVE).count();
        long activeCustody = snapshot.custody().values().stream()
                .filter(value -> value.status() == LawEnforcementEngine.CustodyStatus.DETAINED
                        || value.status() == LawEnforcementEngine.CustodyStatus.JAILED).count();
        long pendingCases = snapshot.courtCases().values().stream()
                .filter(value -> value.status() == LawEnforcementEngine.CourtStatus.FILED
                        || value.status() == LawEnforcementEngine.CourtStatus.HEARING).count();
        source.sendFeedback(() -> Text.literal("Law: wanted=" + snapshot.wanted().size() + " activeWarrants=" + activeWarrants
                + " custody=" + activeCustody + " pendingCases=" + pendingCases), false);
        return 1;
    }

    private static int wanted(ServerCommandSource source) {
        var values = SocietyApi.law().snapshot().wanted().values().stream()
                .sorted(Comparator.comparingInt(LawEnforcementEngine.WantedRecord::points).reversed()).limit(20).toList();
        if (values.isEmpty()) { source.sendFeedback(() -> Text.literal("No wanted records."), false); return 1; }
        values.forEach(value -> source.sendFeedback(() -> Text.literal(value.subject().uuid() + " " + value.jurisdiction()
                + " " + value.level() + " points=" + value.points() + " bounty=" + value.bounty()
                + " crimes=" + value.crimeIds().size()), false));
        return values.size();
    }

    private static int warrants(ServerCommandSource source) {
        var values = SocietyApi.law().activeWarrants().stream().limit(20).toList();
        if (values.isEmpty()) { source.sendFeedback(() -> Text.literal("No active warrants."), false); return 1; }
        values.forEach(value -> source.sendFeedback(() -> Text.literal(value.id() + " subject=" + value.subject().uuid()
                + " jurisdiction=" + value.jurisdiction() + " cause=" + score(value.probableCause())
                + " crimes=" + value.crimeIds().size()), false));
        return values.size();
    }

    private static int custody(ServerCommandSource source) {
        var values = SocietyApi.law().snapshot().custody().values().stream()
                .filter(value -> value.status() == LawEnforcementEngine.CustodyStatus.DETAINED
                        || value.status() == LawEnforcementEngine.CustodyStatus.JAILED)
                .sorted(Comparator.comparing(LawEnforcementEngine.Custody::arrestedAt).reversed()).limit(20).toList();
        if (values.isEmpty()) { source.sendFeedback(() -> Text.literal("No active custody records."), false); return 1; }
        values.forEach(value -> source.sendFeedback(() -> Text.literal(value.id() + " subject=" + value.subject().uuid()
                + " status=" + value.status() + " facility=" + value.facilityId() + " release=" + value.releaseAt()), false));
        return values.size();
    }

    private static int cases(ServerCommandSource source) {
        var values = SocietyApi.law().snapshot().courtCases().values().stream()
                .sorted(Comparator.comparing(LawEnforcementEngine.CourtCase::filedAt).reversed()).limit(20).toList();
        if (values.isEmpty()) { source.sendFeedback(() -> Text.literal("No court cases."), false); return 1; }
        values.forEach(value -> source.sendFeedback(() -> Text.literal(value.id() + " defendant=" + value.defendant().uuid()
                + " status=" + value.status() + " evidence=" + score(value.evidenceScore()) + " alibi=" + score(value.alibiStrength())
                + " fine=" + value.fine() + " jail=" + value.jailSeconds() + "s"), false));
        return values.size();
    }

    private static int inspect(ServerCommandSource source, String raw) {
        UUID id;
        try { id = UUID.fromString(raw); }
        catch (IllegalArgumentException ignored) { source.sendError(Text.literal("Invalid UUID: " + raw)); return 0; }
        var snapshot = SocietyApi.law().snapshot();
        long wanted = snapshot.wanted().values().stream().filter(value -> value.subject().uuid().equals(id)).count();
        long warrants = snapshot.warrants().values().stream().filter(value -> value.subject().uuid().equals(id)).count();
        long custody = snapshot.custody().values().stream().filter(value -> value.subject().uuid().equals(id)).count();
        long cases = snapshot.courtCases().values().stream().filter(value -> value.defendant().uuid().equals(id)).count();
        source.sendFeedback(() -> Text.literal("Law subject " + id + ": wanted=" + wanted + " warrants=" + warrants
                + " custodyRecords=" + custody + " courtCases=" + cases), false);
        return 1;
    }

    private static String score(double value) { return String.format(Locale.ROOT, "%.3f", value); }
}
