package io.github.aristheg201.cobblemonworld.qa;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.aristheg201.cobblemonworld.boss.MysteriousFigureEntity;
import io.github.aristheg201.cobblemonworld.boss.TobaCombatService;
import io.github.aristheg201.cobblemonworld.boss.TobaEncounterService;
import io.github.aristheg201.cobblemonworld.boss.TobaEntity;
import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import io.github.aristheg201.cobblemonworld.faction.FactionStore;
import io.github.aristheg201.cobblemonworld.faction.NativeFactionService;
import io.github.aristheg201.cobblemonworld.faction.IslandWarPhase;
import io.github.aristheg201.cobblemonworld.faction.IslandWarService;
import io.github.aristheg201.cobblemonworld.faction.IslandWarStore;
import io.github.aristheg201.cobblemonworld.integration.SvFrameRpgBridge;
import io.github.aristheg201.cobblemonworld.item.ModItems;
import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.network.QaAckPayload;
import io.github.aristheg201.cobblemonworld.network.QaControlPayload;
import io.github.aristheg201.cobblemonworld.network.ToastPayload;
import io.github.aristheg201.cobblemonworld.npc.NpcDefinitionRegistry;
import io.github.aristheg201.cobblemonworld.npc.NpcPlacement;
import io.github.aristheg201.cobblemonworld.npc.NpcPlacementStore;
import io.github.aristheg201.cobblemonworld.npc.TrainerBattleService;
import io.github.aristheg201.cobblemonworld.progression.LevelCapService;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import io.github.aristheg201.cobblemonworld.story.CampaignService;
import io.github.aristheg201.cobblemonworld.story.ObjectiveBridge;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CWorldQaServerHarness {
    private static final Set<String> ACKS = ConcurrentHashMap.newKeySet();
    private static volatile String clientFailure;

    private static int stage;
    private static long ticks;
    private static long stageStarted;
    private static String awaiting;
    private static NPCEntity qaNpc;
    private static boolean islandStarted;
    private static boolean finalConfigured;
    private static boolean shutdownRequested;

    private CWorldQaServerHarness() {}

    public static void register() {
        System.out.println("CWORLD_QA_SERVER_DRIVER_LOADED");
        ServerTickEvents.END_SERVER_TICK.register(CWorldQaServerHarness::tick);
    }

    public static void onAck(ServerPlayer player, QaAckPayload payload) {
        if (!payload.ok()) {
            clientFailure = payload.token() + ": " + payload.detail();
            return;
        }
        ACKS.add(payload.token());
        System.out.println("CWORLD_QA_ACK " + payload.token() + " " + payload.detail());
    }

    private static void tick(MinecraftServer server) {
        if (shutdownRequested) return;
        ticks++;
        if (clientFailure != null) fail("Client QA failure: " + clientFailure);
        if (ticks % 5L != 0L) return;

        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        if (stageStarted == 0L) stageStarted = ticks;
        if (ticks - stageStarted > 2400L) fail("Stage timed out: " + stage);

        if (awaiting != null) {
            if (!ACKS.remove(awaiting)) return;
            System.out.println("CWORLD_QA_VISUAL_PASS " + awaiting);
            awaiting = null;
            stage++;
            stageStarted = ticks;
        }

        try {
            switch (stage) {
                case 0 -> bootstrap(player);
                case 1 -> phone(player, "trainer_card", "01-phone-trainer-card");
                case 2 -> phone(player, "objective", "02-phone-objective");
                case 3 -> phone(player, "story", "03-phone-story");
                case 4 -> phone(player, "side_quests", "04-phone-side-quests");
                case 5 -> phone(player, "level_cap", "05-phone-level-cap");
                case 6 -> phone(player, "badges", "06-phone-badges");
                case 7 -> phone(player, "contacts", "07-phone-contacts");
                case 8 -> phone(player, "messages", "08-phone-messages");
                case 9 -> phone(player, "league", "09-phone-league");
                case 10 -> phone(player, "faction", "10-phone-faction");
                case 11 -> toast(player);
                case 12 -> prepareStoryTrainer(player);
                case 13 -> battleAndStoryAudit(player);
                case 14 -> factionGate(player);
                case 15 -> factionConquest(player);
                case 16 -> factionOccupation(player);
                case 17 -> factionSpawnAndPersistence(player);
                case 18 -> npcRecovery(player);
                case 19 -> mysteriousFigure(player);
                case 20 -> phaseOneAndTransform(player);
                case 21 -> tobaRuntime(player);
                case 22 -> phone(player, "trainer_card", "18-phone-rpg-final");
                case 23 -> stopClient(player);
                case 24 -> finish(server);
                default -> {}
            }
        } catch (Throwable t) {
            t.printStackTrace();
            fail("Stage " + stage + " failed: " + t);
        }
    }

    private static void bootstrap(ServerPlayer player) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        if (!p.storyFlags.contains("phone_granted")
                || !p.storyFlags.contains("phone_obtained")
                || !hasPhone(player)
                || !p.contacts.contains("mysterious")
                || !p.unreadMessages.contains("mysterious:first_contact")) {
            return;
        }

        var rpg = SvFrameRpgBridge.snapshot(player);
        if (!rpg.available() || !rpg.libAvailable()) return;
        System.out.println("CWORLD_QA_INTEGRATIONS_PASS nativeFaction=true svframemmo=true svframelib=true profile="
                + rpg.classId() + ":" + rpg.level());

        CWorldNetworking.openPhone(player);
        control(player, "capture_phone", "home", "00-phone-home");
    }

    private static void toast(ServerPlayer player) {
        ServerPlayNetworking.send(player, new ToastPayload(
                "level_cap", "Level Cap Increased", "Lv." + LevelCapService.getCap(player)));
        control(player, "capture_toast", "", "11-toast-overlay");
    }

    private static void prepareStoryTrainer(ServerPlayer player) {
        var mara = NpcDefinitionRegistry.INSTANCE.get("mara_voss");
        require(mara != null, "Mara definition missing");
        require(!CampaignService.canChallengeTrainer(player, mara).allowed(), "Mara unlocked before prologue response");

        require(CampaignService.respondToMessage(player, "mysterious", "first_contact", 0),
                "could not answer first ??? message");
        require("chapter_01_signal".equals(ProgressionStore.INSTANCE.getOrCreate(player.getUUID()).currentStory),
                "prologue did not advance to chapter 1");
        require(CampaignService.canChallengeTrainer(player, mara).allowed(), "Mara did not unlock in chapter 1");

        ServerLevel level = player.serverLevel();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ() + 5.0;
        qaNpc = TrainerBattleService.createNpc(player, mara);
        qaNpc.moveTo(x, y, z, 180.0F, 0.0F);
        require(level.addFreshEntity(qaNpc), "Mara QA NPC failed to spawn");
        face(player, x, y + 1.3, z);

        NpcPlacementStore.INSTANCE.put(new NpcPlacement(
                "mara_voss", level.dimension().location().toString(),
                x, y, z, 180.0F, 0.0F, qaNpc.getUUID().toString()));
        control(player, "capture_world", "", "12-story-npc");
    }

    private static void battleAndStoryAudit(ServerPlayer player) {
        ObjectiveBridge.visit(player, "first_anomaly_site");

        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        var overCap = PokemonProperties.Companion.parse("pikachu level=20").create(player);
        party.set(0, overCap);
        LevelCapService.setCap(player, 15);
        require(LevelCapService.firstOverCapPartyPokemon(player) == overCap, "over-cap party detection failed");

        boolean started = TrainerBattleService.interact(qaNpc, player, "mara_voss");
        var blockedBattle = Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player);
        require(blockedBattle == null, "over-cap party bypassed Cobblemon battle gate");
        System.out.println("CWORLD_QA_LEVEL_CAP_NEGATIVE_PASS use=battle cap=15 pokemon=20 interact=" + started);

        var legal = PokemonProperties.Companion.parse("pikachu level=15").create(player);
        party.set(0, legal);
        require(TrainerBattleService.interact(qaNpc, player, "mara_voss"), "legal trainer battle did not start");
        var battle = Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player);
        require(battle != null, "legal Cobblemon battle missing from registry");
        System.out.println("CWORLD_QA_COBBLEMON_BATTLE_START_PASS id=mara_voss");
        battle.end();

        CampaignService.onTrainerDefeated(player, "mara_voss");
        require(LevelCapService.getCap(player) == 22, "chapter 1 level cap did not become 22");
        require(ProgressionStore.INSTANCE.getOrCreate(player.getUUID()).completedSideQuests.contains("first_signal"),
                "first_signal side quest did not complete");

        completeVossQuest(player);
        CampaignService.onTrainerDefeated(player, "dr_orin");
        require(LevelCapService.getCap(player) == 30, "chapter 2 cap wrong");
        completeOrinQuest(player);
        CampaignService.onTrainerDefeated(player, "rook");
        require(LevelCapService.getCap(player) == 38, "chapter 3 cap wrong");
        completeRookQuest(player);
        CampaignService.onTrainerDefeated(player, "selene_kade");
        require(LevelCapService.getCap(player) == 47, "chapter 4 cap wrong");
        completeSeleneQuest(player);
        CampaignService.onTrainerDefeated(player, "aurelia");
        require(LevelCapService.getCap(player) == 58, "chapter 5 cap wrong");
        completeAureliaQuest(player);
        CampaignService.onTrainerDefeated(player, "sixth_warden");
        require(ProgressionStore.INSTANCE.getOrCreate(player.getUUID()).storyFlags.contains("seal_06_broken"), "seal 6 missing");
        CampaignService.onTrainerDefeated(player, "seventh_warden");
        require(LevelCapService.getCap(player) == 70, "chapter 6 cap wrong");
        CampaignService.onTrainerDefeated(player, "resonance_heart");
        require(LevelCapService.getCap(player) == 85, "chapter 7 cap wrong");

        require(CampaignService.respondToMessage(player, "mysterious", "last_person", 0),
                "last_person response failed");
        require(LevelCapService.getCap(player) == 100, "main story did not unlock Lv.100 cap");
        require(CampaignService.respondToMessage(player, "mysterious", "meet_me", 0),
                "meet_me response failed");

        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        require(p.storyFlags.contains("main_story_complete") && p.storyFlags.contains("toba_meeting_revealed"),
                "final encounter flags missing");
        require(p.completedSideQuests.containsAll(Set.of(
                        "voss_echo", "orin_archive", "rook_black_card", "selene_stalemate", "aurelia_below")),
                "scripted side-quest chain incomplete");
        System.out.println("CWORLD_QA_STORY_PASS cap=100 badges=" + p.badges.size()
                + " contacts=" + p.contacts.size() + " sidequests=" + p.completedSideQuests.size());

        ProgressionStore.INSTANCE.save();
        NpcPlacementStore.INSTANCE.save();
        String beforeStory = p.currentStory;
        ProgressionStore.INSTANCE.load(player.getServer());
        NpcPlacementStore.INSTANCE.load(player.getServer());
        PlayerProgression reloaded = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        require(beforeStory.equals(reloaded.currentStory), "progression disk round-trip changed currentStory");
        require(reloaded.levelCap == 100, "progression disk round-trip changed cap");
        require(NpcPlacementStore.INSTANCE.get("mara_voss") != null, "NPC placement disk round-trip failed");
        System.out.println("CWORLD_QA_PERSISTENCE_PASS story=" + reloaded.currentStory + " cap=" + reloaded.levelCap);

        stage++;
        stageStarted = ticks;
    }

    private static void factionGate(ServerPlayer player) throws Exception {
        if (!islandStarted) {
            var result = NativeFactionService.create(player, "CWorldQA");
            require(result.success(), "Native faction creation failed: " + result.message());
            require("CWorldQA".equals(NativeFactionService.factionName(player).orElse("")), "Native faction store did not return created faction");
            require(NativeFactionService.role(player) == FactionStore.Role.OWNER, "Creator is not native faction OWNER");

            Method start = IslandWarService.class.getDeclaredMethod("startNewWar", String.class);
            start.setAccessible(true);
            start.invoke(null, "qa-runtime");
            islandStarted = true;
            stageStarted = ticks;
            System.out.println("CWORLD_QA_NATIVE_FACTION_CREATE_PASS name=CWorldQA role=OWNER");
            return;
        }

        if (!IslandWarStore.INSTANCE.state().islandBuilt) return;
        require(IslandWarStore.INSTANCE.state().phase == IslandWarPhase.GATE_WAR, "island did not enter Gate War");
        require(IslandWarService.join(player), "could not join Gate War");
        require(player.level().dimension().equals(IslandWarService.DIMENSION), "Gate War teleport dimension wrong");
        control(player, "capture_world", "", "13-faction-gate-war");
    }

    private static void factionConquest(ServerPlayer player) {
        var state = IslandWarStore.INSTANCE.state();
        state.phase = IslandWarPhase.CONQUEST;
        state.qualifiedFactions.add("CWorldQA");
        state.phaseStartedEpochMillis = System.currentTimeMillis();
        IslandWarStore.INSTANCE.save();
        require(IslandWarService.join(player), "qualified faction could not join Conquest");
        control(player, "capture_world", "", "14-faction-conquest");
    }

    private static void factionOccupation(ServerPlayer player) {
        var state = IslandWarStore.INSTANCE.state();
        state.phase = IslandWarPhase.OCCUPATION;
        state.ownerFaction = "CWorldQA";
        state.affinity = "electric";
        state.phaseStartedEpochMillis = System.currentTimeMillis();
        IslandWarStore.INSTANCE.save();
        CWorldConfig.INSTANCE.factionOccupationSpawnIntervalTicks = 20;
        CWorldConfig.INSTANCE.factionRareBonusChance = 1.0;
        CWorldConfig.INSTANCE.factionLegendaryBonusChance = 0.0;
        require(IslandWarService.join(player), "owner faction could not enter Occupation");
        control(player, "capture_world", "", "15-faction-occupation");
    }

    private static void factionSpawnAndPersistence(ServerPlayer player) {
        ServerLevel island = player.serverLevel();
        var pokemon = island.getEntitiesOfClass(PokemonEntity.class, player.getBoundingBox().inflate(40.0));
        if (pokemon.isEmpty()) return;
        require(pokemon.stream().allMatch(p -> p.getPokemon().getLevel() <= LevelCapService.getCap(player)),
                "Faction Island spawned an over-cap Pokemon");
        System.out.println("CWORLD_QA_FACTION_SPAWN_PASS count=" + pokemon.size()
                + " affinity=" + IslandWarStore.INSTANCE.state().affinity);

        stage++;
        stageStarted = ticks;
    }

    private static void npcRecovery(ServerPlayer player) {
        NpcPlacement placement = NpcPlacementStore.INSTANCE.get("mara_voss");
        require(placement != null, "Mara placement missing before recovery");

        ServerLevel placementLevel = null;
        for (ServerLevel level : player.getServer().getAllLevels()) {
            if (level.dimension().location().toString().equals(placement.dimension())) {
                placementLevel = level;
                break;
            }
        }
        require(placementLevel != null, "Mara placement dimension is not loaded: " + placement.dimension());

        double dx = player.getX() - placement.x();
        double dz = player.getZ() - placement.z();
        boolean wrongDimension = player.serverLevel() != placementLevel;
        boolean tooFar = dx * dx + dz * dz > 64.0 * 64.0;
        if (wrongDimension || tooFar) {
            teleport(player, placementLevel,
                    placement.x(), placement.y(), placement.z() - 4.0,
                    placement.yaw(), placement.pitch());
            stageStarted = ticks;
            return;
        }

        if (qaNpc != null && !qaNpc.isRemoved()) {
            qaNpc.discard();
            qaNpc = null;
            stageStarted = ticks;
            return;
        }

        NpcPlacement recovered = NpcPlacementStore.INSTANCE.get("mara_voss");
        if (recovered.entityUuid() == null || recovered.entityUuid().isBlank()) return;
        try {
            UUID uuid = UUID.fromString(recovered.entityUuid());
            if (!(placementLevel.getEntity(uuid) instanceof NPCEntity npc)) return;
            qaNpc = npc;
            face(player, npc.getX(), npc.getY() + 1.3, npc.getZ());
            System.out.println("CWORLD_QA_NPC_RECOVERY_PASS uuid=" + uuid);
            control(player, "capture_world", "", "16-npc-recovered");
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void mysteriousFigure(ServerPlayer player) {
        if (!finalConfigured) {
            ServerLevel level = player.getServer().overworld();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 12, 0) + 1;
            clearFinalEncounterStage(level, ground);
            CWorldConfig.INSTANCE.finalEncounterEnabled = true;
            CWorldConfig.INSTANCE.finalEncounterDimension = level.dimension().location().toString();
            CWorldConfig.INSTANCE.finalEncounterX = 12.5;
            CWorldConfig.INSTANCE.finalEncounterY = ground;
            CWorldConfig.INSTANCE.finalEncounterZ = 0.5;
            CWorldConfig.INSTANCE.finalEncounterYaw = 90.0F;
            CWorldConfig.INSTANCE.finalEncounterActivationRadius = 20.0;
            teleport(player, level, 6.5, ground, 0.5, -90.0F, 0.0F);
            finalConfigured = true;
            stageStarted = ticks;
            return;
        }

        MysteriousFigureEntity actor = findMysterious(player);
        if (actor == null) return;
        face(player, actor.getX(), actor.getY() + 1.4, actor.getZ());
        System.out.println("CWORLD_QA_MYSTERIOUS_SPAWN_PASS x=" + actor.getX() + " y=" + actor.getY() + " z=" + actor.getZ());
        control(player, "capture_world", "", "17-mysterious-figure");
    }

    private static void phaseOneAndTransform(ServerPlayer player) {
        MysteriousFigureEntity actor = findMysterious(player);
        require(actor != null, "??? actor missing before phase one");
        TobaEncounterService.beginPhaseOne(player, actor);
        var battle = Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player);
        require(battle != null, "??? phase-one Cobblemon battle did not register");
        System.out.println("CWORLD_QA_TOBA_PHASE1_BATTLE_PASS battle=" + battle.getBattleId());
        battle.end();

        TobaEncounterService.cobblemonPhaseWon(player);
        TobaEntity boss = TobaEncounterService.activeBoss(player);
        require(boss != null && boss.isAlive(), "TOBA phase-two entity was not spawned");
        // Back the QA camera away after transformation so the artifact proves the whole
        // boss model exists and renders, instead of showing only a cropped torso.
        teleport(player, player.serverLevel(), boss.getX() - 12.0, boss.getY(), boss.getZ(), -90.0F, 0.0F);
        face(player, boss.getX(), boss.getY() + 2.0, boss.getZ());
        System.out.println("CWORLD_QA_TOBA_TRANSFORM_PASS health=" + boss.getHealth());
        control(player, "capture_world", "", "18-toba-phase2");
    }

    private static void tobaRuntime(ServerPlayer player) {
        TobaEntity boss = TobaEncounterService.activeBoss(player);
        require(boss != null && boss.isAlive(), "TOBA missing during RPG audit");

        float before = boss.getHealth();
        boolean vanillaAccepted = boss.hurt(player.damageSources().playerAttack(player), 10.0F);
        require(!vanillaAccepted && Math.abs(boss.getHealth() - before) < 0.001F,
                "vanilla damage bypassed TOBA RPG-only gate");

        var rpg = SvFrameRpgBridge.snapshot(player);
        require(rpg.available() && rpg.libAvailable(), "SVFrame runtime disappeared during TOBA phase");
        boolean rpgAccepted = boss.applyRpgDamage(player, 8.0F, SvFrameRpgBridge.DamageFlavor.PHYSICAL_SKILL);
        require(rpgAccepted, "SVFrameLib PlayerMetadata.attack bridge did not handle TOBA RPG damage");
        TobaCombatService.useSkill(player, "DASH");
        System.out.println("CWORLD_QA_TOBA_RPG_PASS vanillaBlocked=true svframeDamage=true healthBefore="
                + before + " healthAfter=" + boss.getHealth() + " profile=" + rpg.classId() + ":" + rpg.level());

        stage++;
        stageStarted = ticks;
    }

    private static void stopClient(ServerPlayer player) {
        control(player, "stop", "complete", "qa-client-stop");
    }

    private static void finish(MinecraftServer server) {
        System.out.println("CWORLD_QA_COMPLETE screenshots=20 runtime=dedicated_server");
        shutdownRequested = true;
        server.halt(false);
    }

    private static void completeVossQuest(ServerPlayer player) {
        require(CampaignService.respondToMessage(player, "mara_voss", "after_defeat", 0), "Mara side quest did not unlock");
        ObjectiveBridge.interact(player, "league_relay_archive");
        ObjectiveBridge.cobblemonBattle(player, "relay_cleaner");
        require(CampaignService.respondToMessage(player, "mara_voss", "after_echo", 0), "Mara follow-up missing");
    }

    private static void completeOrinQuest(ServerPlayer player) {
        require(CampaignService.respondToMessage(player, "dr_orin", "after_defeat", 0), "Orin side quest did not unlock");
        ObjectiveBridge.visit(player, "buried_resonance_archive");
        ObjectiveBridge.interact(player, "seal_glyph_tablet");
        require(CampaignService.respondToMessage(player, "dr_orin", "after_archive", 0), "Orin follow-up missing");
    }

    private static void completeRookQuest(ServerPlayer player) {
        require(CampaignService.respondToMessage(player, "rook", "after_defeat", 0), "Rook side quest did not unlock");
        ObjectiveBridge.cardWorldVisit(player, "black_card_memory");
        ObjectiveBridge.cardWorldWin(player, "archivist_duelist");
        require(CampaignService.respondToMessage(player, "rook", "after_card", 0), "Rook follow-up missing");
    }

    private static void completeSeleneQuest(ServerPlayer player) {
        require(CampaignService.respondToMessage(player, "selene_kade", "after_defeat", 0), "Selene side quest did not unlock");
        ObjectiveBridge.visit(player, "seal_chess_chamber");
        ObjectiveBridge.chessWin(player, "seal_board_variant");
        require(CampaignService.respondToMessage(player, "selene_kade", "after_stalemate", 0), "Selene follow-up missing");
    }

    private static void completeAureliaQuest(ServerPlayer player) {
        require(CampaignService.respondToMessage(player, "aurelia", "after_defeat", 0), "Aurelia side quest did not unlock");
        ObjectiveBridge.cobblemonBattle(player, "league_vault_keeper");
        ObjectiveBridge.interact(player, "offline_seventh_record");
        require(CampaignService.respondToMessage(player, "aurelia", "after_below", 0), "Aurelia follow-up missing");
    }

    private static void phone(ServerPlayer player, String app, String file) {
        CWorldNetworking.openPhone(player);
        control(player, "capture_phone", app, file);
    }

    private static void control(ServerPlayer player, String action, String primary, String token) {
        awaiting = token;
        ServerPlayNetworking.send(player, new QaControlPayload(action, primary, token));
    }

    private static boolean hasPhone(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.TRAINER_PHONE)) return true;
        }
        return false;
    }

    private static MysteriousFigureEntity findMysterious(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(
                        MysteriousFigureEntity.class, player.getBoundingBox().inflate(32.0))
                .stream()
                .filter(actor -> actor.getOwnerUuid() == null || actor.getOwnerUuid().equals(player.getUUID()))
                .findFirst()
                .orElse(null);
    }

    private static void clearFinalEncounterStage(ServerLevel level, int ground) {
        // Runtime visual QA must prove the actors themselves are visible, not photograph a hillside.
        // This only affects the disposable QA world.
        for (int x = -2; x <= 18; x++) {
            for (int z = -5; z <= 5; z++) {
                level.setBlockAndUpdate(new BlockPos(x, ground - 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = ground; y <= ground + 6; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void face(ServerPlayer player, double x, double y, double z) {
        double dx = x - player.getX();
        double dz = z - player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(y - player.getEyeY(), horizontal)));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
    }

    private static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(level, x, y, z, EnumSet.noneOf(RelativeMovement.class), yaw, pitch);
    }

    private static void require(boolean condition, String message) {
        if (!condition) fail(message);
    }

    private static void fail(String message) {
        System.err.println("CWORLD_QA_FAILED " + message);
        throw new IllegalStateException(message);
    }
}
