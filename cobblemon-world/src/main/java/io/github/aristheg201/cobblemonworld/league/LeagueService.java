package io.github.aristheg201.cobblemonworld.league;

import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.minecraft.server.level.ServerPlayer;

public final class LeagueService {
    private LeagueService() {}

    public static void awardChapter(ServerPlayer player, String chapterId) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        String rewardFlag = "league_reward:" + chapterId;
        if (!p.storyFlags.add(rewardFlag)) return;

        p.leaguePoints += 100;
        String before = p.leagueTier;
        p.leagueTier = tierFor(p.leaguePoints, p.badges.size());
        ProgressionStore.INSTANCE.save();

        if (!p.leagueTier.equals(before)) {
            CWorldNetworking.toast(player, "league", "League Promotion", p.leagueTier);
        }
    }

    public static void addPoints(ServerPlayer player, int amount) {
        if (amount == 0) return;
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        p.leaguePoints = Math.max(0, p.leaguePoints + amount);
        String before = p.leagueTier;
        p.leagueTier = tierFor(p.leaguePoints, p.badges.size());
        ProgressionStore.INSTANCE.save();
        if (!p.leagueTier.equals(before)) {
            CWorldNetworking.toast(player, "league", "League Promotion", p.leagueTier);
        }
    }

    public static String tierFor(int points, int badgeCount) {
        if (badgeCount >= 8 || points >= 800) return "CHAMPION";
        if (points >= 650) return "MASTER";
        if (points >= 500) return "PLATINUM";
        if (points >= 350) return "GOLD";
        if (points >= 200) return "SILVER";
        if (points >= 100) return "BRONZE";
        return "UNRANKED";
    }
}
