package io.github.aristheg201.cobblemonworld.qa;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import io.github.aristheg201.cobblemonworld.network.*;
import io.github.aristheg201.cobblemonworld.npc.*;
import io.github.aristheg201.cobblemonworld.progression.*;
import io.github.aristheg201.cobblemonworld.shop.*;
import io.github.aristheg201.cobblemonworld.story.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Actual client/server test driver for the production-remapped mod, with a non-OP survival player. */
public final class ProductionQaServer {
    private record Step(String name, String action, Supplier<String> primary, Consumer<ServerPlayer> setup, Predicate<ServerPlayer> verify) {}
    private static final List<Step> STEPS = new ArrayList<>();
    private static final Set<String> ACKS = new HashSet<>();
    private static int stage, age;
    private static long clock;
    private static boolean started, sent, stopped, prepared;
    private static NPCEntity active;
    private static String failure;
    private static final Map<String, Integer> LOCATIONS = new LinkedHashMap<>();
    private static final Set<String> SCALED = new HashSet<>();
    private static String currency;
    private static MinecraftServer qaServer;
    private ProductionQaServer() {}
    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server ->
                server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true));
        ServerTickEvents.END_SERVER_TICK.register(ProductionQaServer::tick);
    }
    public static void ack(QaAckPayload payload) {
        if (!payload.ok()) failure = payload.token() + ": " + payload.detail();
        else ACKS.add(payload.token());
    }
    private static void tick(MinecraftServer server) {
        qaServer = server;
        if (stopped) {
            if (server.getPlayerList().getPlayers().isEmpty()) server.halt(false);
            return;
        }
        clock++;
        if (failure != null) throw new IllegalStateException("CWORLD_PROD_QA_FAILED " + failure);
        if (server.getPlayerList().getPlayers().isEmpty() || clock < 120) return;
        var player = server.getPlayerList().getPlayers().getFirst();
        if (!started) {
            started = true;
            player.serverLevel().setDayTime(6000);
            player.serverLevel().setWeatherParameters(0, 0, false, false);
            server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false, server);
            server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
            server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, server);
            for (var level : server.getAllLevels()) {
                var monsters = new java.util.ArrayList<net.minecraft.world.entity.Entity>();
                for (var entity : level.getAllEntities())
                    if (entity instanceof net.minecraft.world.entity.monster.Monster) monsters.add(entity);
                monsters.forEach(net.minecraft.world.entity.Entity::discard);
            }
            for (var placement : NpcPlacementStore.INSTANCE.all().values())
                LOCATIONS.put(placement.id(), (int) Math.round((placement.x() - 4) / 18));
            require(!player.hasPermissions(2), "QA player must be non-OP, with cheats disabled");
            System.out.println("CWORLD_PROD_QA_NON_OP_CONFIRMED player=" + player.getGameProfile().getName());
            if (Boolean.getBoolean("cworld.qa.noeconomy")) missingEconomySteps(player);
            else if (Boolean.getBoolean("cworld.qa.restart")) restartSteps(player);
            else if (Boolean.getBoolean("cworld.qa.resume")) campaignSteps(player);
            else prepare(player);
        }
        if (stage >= STEPS.size()) {
            ProgressionStore.INSTANCE.save(); NpcPlacementStore.INSTANCE.save();
            System.out.println("CWORLD_PROD_QA_FINISHED stages=" + STEPS.size() + " restart=" + Boolean.getBoolean("cworld.qa.restart"));
            ServerPlayNetworking.send(player, new QaControlPayload("stop", "", "prod-stop")); stopped = true; return;
        }
        Step step = STEPS.get(stage);
        if (!prepared) {
            step.setup().accept(player); age = 0; prepared = true;
            System.out.println("CWORLD_PROD_QA_BEGIN " + step.name());
        }
        age++;
        if (age > 12000) throw new IllegalStateException("CWORLD_PROD_QA_FAILED timed out " + step.name());
        if (!sent) {
            String primary = step.primary().get();
            if (step.action().equals("prod_interact") && primary.isBlank()) return;
            sent = true;
            ServerPlayNetworking.send(player, new QaControlPayload(step.action(), primary, step.name()));
        }
        if (step.name().startsWith("progression-") && active != null && active.isInBattle() && SCALED.add(step.name())) {
            int highestPlayer = 1, highestTrainer = 1;
            for (var member : Cobblemon.INSTANCE.getStorage().getParty(player)) highestPlayer = Math.max(highestPlayer, member.getLevel());
            for (var member : active.getParty()) highestTrainer = Math.max(highestTrainer, member.getLevel());
            require(highestPlayer == highestTrainer, "Trainer's actual battle party did not scale to player: " + step.name());
            System.out.println("CWORLD_PROD_QA_TRAINER_SCALE " + step.name() + " player=" + highestPlayer + " trainer=" + highestTrainer);
        }
        if (ACKS.contains(step.name()) && step.verify().test(player)) {
            System.out.println("CWORLD_PROD_QA_PASS " + step.name());
            ACKS.remove(step.name()); stage++; sent = false; prepared = false;
        } else if (age > 12000) throw new IllegalStateException("CWORLD_PROD_QA_FAILED timed out " + step.name());
    }
    private static void add(String name, String action, String primary, Consumer<ServerPlayer> setup, Predicate<ServerPlayer> verify) {
        STEPS.add(new Step(name, action, () -> primary, setup, verify));
    }
    private static void capture(String name, String action, String primary) { add(name, action, primary, p -> {}, p -> true); }
    private static org.krripe.beconomy.api.EconomyAPI api() { return org.krripe.beconomy.api.BEconomy.INSTANCE.getAPI(); }
    private static void balance(ServerPlayer p, int amount) { api().setBalance(p.getUUID(), BigDecimal.valueOf(amount), currency); }
    private static boolean money(ServerPlayer p, int expected) { return api().getBalance(p.getUUID(), currency).compareTo(BigDecimal.valueOf(expected)) == 0; }
    private static PlayerProgression progression(ServerPlayer p) { return ProgressionStore.INSTANCE.getOrCreate(p.getUUID()); }
    private static int count(ServerPlayer p, String id) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); int total = 0;
        for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).is(item)) total += p.getInventory().getItem(i).getCount();
        return total;
    }
    private static void clearInventory(ServerPlayer p) {
        p.getInventory().clearContent(); p.getInventory().setItem(0, new ItemStack(io.github.aristheg201.cobblemonworld.item.ModItems.TRAINER_PHONE));
        p.getInventory().selected = 0; p.inventoryMenu.broadcastChanges();
    }
    private static void move(ServerPlayer p, double x, double z, float yaw) {
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            var ground = BlockPos.containing(x + dx, 119, z + dz);
            p.serverLevel().setBlockAndUpdate(ground, Blocks.STONE_BRICKS.defaultBlockState());
            for (int y = 120; y <= 124; y++) p.serverLevel().setBlockAndUpdate(BlockPos.containing(x + dx, y, z + dz), Blocks.AIR.defaultBlockState());
        }
        p.teleportTo(p.serverLevel(), x, 120, z, EnumSet.noneOf(RelativeMovement.class), yaw, 0);
    }
    private static void place(ServerPlayer p, String id) {
        int location = LOCATIONS.computeIfAbsent(id, k -> LOCATIONS.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1);
        double x = location * 18 + 4;
        move(p, x, 0, 135);
        p.setXRot(60); // Exercise the former floor-facing placement failure with an authored downward view.
        p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "cworld npc place " + id);
        var placement = NpcPlacementStore.INSTANCE.get(id);
        require(placement != null && placement.pitch() == 0, "Placement must normalize pitch: " + id);
        active = (NPCEntity) p.serverLevel().getEntity(UUID.fromString(placement.entityUuid()));
        move(p, x - 3, 3, -135);
    }
    private static void interact(String name, String id, Predicate<ServerPlayer> verify) {
        STEPS.add(new Step(name, name.equals("professor-gameplay") ? "prod_narrative" : "prod_interact", () -> {
            var p = NpcPlacementStore.INSTANCE.get(id);
            // Newly placed entities can join the chunk's visible entity lookup on the next tick.
            if (active == null && qaServer != null) {
                for (var level : qaServer.getAllLevels()) {
                    var found = level.getEntity(UUID.fromString(p.entityUuid()));
                    if (found instanceof NPCEntity npc) { active = npc; break; }
                }
            }
            return active == null ? "" : name.equals("professor-gameplay") ? new com.google.gson.Gson().toJson(new NarrativeQaServer.Control("hale_phone","talk",active.getId(),0,0,0,"")) : Integer.toString(active.getId());
        }, p -> place(p, id), verify));
    }
    private static void prepare(ServerPlayer player) {
        var p = progression(player);
        // Fresh QA world only; a resumed QA run uses the restart driver and never clears saves.
        require(!p.storyFlags.contains("production_qa_completed"), "Use a fresh QA world for a full scenario run");
        player.serverLevel().setDayTime(6000);
        clearInventory(player);
        require(org.krripe.beconomy.api.BEconomy.INSTANCE.isInitialized(), "Supplied BEconomy API not initialized");
        if (api().getCurrencyList().stream().noneMatch(c -> c.getCurrencyType().equalsIgnoreCase("BeastCoin"))) {
            api().createCurrency("BeastCoin", "QA ordinary progression currency", "minecraft:gold_nugget", 0, BigDecimal.ZERO, "BC", false);
        }
        currency = api().getCurrencyList().stream().map(c -> c.getCurrencyType()).filter(c -> c.equalsIgnoreCase("BeastCoin")).findFirst().orElseThrow();
        api().createCurrency("HunterCoin", "QA currency isolation", "minecraft:diamond", 0, BigDecimal.ZERO, "HC", true);
        api().setBalance(player.getUUID(), BigDecimal.valueOf(9000), "huntercoin");
        balance(player, 0);
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        party.clearParty();
        var pokemon = PokemonProperties.Companion.parse("pikachu level=5 moves=thundershock,quickattack").create(player);
        pokemon.setCurrentHealth(1); party.set(0, pokemon);

        interact("professor-gameplay", "professor_hale", q -> progression(q).storyFlags.contains("professor_met"));
        if (Boolean.getBoolean("cworld.qa.services")) {
            interact("gate-tower-before-eight-towns", "battle_tower_receptionist", q -> !progression(q).storyFlags.contains("battle_tower_registered"));
            capture("gate-tower-before-eight-towns.png", "prod_dialogue", "blocked");
            interact("gate-league-before-tower", "royal_league_receptionist", q -> !progression(q).storyFlags.contains("royal_league_registered"));
            capture("gate-league-before-tower.png", "prod_dialogue", "blocked");
            interact("gate-school-before-league", "school_wolf_gatekeeper", q -> !progression(q).storyFlags.contains("school_wolf_entry_granted"));
            capture("gate-school-before-league.png", "prod_dialogue", "blocked");
            interact("gate-vargan-before-trials", "school_wolf_master", q -> !progression(q).storyFlags.contains("school_wolf_master_defeated") && Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(q) == null);
            capture("gate-vargan-before-trials.png", "prod_dialogue", "blocked");
            add("gate-final-before-archive", "prod_close", "", q -> {}, q -> !io.github.aristheg201.cobblemonworld.boss.TobaEncounterService.eligible(q) && !progression(q).storyFlags.contains("toba_identity_revealed"));
        }
        add("legacy-side-quest-fixture","prod_close","",q -> CampaignService.activateQuest(q,"first_signal"),q -> progression(q).activeSideQuests.contains("first_signal"));
        capture("01-phone-home-en.png", "prod_phone", "home");
        add("native-faction-create", "prod_faction", "faction_create", q -> {}, q -> io.github.aristheg201.cobblemonworld.faction.NativeFactionService.faction(q).isPresent());
        capture("01-phone-native-faction.png", "prod_phone", "faction");
        add("native-faction-owner-cannot-leave", "prod_faction", "faction_leave", q -> {}, q -> io.github.aristheg201.cobblemonworld.faction.NativeFactionService.faction(q).isPresent());
        add("native-faction-disband", "prod_faction", "faction_disband", q -> {}, q -> io.github.aristheg201.cobblemonworld.faction.NativeFactionService.faction(q).isEmpty());
        for (String app : List.of("trainer_card", "objective", "story", "level_cap", "badges", "contacts", "league", "faction"))
            capture("01-phone-app-" + app + ".png", "prod_phone", app);
        capture("02-branching-messages-before.png", "prod_phone", "messages");
        add("03-branching-messages-reply.png", "prod_reply", "2", q -> {}, q -> progression(q).dialogueHistory.stream().anyMatch(t -> t.player() && t.choice() == 2 && t.playerName().equals(q.getGameProfile().getName())));
        capture("04-phone-quest-pin.png", "prod_phone", "side_quests");
        add("05-pin-objective.png", "prod_pin", "", q -> {}, q -> progression(q).pinnedObjective != null && progression(q).pinnedObjective.questId().equals("first_signal"));
        capture("06-compass-unplaced.png", "prod_world", "");

        interact("mira-party-heal", "daycare_mira", q -> pokemon.getCurrentHealth() == pokemon.getMaxHealth());
        capture("07-service-npc-upright.png", "prod_world", "");
        add("npc-anchor-restores-displacement", "prod_close", "", q -> active.setPos(active.getX() + 3, active.getY() + 1, active.getZ()), q -> {
            var saved = NpcPlacementStore.INSTANCE.get("daycare_mira");
            return active.distanceToSqr(saved.x(), saved.y(), saved.z()) < .001 && active.getXRot() == 0;
        });
        add("npc-rotate-authored-yaw", "prod_close", "", q -> {
            q.setYRot(72); q.setXRot(50);
            q.getServer().getCommands().performPrefixedCommand(q.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "cworld npc rotate daycare_mira");
        }, q -> NpcPlacementStore.INSTANCE.get("daycare_mira").pitch() == 0 && Math.abs(NpcPlacementStore.INSTANCE.get("daycare_mira").yaw() - 72) < 0.1);
        add("npc-recovery-store-reload", "prod_close", "", q -> {
            active.discard(); NpcPlacementStore.INSTANCE.save(); NpcPlacementStore.INSTANCE.load(q.getServer());
        }, q -> {
            var saved = NpcPlacementStore.INSTANCE.get("daycare_mira");
            var npc = q.serverLevel().getEntity(UUID.fromString(saved.entityUuid()));
            return npc != null && npc.getXRot() == 0 && npc.distanceToSqr(saved.x(), saved.y(), saved.z()) < 0.01;
        });
        interact("ren-opens-dedicated-shop", "pokemall_ren", q -> true);
        capture("08-ren-pokemall-zero-balance.png", "prod_shop", "");
        add("09-purchase-zero.png", "prod_buy", "request", q -> {}, q -> money(q, 0) && count(q, "cobblemon:poke_ball") == 0);
        add("10-purchase-insufficient.png", "prod_buy", "request", q -> balance(q, 19), q -> money(q, 19) && count(q, "cobblemon:poke_ball") == 0);
        add("11-purchase-exact.png", "prod_buy", "request", q -> balance(q, 20), q -> money(q, 0) && count(q, "cobblemon:poke_ball") == 16);
        add("12-purchase-rapid-double.png", "prod_buy", "double", q -> balance(q, 100), q -> money(q, 80) && count(q, "cobblemon:poke_ball") == 32);
        add("13-purchase-invalid-quantity.png", "prod_buy", "invalid", q -> {}, q -> money(q, 80) && count(q, "cobblemon:poke_ball") == 32);
        add("14-purchase-unknown-entry.png", "prod_buy", "unknown", q -> {}, q -> money(q, 80) && count(q, "cobblemon:poke_ball") == 32);
        add("15-purchase-inventory-full.png", "prod_buy", "request", q -> {
            for (int i = 1; i < 36; i++) q.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            q.inventoryMenu.broadcastChanges();
        }, q -> money(q, 80) && count(q, "cobblemon:poke_ball") == 0);
        add("16-purchase-failed-grant-refund.png", "prod_buy", "request", q -> {
            clearInventory(q); ShopService.qaFailNextGrant();
        }, q -> money(q, 80) && count(q, "cobblemon:poke_ball") == 0);
        add("17-purchase-success-retains-shop.png", "prod_buy", "request", q -> {}, q -> money(q, 60) && count(q, "cobblemon:poke_ball") == 16 && api().getBalance(q.getUUID(), "huntercoin").compareTo(BigDecimal.valueOf(9000)) == 0);
        interact("elle-opens-full-registry-shop", "fashion_elle", q -> auditCatalog());
        add("explicit-shop-registry-items-resolve", "prod_close", "", q -> {}, q -> {
            for (var e : ShopRegistry.INSTANCE.entries("pokemall_ren")) require(e.available() && ShopRegistry.registered(e.item()), "Missing supply item " + e.item());
            return true;
        });
        interact("elle-reopen-after-catalog-check", "fashion_elle", q -> true);
        capture("18-elle-fashion-overview.png", "prod_shop", "");
        for (String category : List.of("armor", "weapons", "accessories", "materials", "shiny")) capture("19-elle-category-" + category + ".png", "prod_shop", category);
        capture("19-elle-zacian-selected.png", "prod_shop", "select:cobblemonarmory:zacian_sword");
        capture("19-elle-armors-selected.png", "prod_shop", "select_namespace:cobblemonarmors");
        capture("20-elle-all.png", "prod_shop", "all");
        capture("21-elle-scrolled.png", "prod_shop", "scroll:9");
        capture("22-elle-scale3.png", "prod_shop", "scale:3");
        capture("23-elle-scale2.png", "prod_shop", "scale:2");
        if (ShopRegistry.registered("mapkit:bicycle"))
            add("prepare-bicycle-wallet", "prod_close", "", q -> balance(q, 500), q -> true);
        interact("tomo-opens-bicycle-shop", "bicycle_tomo", q -> true);
        capture("24-tomo-bicycle-shop.png", "prod_shop", "");
        if (ShopRegistry.registered("mapkit:bicycle")) {
            add("24-tomo-purchase-bicycle.png", "prod_buy", "click", q -> {},
                    q -> money(q, 0) && count(q, "mapkit:bicycle") == 1);
            add("bicycle-persistence-fixture", "prod_close", "", q -> balance(q, 60), q -> true);
        }
        add("bicycle-dependency-check", "prod_close", "", q -> {}, q -> {
            boolean available = ShopRegistry.registered("mapkit:bicycle");
            require(ShopRegistry.INSTANCE.entries("bicycle_tomo").getFirst().available() == available, "Bicycle availability must match actual registry");
            System.out.println("CWORLD_PROD_QA_BICYCLE_REGISTERED " + available); return true;
        });
        add("navigation-placed-target", "prod_close", "", q -> { place(q, "mara_voss"); ObjectiveService.reset(q); ObjectiveService.pin(q,"first_signal"); }, q -> ObjectiveService.navigation(q).status().equals("ready"));
        capture("24a-pin-placed-target-phone.png", "prod_phone", "side_quests");
        add("24b-pin-placed-target.png", "prod_pin", "", q -> {}, q -> progression(q).pinnedObjective != null);
        capture("25-compass-immediate.png", "prod_world", "");
        add("26-compass-rotated.png", "prod_world", "", q -> move(q, active.getX() - 6, 3, 45), q -> true);
        add("27-compass-moved.png", "prod_world", "", q -> move(q, active.getX() - 4, 3, 45), q -> true);
        add("28-compass-replaced-target.png", "prod_world", "", q -> {
            move(q, active.getX() + 4, 0, 120);
            q.getServer().getCommands().performPrefixedCommand(q.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "cworld npc place mara_voss");
            ObjectiveService.sync(q, true);
        }, q -> ObjectiveService.navigation(q).x() == NpcPlacementStore.INSTANCE.get("mara_voss").x());
        add("navigation-target-other-dimension", "prod_close", "", q -> {
            var overworld = q.serverLevel();
            double x = q.getX(), z = q.getZ();
            q.teleportTo(q.getServer().getLevel(net.minecraft.world.level.Level.NETHER), x, 120, z, EnumSet.noneOf(RelativeMovement.class), 0, 0);
            place(q, "mara_voss");
            q.teleportTo(overworld, x, 120, z, EnumSet.noneOf(RelativeMovement.class), 0, 0);
            ObjectiveService.sync(q, true);
        }, q -> ObjectiveService.navigation(q).status().equals("dimension"));
        capture("28a-compass-different-dimension.png", "prod_world", "");
        add("navigation-target-returned", "prod_close", "", q -> { place(q, "mara_voss"); ObjectiveService.sync(q, true); }, q -> ObjectiveService.navigation(q).status().equals("ready"));
        add("objective-reset-long-command", "prod_reset", "cworld story objective reset", q -> {}, q -> progression(q).pinnedObjective == null);
        add("objective-reset-alias", "prod_reset", "cworldresetobjective", q -> ObjectiveService.pin(q, "first_signal"), q -> progression(q).pinnedObjective == null);
        capture("29-compass-reset-main.png", "prod_world", "");
        interact("ren-resource-pack-proof", "pokemall_ren", q -> true);
        capture("29a-ren-original-itemstack.png", "prod_shop", "select:poke_ball");
        add("resource-pack-enable", "prod_pack", "on", q -> {}, q -> true);
        capture("29b-ren-resource-pack-itemstack.png", "prod_shop", "select:poke_ball");
        add("resource-pack-disable", "prod_pack", "off", q -> {}, q -> true);
        add("locale-vi", "prod_locale", "vi_vn", q -> {}, q -> true);
        capture("30-phone-home-vi.png", "prod_phone", "home");
        capture("30a-phone-faction-vi.png", "prod_phone", "faction");
        capture("31-phone-messages-vi.png", "prod_phone", "messages");
        interact("ren-vi", "pokemall_ren", q -> true);
        capture("32-ren-vi.png", "prod_shop", "");
        add("locale-en", "prod_locale", "en_us", q -> {}, q -> true);
        if (!Boolean.getBoolean("cworld.qa.services")) scalingSteps();
        if (!Boolean.getBoolean("cworld.qa.services")) campaignSteps(player);
        else add("production-services-save-checkpoint", "prod_close", "", q -> {
            progression(q).storyFlags.add("production_qa_completed");
            ProgressionStore.INSTANCE.save(); NpcPlacementStore.INSTANCE.save();
        }, q -> true);
    }
    private static void scalingSteps() {
        add("prepare-over-cap-battle-test", "prod_close", "", q -> {
            LevelCapService.setCap(q, 15);
            var team = Cobblemon.INSTANCE.getStorage().getParty(q);
            team.clearParty();
            team.set(0, PokemonProperties.Companion.parse("mewtwo level=16 moves=psychic").create(q));
        }, q -> true);
        interact("over-cap-trainer-battle-rejected", "mara_voss", q ->
                Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(q) == null
                        && Cobblemon.INSTANCE.getStorage().getParty(q).get(0).getLevel() == 16
                        && !progression(q).storyFlags.contains("mara_voss_defeated"));
        for (String id : List.of("mara_voss", "dr_orin")) {
            int level = id.equals("mara_voss") ? 12 : 23;
            add("scaling-party-" + level, "prod_close", "", q -> {
                LevelCapService.setCap(q, level);
                var team = Cobblemon.INSTANCE.getStorage().getParty(q);
                for (int i = 0; i < 6; i++) team.set(i, PokemonProperties.Companion.parse("mewtwo level=" + level + " moves=aurasphere,icebeam,psychic,flamethrower").create(q));
            }, q -> true);
            interact("progression-" + id, id, q -> progression(q).storyFlags.contains(NpcDefinitionRegistry.INSTANCE.get(id).defeatFlag()));
        }
    }
    private static void missingEconomySteps(ServerPlayer player) {
        require(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("beconomy"), "Missing economy scenario accidentally loaded BEconomy");
        clearInventory(player);
        interact("missing-economy-open-ren", "pokemall_ren", q -> true);
        capture("39-ren-economy-unavailable.png", "prod_shop", "noeconomy");
        add("missing-economy-reject-purchase", "prod_buy", "request", q -> {}, q -> count(q, "cobblemon:poke_ball") == 0);
    }
    private static void campaignSteps(ServerPlayer player) {
        // Seed a powerful QA party to exercise battle transport/victory hooks, not campaign balance.
        add("prepare-battle-fixture", "prod_close", "", q -> {
            LevelCapService.setCap(q, 100);
            var team = Cobblemon.INSTANCE.getStorage().getParty(q);
            require(ShopRegistry.registered("cobblemon:life_orb"), "QA held item absent");
            for (int i = 0; i < 6; i++) {
                var member = PokemonProperties.Companion.parse("mewtwo level=100 moves=aurasphere,icebeam,thunderbolt,flamethrower").create(q);
                for (var stat : List.of(com.cobblemon.mod.common.api.pokemon.stats.Stats.HP,
                        com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK,
                        com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE,
                        com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK,
                        com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE,
                        com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED)) member.setIV(stat, 31);
                member.setEV(com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK, 252);
                member.setEV(com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED, 252);
                member.setEV(com.cobblemon.mod.common.api.pokemon.stats.Stats.HP, 4);
                member.swapHeldItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("cobblemon:life_orb"))), false, false);
                member.heal(); team.set(i, member);
            }
        }, q -> true);
        if (!progression(player).storyFlags.contains("town8_qualifier_defeated")) interact("33-battle-tower-locked-before-town8", "battle_tower_receptionist", q -> !progression(q).storyFlags.contains("battle_tower_registered"));
        String[] circuit = {"mara_voss", "dr_orin", "rook", "selene_kade", "sixth_warden", "rocket_grunt_01", "rocket_grunt_02", "rocket_grunt_03", "rocket_admin_vex", "lab_scientist_iris", "archaeologist_marlow", "seventh_warden", "harbour_marshal_liora", "captain_dorian", "battle_tower_receptionist", "battle_tower_trainer_01", "battle_tower_trainer_02", "battle_tower_trainer_03", "royal_league_receptionist", "league_elite_01", "league_elite_02", "league_elite_03", "aurelia", "school_wolf_gatekeeper", "school_wolf_trainer_01", "school_wolf_trainer_02", "school_wolf_trainer_03", "school_wolf_master"};
        for (String id : circuit) {
            var definition = NpcDefinitionRegistry.INSTANCE.get(id);
            boolean battle = definition.team() != null && definition.team().length > 0;
            String flag = battle ? definition.defeatFlag() : definition.interactionFlag();
            if (progression(player).storyFlags.contains(flag)) continue;
            if (battle) add("party-heal-before-" + id, "prod_close", "", q -> {
                for (var member : Cobblemon.INSTANCE.getStorage().getParty(q)) member.heal();
            }, q -> true);
            interact("progression-" + id, id, q -> progression(q).storyFlags.contains(flag));
            if (id.equals("seventh_warden")) interact("tower-locked-after-seven-towns", "battle_tower_receptionist", q -> !progression(q).storyFlags.contains("battle_tower_registered"));
            if (id.equals("captain_dorian")) capture("34-town8-qualified.png", "prod_world", "");
            if (id.equals("battle_tower_receptionist")) capture("35-battle-tower-open.png", "prod_world", "");
        }
        add("final-mystery-story-spawn", "prod_close", "", q -> {
            var cfg = CWorldConfig.INSTANCE; cfg.finalEncounterEnabled = true;
            cfg.finalEncounterDimension = q.level().dimension().location().toString();
            cfg.finalEncounterX = q.getX() + 2; cfg.finalEncounterY = q.getY(); cfg.finalEncounterZ = q.getZ(); CWorldConfig.save();
            ObjectiveService.sync(q, true);
        }, q -> q.serverLevel().getEntitiesOfClass(io.github.aristheg201.cobblemonworld.boss.MysteriousFigureEntity.class, q.getBoundingBox().inflate(10)).size() == 1);
        capture("36-mysterious-story-spawn.png", "prod_world", "");
        STEPS.add(new Step("final-encounter-real-battle", "prod_interact", () -> {
            var actors = player.serverLevel().getEntitiesOfClass(io.github.aristheg201.cobblemonworld.boss.MysteriousFigureEntity.class, player.getBoundingBox().inflate(10));
            return actors.isEmpty() ? "" : Integer.toString(actors.getFirst().getId());
        }, q -> {
            for (var member : Cobblemon.INSTANCE.getStorage().getParty(q)) member.heal();
        }, q -> progression(q).storyFlags.contains("toba_identity_revealed") && progression(q).storyFlags.contains("main_story_complete")));
        capture("36-final-identity-revealed.png", "prod_phone", "story");
        add("production-save-checkpoint", "prod_close", "", q -> {
            progression(q).storyFlags.add("production_qa_completed");
            ProgressionStore.INSTANCE.save(); NpcPlacementStore.INSTANCE.save();
        }, q -> true);
    }
    private static boolean auditCatalog() {
        var rows = ShopRegistry.INSTANCE.entries("fashion_elle");
        Set<String> actual = new TreeSet<>();
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (Set.of("cobblemonarmory", "cobblemonarmors").contains(id.getNamespace()) && !id.toString().equals("cobblemonarmory:cobblemon_armory")) actual.add(id.toString());
        }
        Set<String> reachable = new TreeSet<>(); rows.forEach(e -> { reachable.add(e.item()); require(e.price() >= 1 && e.price() <= 500, "Price ceiling"); require(!e.categories().isEmpty(), "Missing category"); });
        require(actual.equals(reachable), "Fashion registry/catalog mismatch expected=" + actual.size() + " got=" + reachable.size());
        require(actual.size() > 50, "Fashion unexpectedly collapsed to a small catalog");
        try { var out = Path.of("qa-runtime/catalog-registry-audit.txt"); Files.createDirectories(out.getParent()); Files.write(out, actual); }
        catch (Exception e) { throw new IllegalStateException(e); }
        System.out.println("CWORLD_PROD_QA_REGISTRY_CATALOG_MATCH count=" + actual.size()); return true;
    }
    private static void restartSteps(ServerPlayer p) {
        require(progression(p).storyFlags.contains("production_qa_completed"), "Fresh run did not save completion checkpoint");
        require(progression(p).dialogueHistory.stream().anyMatch(t -> t.player() && t.choice() == 2), "Dialogue reply did not survive restart");
        var saved = NpcPlacementStore.INSTANCE.get("daycare_mira"); require(saved != null && saved.pitch() == 0 && saved.yaw() == 72, "Authored NPC pose did not persist");
        var cfg = CWorldConfig.INSTANCE; cfg.finalEncounterEnabled = false; CWorldConfig.save();
        add("restart-npc-recovery", "prod_close", "", q -> move(q, saved.x() - 3, saved.z() + 3, -135), q -> {
            var current = NpcPlacementStore.INSTANCE.get("daycare_mira");
            var npc = q.serverLevel().getEntity(UUID.fromString(current.entityUuid()));
            return npc != null && npc.getXRot() == 0 && npc.distanceToSqr(saved.x(), saved.y(), saved.z()) < .001
                    && Math.abs(net.minecraft.util.Mth.wrapDegrees(npc.getYRot() - saved.yaw())) < .1;
        });
        capture("37-npc-after-real-restart.png", "prod_world", "");
        capture("38-dialogue-after-real-restart.png", "prod_phone", "messages");
        add("restart-economy-verification", "prod_close", "", q -> {
            currency = api().getCurrencyList().stream().map(c -> c.getCurrencyType()).filter(c -> c.equalsIgnoreCase("BeastCoin")).findFirst().orElseThrow();
        }, q -> money(q, 60) && count(q, "cobblemon:poke_ball") == 16);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException("CWORLD_PROD_QA_FAILED " + message); }
}
