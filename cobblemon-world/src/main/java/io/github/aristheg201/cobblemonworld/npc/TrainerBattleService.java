package io.github.aristheg201.cobblemonworld.npc;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.npc.NPCPlayerModelType;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import io.github.aristheg201.cobblemonworld.story.CampaignService;
import kotlin.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TrainerBattleService {
    private TrainerBattleService() {}

    public static void register() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, event -> {
            for (var loser : event.getLosers()) {
                if (!(loser instanceof NPCBattleActor npcActor)) continue;
                NPCEntity npc = npcActor.getNpc();
                if (!(npc.getInteraction() instanceof CWorldNpcInteraction interaction)) continue;

                for (var winner : event.getWinners()) {
                    if (!(winner instanceof PlayerBattleActor playerActor)) continue;
                    ServerPlayer player = playerActor.getEntity();
                    if (player == null) continue;
                    CampaignService.onTrainerDefeated(player, interaction.definitionId());
                    if ("mysterious".equals(interaction.definitionId())) npc.discard();
                }
            }
            return Unit.INSTANCE;
        });
    }

    public static NPCEntity createNpc(ServerPlayer owner, NpcDefinitionRegistry.Definition definition) {
        NPCEntity npc = new NPCEntity(owner.level());
        var npcClass = NPCClasses.INSTANCE.getByName(
                definition.npcClass() == null || definition.npcClass().isBlank()
                        ? "standard"
                        : definition.npcClass()
        );
        if (npcClass == null) npcClass = NPCClasses.INSTANCE.getByName("standard");
        if (npcClass == null) throw new IllegalStateException("Cobblemon standard NPC class is not available.");

        npc.setNpc(npcClass);
        npc.initialize(Math.max(1, definition.visualLevel()));
        npc.setCustomName(Component.literal(definition.displayName()));
        npc.setCustomNameVisible(true);
        npc.setNoAi(true);
        npc.setInvulnerable(true);
        npc.setSkill(Math.max(0, Math.min(5, definition.skill())));
        npc.setInteraction(new CWorldNpcInteraction(definition.id()));

        applyAuthoredSkin(npc, definition);

        NPCPartyStore party = new NPCPartyStore(npc);
        String[] team = definition.team();
        if (team != null) {
            int slot = 0;
            for (String spec : team) {
                if (spec == null || spec.isBlank() || slot >= 6) continue;
                party.set(slot++, PokemonProperties.Companion.parse(spec).create(owner));
            }
        }
        npc.setParty(party);
        return npc;
    }

    private static void applyAuthoredSkin(NPCEntity npc, NpcDefinitionRegistry.Definition definition) {
        String skin = definition.skin();
        if (skin == null || skin.isBlank()) return;

        String path = "assets/cobblemonworld/textures/entity/npc/" + skin + ".png";
        try {
            var resource = TrainerBattleService.class.getClassLoader().getResource(path);
            if (resource == null) {
                throw new IllegalStateException("Missing authored NPC skin " + path + " for " + definition.id());
            }
            npc.loadTexture(resource.toURI(), NPCPlayerModelType.DEFAULT);
            System.out.println("CWORLD_NPC_SKIN_APPLIED id=" + definition.id() + " skin=" + skin);
        } catch (Exception e) {
            CobblemonWorldMod.LOGGER.error("Failed to apply authored NPC skin {} to {}", path, definition.id(), e);
        }
    }

    public static boolean interact(NPCEntity npc, ServerPlayer player, String definitionId) {
        NpcDefinitionRegistry.Definition definition = NpcDefinitionRegistry.INSTANCE.get(definitionId);
        if (definition == null) {
            player.sendSystemMessage(Component.translatable("service.cobblemonworld.unavailable")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        AnchoredNpcService.facePlayer(npc, player);
        var accessGate = CampaignService.canChallengeTrainer(player, definition);
        if (!accessGate.allowed()) {
            player.sendSystemMessage(Component.translatable(accessGate.reason()).withStyle(ChatFormatting.YELLOW));
            return true;
        }
        io.github.aristheg201.cobblemonworld.story.ObjectiveBridge.interact(player, definitionId);
        boolean serviced = NpcService.dispatch(npc, player, definitionId);
        PlayerProgression progression = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        boolean hasTeam = definition.team() != null && definition.team().length > 0;

        if (!hasTeam) {
            boolean alreadyInteracted = definition.interactionFlag() != null
                    && !definition.interactionFlag().isBlank()
                    && progression.storyFlags.contains(definition.interactionFlag());
            if (alreadyInteracted) {
                if (serviced) return true;
                String repeat = definition.postBattleText();
                if (repeat != null && !repeat.isBlank()) {
                    player.sendSystemMessage(Component.literal(definition.displayName() + ": ").append(Component.translatable(repeat)));
                }
                return true;
            }

            CampaignService.ChallengeGate gate = CampaignService.canChallengeTrainer(player, definition);
            if (!gate.allowed()) {
                player.sendSystemMessage(Component.translatable(gate.reason()).withStyle(ChatFormatting.YELLOW));
                return true;
            }

            String text = definition.preBattleText();
            if (!serviced && text != null && !text.isBlank()) {
                player.sendSystemMessage(Component.literal(definition.displayName() + ": ").append(Component.translatable(text)));
            }
            if (definition.contactUnlock() != null && !definition.contactUnlock().isBlank()) {
                CampaignService.unlockContact(player, definition.contactUnlock());
            }
            if (definition.questUnlockOnInteract() != null && !definition.questUnlockOnInteract().isBlank()) {
                CampaignService.activateQuest(player, definition.questUnlockOnInteract());
            }
            if (definition.flagsOnInteract() != null) {
                for (String flag : definition.flagsOnInteract()) CampaignService.setFlag(player, flag);
            }
            if (definition.interactionFlag() != null && !definition.interactionFlag().isBlank()) {
                CampaignService.setFlag(player, definition.interactionFlag());
            }
            return true;
        }

        boolean alreadyDefeated = definition.defeatFlag() != null
                && !definition.defeatFlag().isBlank()
                && progression.storyFlags.contains(definition.defeatFlag());

        if (alreadyDefeated && !definition.rematchable()) {
            String text = definition.postBattleText();
            if (text != null && !text.isBlank()) {
                player.sendSystemMessage(Component.literal(definition.displayName() + ": ").append(Component.translatable(text)));
            }
            return true;
        }

        CampaignService.ChallengeGate gate = CampaignService.canChallengeTrainer(player, definition);
        if (!gate.allowed()) {
            player.sendSystemMessage(Component.translatable(gate.reason()).withStyle(ChatFormatting.YELLOW));
            return true;
        }

        String pre = definition.preBattleText();
        if (pre != null && !pre.isBlank()) {
            player.sendSystemMessage(Component.literal(definition.displayName() + ": ").append(Component.translatable(pre)));
        }

        try {
            // Never replace a shared NPC party while another player is fighting it.
            if (npc.isInBattle() || Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null) {
                player.sendSystemMessage(Component.translatable("service.cobblemonworld.battle_busy"));
                return true;
            }
            prepareBattleParty(npc, player, definition);
            var result = BattleBuilder.INSTANCE.pvn(player, npc);
            if (result instanceof com.cobblemon.mod.common.battles.SuccessfulBattleStart) return true;
            player.sendSystemMessage(Component.translatable("service.cobblemonworld.battle_failed").withStyle(ChatFormatting.RED));
            return false;
        } catch (Exception e) {
            CobblemonWorldMod.LOGGER.error("Failed to start trainer battle {} for {}", definitionId, player.getGameProfile().getName(), e);
            player.sendSystemMessage(Component.translatable("service.cobblemonworld.battle_failed")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
    }

    public static void prepareBattleParty(NPCEntity npc, ServerPlayer player, NpcDefinitionRegistry.Definition definition) {
        var party = new NPCPartyStore(npc);
        int slot = 0, authoredAce = 1, playerAce = 1;
        for (String spec : definition.team()) {
            if (spec == null || spec.isBlank() || slot >= 6) continue;
            var member = PokemonProperties.Companion.parse(spec).create(player);
            authoredAce = Math.max(authoredAce, member.getLevel());
            party.set(slot++, member);
        }
        for (var member : Cobblemon.INSTANCE.getStorage().getParty(player))
            playerAce = Math.max(playerAce, member.getLevel());
        var config = io.github.aristheg201.cobblemonworld.config.CWorldConfig.INSTANCE;
        if (config.scaleTrainerLevels) {
            for (var member : party) {
                member.setLevel(TrainerLevelScaling.level(member.getLevel(), authoredAce, playerAce, config.trainerLevelOffset));
                member.heal();
            }
        }
        npc.setParty(party);
    }
}
