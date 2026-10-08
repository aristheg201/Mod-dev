package io.github.aristheg201.cobblemonworld.qa;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import io.github.aristheg201.cobblemonworld.client.ObjectiveHud;
import io.github.aristheg201.cobblemonworld.client.screen.ShopScreen;
import io.github.aristheg201.cobblemonworld.client.screen.TrainerPhoneScreen;
import io.github.aristheg201.cobblemonworld.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import java.util.*;

/** Test controls are registered only with explicit QA JVM properties. Inputs use normal gameplay packets. */
public final class ProductionQaClient {
    private static String token = "";
    private static int ticks;
    private static boolean interacted;
    private ProductionQaClient() {}
    public static void autoBattle(Minecraft mc) {
        if (!Boolean.getBoolean("cworld.qa.production")) return;
        var battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null || !battle.getMustChoose()) return;
        var request = battle.getFirstUnansweredRequest();
        if (request == null) {
            // Cobblemon may reject a choice made during an opponent switch. Retry through
            // its normal choice API rather than leaving an answered request stuck forever.
            if (mc.player.tickCount % 20 == 0 && battle.getLastAnsweredRequest() != null) {
                System.out.println("CWORLD_PROD_QA_RETRY_CHOICE " + battle.getLastAnsweredRequest().getResponse());
                battle.cancelLastAnsweredRequest();
            }
            return;
        }
        if (request.getForceSwitch()) {
            var actor = battle.getParticipatingActor(mc.player.getUUID());
            if (actor == null) return;
            for (var pokemon : actor.getPokemon()) {
                if (pokemon.getCurrentHealth() > 0 && actor.getActivePokemon().stream().noneMatch(a -> a.getBattlePokemon() != null && a.getBattlePokemon().getUuid().equals(pokemon.getUuid()))) {
                    request.setResponse(new SwitchActionResponse(pokemon.getUuid())); break;
                }
            }
        } else if (request.getMoveSet() != null) {
            var moves = request.getMoveSet().getMoves().stream().filter(m -> m.canBeUsed()).toList();
            if (moves.isEmpty()) return;
            var opposite = request.getActivePokemon().getOppositeOpponent();
            if (!(opposite instanceof com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon target) || target.getBattlePokemon() == null) return;
            var move = moves.stream().max(Comparator.comparingDouble(m -> qaMoveScore(m.getId(), target.getBattlePokemon()))).orElseThrow();
            request.setResponse(new MoveActionResponse(move.getId(), opposite.getPNX(), null));
        }
        if (request.getResponse() != null) battle.checkForFinishedChoosing();
    }
    private static double qaMoveScore(String move, com.cobblemon.mod.common.client.battle.ClientBattlePokemon target) {
        // Choose among the four authored QA Mewtwo attacks. This is test input only,
        // and changes neither damage calculation nor the opposing trainer's AI.
        var template = com.cobblemon.mod.common.api.moves.Moves.getByName(move);
        if (template == null) return 0;
        String type = template.getElementalType().getShowdownId();
        double score = template.getPower() * (type.equals("psychic") ? 1.5 : 1);
        var strong = switch (type) {
            case "fighting" -> Set.of("normal", "ice", "rock", "dark", "steel");
            case "ice" -> Set.of("grass", "ground", "flying", "dragon");
            case "fire" -> Set.of("grass", "ice", "bug", "steel");
            case "electric" -> Set.of("water", "flying");
            default -> Set.of("fighting", "poison");
        };
        var weak = switch (type) {
            case "fighting" -> Set.of("poison", "flying", "psychic", "bug", "fairy");
            case "ice" -> Set.of("fire", "water", "ice", "steel");
            case "fire" -> Set.of("fire", "water", "rock", "dragon");
            case "electric" -> Set.of("electric", "grass", "dragon");
            default -> Set.of("psychic", "steel");
        };
        for (var defending : target.getSpecies().getStandardForm().getTypes()) {
            String id = defending.getShowdownId();
            if ((type.equals("psychic") && id.equals("dark")) || (type.equals("fighting") && id.equals("ghost")) || (type.equals("electric") && id.equals("ground"))) return 0;
            if (strong.contains(id)) score *= 2;
            if (weak.contains(id)) score *= .5;
        }
        return score;
    }
    /** 0 waits, 1 acknowledges, 2 captures the actual framebuffer through the normal screenshot helper. */
    public static int perform(Minecraft mc, QaControlPayload p) {
        if (!p.secondary().equals(token)) { token = p.secondary(); ticks = 0; interacted = false; }
        ticks++;
        if (ticks == 1 && (p.action().equals("prod_phone") || p.action().equals("prod_shop") || p.action().equals("prod_world"))) {
            mc.getToasts().clear();
            if (!p.action().equals("prod_world")) org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), mc.getWindow().getScreenWidth() - 4, mc.getWindow().getScreenHeight() - 4);
        }
        switch (p.action()) {
            case "prod_interact" -> {
                var npc = mc.level.getEntity(Integer.parseInt(p.primary()));
                if (npc == null) return 0;
                if (!interacted) { mc.gameMode.interact(mc.player, npc, InteractionHand.MAIN_HAND); interacted = true; ticks = 1; }
                return ticks > 12 ? 1 : 0;
            }
            case "prod_phone" -> {
                if (ticks == 1) { mc.setScreen(null); mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); }
                if (!(mc.screen instanceof TrainerPhoneScreen phone)) return 0;
                phone.qaSelectApp(p.primary()); return ticks > 16 ? 2 : 0;
            }
            case "prod_reply" -> {
                if (!(mc.screen instanceof TrainerPhoneScreen phone)) return 0;
                if (ticks == 1) phone.qaClickReply(Integer.parseInt(p.primary()));
                return ticks > 20 ? 2 : 0;
            }
            case "prod_pin" -> {
                if (!(mc.screen instanceof TrainerPhoneScreen phone)) return 0;
                if (ticks == 1) phone.qaPinQuest();
                return ticks > 20 ? 2 : 0;
            }
            case "prod_world" -> {
                if (ticks == 1) mc.setScreen(null);
                if (ticks > 16 && ObjectiveHud.qaTarget() == null) throw new IllegalStateException("Navigation payload did not arrive");
                return ticks > 20 ? 2 : 0;
            }
            case "prod_shop" -> {
                if (!(mc.screen instanceof ShopScreen shop)) return 0;
                if (p.primary().equals("noeconomy")) {
                    if (shop.qaSnapshot().economyAvailable()) throw new IllegalStateException("Missing BEconomy enabled shop purchasing");
                    return ticks > 20 ? 2 : 0;
                }
                if (ticks == 1) {
                    if (p.primary().startsWith("select:")) shop.qaClickEntry(p.primary().substring(7));
                    else if (p.primary().startsWith("select_namespace:")) {
                        String id = shop.qaSnapshot().entries().stream().filter(e -> e.item().startsWith(p.primary().substring(17) + ":")).findFirst().orElseThrow().id();
                        shop.qaClickEntry(id);
                    }
                    else if (p.primary().startsWith("scale:")) { mc.options.guiScale().set(Integer.parseInt(p.primary().substring(6))); mc.resizeDisplay(); }
                    else if (p.primary().startsWith("scroll:")) shop.mouseScrolled(0, 0, 0, -Integer.parseInt(p.primary().substring(7)));
                    else if (!p.primary().isBlank()) shop.qaClickCategory(p.primary());
                }
                if (shop.qaSnapshot().entries().isEmpty() && shop.qaSnapshot().id().equals("fashion_elle")) throw new IllegalStateException("Fashion catalog empty in actual screen");
                return ticks > 20 ? 2 : 0;
            }
            case "prod_buy" -> {
                if (!(mc.screen instanceof ShopScreen shop)) return 0;
                if (ticks == 1) {
                    if (p.primary().equals("double")) {
                        ClientPlayNetworking.send(new ShopBuyPayload(shop.qaSnapshot().id(), "poke_ball", 1));
                        ClientPlayNetworking.send(new ShopBuyPayload(shop.qaSnapshot().id(), "poke_ball", 1));
                    } else if (p.primary().equals("invalid")) ClientPlayNetworking.send(new ShopBuyPayload(shop.qaSnapshot().id(), "poke_ball", -100));
                    else if (p.primary().equals("unknown")) ClientPlayNetworking.send(new ShopBuyPayload(shop.qaSnapshot().id(), "poke_ball:price=0", 1));
                    else if (p.primary().equals("request")) ClientPlayNetworking.send(new ShopBuyPayload(shop.qaSnapshot().id(), "poke_ball", 1));
                    else shop.qaClickBuy();
                }
                return ticks > 20 ? 2 : 0;
            }
            case "prod_locale" -> {
                if (ticks == 1) {
                    mc.options.languageCode = p.primary(); mc.getLanguageManager().setSelected(p.primary()); mc.reloadResourcePacks();
                }
                return ticks > 100 && mc.getOverlay() == null ? 1 : 0;
            }
            case "prod_pack" -> {
                if (ticks == 1) {
                    String packId = "file/cworld-qa-item-model";
                    var repository = mc.getResourcePackRepository();
                    if (p.primary().equals("on")) {
                        var root = mc.gameDirectory.toPath().resolve("resourcepacks/cworld-qa-item-model");
                        try {
                            java.nio.file.Files.createDirectories(root.resolve("assets/cobblemon/models/item"));
                            java.nio.file.Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":34,\"description\":\"QA model override\"}}");
                            java.nio.file.Files.writeString(root.resolve("assets/cobblemon/models/item/poke_ball.json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/diamond\"}}");
                        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
                        repository.reload();
                        if (!repository.addPack(packId)) throw new IllegalStateException("QA resource pack not discovered");
                        mc.options.resourcePacks.add(packId);
                    } else {
                        repository.removePack(packId); mc.options.resourcePacks.remove(packId);
                    }
                    mc.reloadResourcePacks();
                }
                return ticks > 100 && mc.getOverlay() == null ? 1 : 0;
            }
            case "prod_faction" -> {
                if (ticks == 1) ClientPlayNetworking.send(new PhoneActionPayload(p.primary(), "QA Rangers", ""));
                return ticks > 20 ? 1 : 0;
            }
            case "prod_reset" -> {
                if (ticks == 1) mc.player.connection.sendCommand(p.primary());
                return ticks > 20 ? 1 : 0;
            }
            case "prod_close" -> { mc.setScreen(null); return ticks > 10 ? 1 : 0; }
            default -> throw new IllegalStateException("Unknown production QA control " + p.action());
        }
    }
}
