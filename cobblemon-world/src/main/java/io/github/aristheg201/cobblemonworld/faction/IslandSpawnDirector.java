package io.github.aristheg201.cobblemonworld.faction;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import io.github.aristheg201.cobblemonworld.progression.LevelCapService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class IslandSpawnDirector {
    private IslandSpawnDirector() {}

    public static void tick(ServerLevel level, IslandWarState state) {
        if (state.phase != IslandWarPhase.OCCUPATION || state.ownerFaction.isBlank()) return;

        for (ServerPlayer player : level.players()) {
            String faction = NativeFactionService.factionName(player).orElse("");
            if (!state.ownerFaction.equals(faction)) continue;
            spawnBonus(level, player, state.affinity);
        }
    }

    private static void spawnBonus(ServerLevel level, ServerPlayer player, String affinity) {
        var pool = IslandSpawnPoolRegistry.INSTANCE.get(affinity);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<String> source;
        double roll = random.nextDouble();
        if (roll < CWorldConfig.INSTANCE.factionLegendaryBonusChance && !pool.legendary().isEmpty()) {
            source = pool.legendary();
        } else if (roll < CWorldConfig.INSTANCE.factionLegendaryBonusChance
                + CWorldConfig.INSTANCE.factionRareBonusChance && !pool.rare().isEmpty()) {
            source = pool.rare();
        } else {
            source = pool.common();
        }
        if (source.isEmpty()) return;

        String species = source.get(random.nextInt(source.size()));
        int cap = LevelCapService.getCap(player);
        int min = Math.max(1, cap - 4);
        int levelValue = min + random.nextInt(Math.max(1, cap - min + 1));

        int x = player.blockPosition().getX() + random.nextInt(-20, 21);
        int z = player.blockPosition().getZ() + random.nextInt(-20, 21);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        if (y < CWorldConfig.INSTANCE.factionIslandY - 12) return;

        var entity = PokemonProperties.Companion.parse(species + " level=" + levelValue).createEntity(level, player);
        entity.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(entity);
    }
}
