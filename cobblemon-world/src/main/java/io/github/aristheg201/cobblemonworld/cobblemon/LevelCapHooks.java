package io.github.aristheg201.cobblemonworld.cobblemon;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import io.github.aristheg201.cobblemonworld.progression.LevelCapService;
import kotlin.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class LevelCapHooks {
    private LevelCapHooks() {}

    public static void register() {
        registerNaturalSpawnGate();
        registerCaptureGate();
        registerSendOutGate();
        registerBattleGate();
        registerExperienceGate();
        registerLevelUpSafetyNet();
        CobblemonWorldMod.LOGGER.info("Registered Cobblemon level-cap enforcement hooks.");
    }

    private static void registerNaturalSpawnGate() {
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.HIGHEST, event -> {
            if (!CWorldConfig.INSTANCE.blockOverCapNaturalSpawns) return Unit.INSTANCE;

            PokemonEntity entity = event.getEntity();
            Pokemon pokemon = entity.getPokemon();
            if (pokemon.isPlayerOwned()) return Unit.INSTANCE;

            if (event.getSpawnablePosition().getCause().getEntity() instanceof ServerPlayer player) {
                int cap = LevelCapService.getCap(player);
                if (pokemon.getLevel() > cap) {
                    event.cancel();
                }
            }
            return Unit.INSTANCE;
        });
    }

    private static void registerCaptureGate() {
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGHEST, event -> {
            if (!CWorldConfig.INSTANCE.blockOverCapCapture) return Unit.INSTANCE;
            if (!(event.getPokeBall().getOwner() instanceof ServerPlayer player)) return Unit.INSTANCE;

            Pokemon pokemon = event.getPokemon().getPokemon();
            int cap = LevelCapService.getCap(player);
            if (pokemon.getLevel() > cap) {
                event.cancel();
                tellBlocked(player, Component.translatable(
                        "message.cobblemonworld.cap.capture_blocked",
                        pokemon.getDisplayName(), pokemon.getLevel(), cap));
            }
            return Unit.INSTANCE;
        });
    }

    private static void registerSendOutGate() {
        CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.HIGHEST, event -> {
            if (!CWorldConfig.INSTANCE.blockOverCapSendOut) return Unit.INSTANCE;

            Pokemon pokemon = event.getPokemon();
            ServerPlayer player = pokemon.getOwnerPlayer();
            if (player == null) return Unit.INSTANCE;

            int cap = LevelCapService.getCap(player);
            if (pokemon.getLevel() > cap) {
                event.cancel();
                tellBlocked(player, Component.translatable(
                        "message.cobblemonworld.cap.use_blocked",
                        pokemon.getDisplayName(), pokemon.getLevel(), cap));
            }
            return Unit.INSTANCE;
        });
    }

    private static void registerBattleGate() {
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGHEST, event -> {
            if (!CWorldConfig.INSTANCE.blockOverCapBattles) return Unit.INSTANCE;

            for (var actor : event.getBattle().getActors()) {
                if (actor.getType() != ActorType.PLAYER) continue;
                if (!(actor instanceof PlayerBattleActor playerActor)) continue;

                ServerPlayer player = playerActor.getEntity();
                if (player == null) continue;

                Pokemon blocked = LevelCapService.firstOverCapPartyPokemon(player);
                if (blocked == null) continue;

                int cap = LevelCapService.getCap(player);
                Component reason = Component.translatable(
                                "message.cobblemonworld.cap.battle_blocked",
                                blocked.getDisplayName(), blocked.getLevel(), cap)
                        .withStyle(ChatFormatting.RED);
                player.sendSystemMessage(reason, true);
                event.setReason(reason);
                event.cancel();
                return Unit.INSTANCE;
            }
            return Unit.INSTANCE;
        });
    }

    private static void registerExperienceGate() {
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.HIGHEST, event -> {
            if (!CWorldConfig.INSTANCE.blockExperiencePastCap) return Unit.INSTANCE;

            Pokemon pokemon = event.getPokemon();
            ServerPlayer player = pokemon.getOwnerPlayer();
            if (player == null) return Unit.INSTANCE;

            int cap = LevelCapService.getCap(player);

            if (pokemon.getLevel() >= cap) {
                event.cancel();
                return Unit.INSTANCE;
            }

            if (cap >= CWorldConfig.INSTANCE.maxLevelCap) return Unit.INSTANCE;

            int xpUntilAboveCap = pokemon.getExperienceToLevel(cap + 1);
            if (xpUntilAboveCap > 0 && event.getExperience() >= xpUntilAboveCap) {
                event.setExperience(Math.max(0, xpUntilAboveCap - 1));
            }
            return Unit.INSTANCE;
        });
    }

    private static void registerLevelUpSafetyNet() {
        CobblemonEvents.LEVEL_UP_EVENT.subscribe(Priority.HIGHEST, event -> {
            Pokemon pokemon = event.getPokemon();
            ServerPlayer player = pokemon.getOwnerPlayer();
            if (player == null) return Unit.INSTANCE;

            int cap = LevelCapService.getCap(player);
            if (pokemon.getLevel() <= cap && event.getNewLevel() > cap) {
                event.setNewLevel(cap);
            }
            return Unit.INSTANCE;
        });
    }

    private static void tellBlocked(ServerPlayer player, Component message) {
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED), true);
    }
}
