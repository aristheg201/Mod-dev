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
                    io.github.aristheg201.cobblemonworld.narrative.NarrativeEngine.battleResult(player, interaction.definitionId(), true);
                    io.github.aristheg201.cobblemonworld.narrative.ConversationService.outcome(player, interaction.definitionId(), true);
                    if ("mysterious".equals(interaction.definitionId())) npc.discard();
                }
            }
            for (var winner : event.getWinners()) if (winner instanceof NPCBattleActor npcActor && npcActor.getNpc().getInteraction() instanceof CWorldNpcInteraction interaction) {
                for (var loser : event.getLosers()) if (loser instanceof PlayerBattleActor actor && actor.getEntity() != null) {
                    io.github.aristheg201.cobblemonworld.narrative.NarrativeEngine.battleResult(actor.getEntity(), interaction.definitionId(), false);
                    io.github.aristheg201.cobblemonworld.narrative.ConversationService.outcome(actor.getEntity(), interaction.definitionId(), false);
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
        npc.setPersistenceRequired();
        npc.setCustomName(Component.literal(definition.displayName()));
        npc.setCustomNameVisible(true);
        npc.setNoAi(true);
        npc.setInvulnerable(true);
        npc.setSkill(5);
        npc.setInteraction(new CWorldNpcInteraction(definition.id()));

        applyAuthoredSkin(npc, definition);

        NPCPartyStore party = new NPCPartyStore(npc);
        var team = io.github.aristheg201.cobblemonworld.narrative.NarrativeRegistry.INSTANCE.teams.get(definition.id());
        if (team != null) {
            int slot = 0;
            for (var data : team.members()) party.set(slot++, io.github.aristheg201.cobblemonworld.narrative.CompetitiveTeams.create(owner, data));
        }
        npc.setParty(party);
        return npc;
    }

    static void applyAuthoredSkin(NPCEntity npc, NpcDefinitionRegistry.Definition definition) {
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
        var actorOwner = io.github.aristheg201.cobblemonworld.narrative.PersonalActors.owner(npc);
        if (actorOwner != null && !actorOwner.equals(player.getUUID())) return false;
        AnchoredNpcService.facePlayer(npc, player);
        io.github.aristheg201.cobblemonworld.story.ObjectiveBridge.interact(player, definitionId);
        io.github.aristheg201.cobblemonworld.narrative.NarrativeEngine.migrate(player);
        boolean hasConversation = io.github.aristheg201.cobblemonworld.narrative.NarrativeEngine.active(player).stream().anyMatch(s -> s.target().equals(definitionId) && (s.type().equals("talk") || s.type().equals("deliver") || s.type().equals("claim")));
        if (!hasConversation && NpcService.dispatch(npc, player, definitionId)) return true;
        return io.github.aristheg201.cobblemonworld.narrative.ConversationService.openNpc(player, npc, definitionId);
    }

    /** Called only after a canonical server conversation or an authorized QA fixture. */
    public static boolean hasLiveBattle(NPCEntity npc) {
        // Cobblemon's recall-animation future can leave a closed battle ID on an anchored NPC.
        // Retain every ID still owned by the native registry; never clear an active opponent.
        var live = new java.util.HashSet<java.util.UUID>();
        for(var id:npc.getBattleIds()) if(com.cobblemon.mod.common.battles.BattleRegistry.getBattle(id)!=null) live.add(id);
        if(!live.equals(npc.getBattleIds())) {
            CobblemonWorldMod.LOGGER.info("Reconciled closed battle IDs for anchored NPC {}",npc.getUUID());
            npc.getEntityData().set(NPCEntity.Companion.getBATTLE_IDS(),java.util.Set.copyOf(live));
        }
        return !live.isEmpty();
    }

    public static boolean startBattle(NPCEntity npc, ServerPlayer player, String definitionId) {
        var definition = NpcDefinitionRegistry.INSTANCE.get(definitionId);
        if (definition == null || !io.github.aristheg201.cobblemonworld.narrative.NarrativeEngine.battleAllowed(player, definitionId)) return false;
        var gate = CampaignService.canChallengeTrainer(player, definition);
        if (!gate.allowed()) return false;
        try {
            if (hasLiveBattle(npc) || Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null) return false;
            prepareBattleParty(npc, player, definition);
            return BattleBuilder.INSTANCE.pvn(player, npc) instanceof com.cobblemon.mod.common.battles.SuccessfulBattleStart;
        } catch (Exception e) {
            CobblemonWorldMod.LOGGER.error("Failed trainer battle {} for {}", definitionId, player.getUUID(), e);
            return false;
        }
    }

    public static void prepareBattleParty(NPCEntity npc, ServerPlayer player, NpcDefinitionRegistry.Definition definition) {
        var party = new NPCPartyStore(npc);
        int slot = 0, authoredAce = 1, playerAce = 1;
        var authored = io.github.aristheg201.cobblemonworld.narrative.NarrativeRegistry.INSTANCE.teams.get(definition.id());
        if (ProgressionStore.INSTANCE.getOrCreate(player.getUUID()).storyFlags.contains("main_story_complete")) authored = io.github.aristheg201.cobblemonworld.narrative.NarrativeRegistry.INSTANCE.teams.getOrDefault(definition.id() + "_postgame", authored);
        if (authored == null) throw new IllegalStateException("Missing competitive trainer data " + definition.id());
        for (var data : authored.members()) {
            var member = io.github.aristheg201.cobblemonworld.narrative.CompetitiveTeams.create(player, data);
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
