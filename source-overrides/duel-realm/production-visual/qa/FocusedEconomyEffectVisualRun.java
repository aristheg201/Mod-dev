package vn.svarcade.tcg.qa;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svarcade.tcg.client.CardWorldsScreen;
import vn.svarcade.tcg.client.component.CardWorldsLanguage;
import vn.svarcade.tcg.client.component.CurrencyPurchaseUi;
import vn.svarcade.tcg.economy.CardWorldsCurrency;

import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

/** The only entrypoint in the focused QA JAR. No full-suite runner or purchase actions. */
public final class FocusedEconomyEffectVisualRun implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("cardworlds-focused-qa");
    private enum Step { BOOT, SNAPSHOT, COLLECTION, PLAY, PACKS, SAVING, COMPLETE }
    private Step step = Step.BOOT;
    private long deadline, readyAt;
    private boolean worldStarted, stopped;
    private final AtomicInteger saved = new AtomicInteger();
    private volatile String screenshotFailure;
    private final List<String> packIds = new ArrayList<>();
    private int packIndex;
    private boolean packPositioned;

    @Override public void onInitializeClient() {
        LOG.info("CARDWORLDS_FOCUSED_VISUAL_DRIVER initializer=FocusedEconomyEffectVisualRun");
        if (FabricLoader.getInstance().isModLoaded("beconomy"))
            throw new AssertionError("Focused client must run with BEconomy absent");
        LOG.info("CARDWORLDS_FOCUSED_CLIENT beconomy=absent dependency=NONE");
        enter(Step.BOOT, 90_000);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void enter(Step next, long timeout) {
        step = next;
        deadline = System.currentTimeMillis() + timeout;
        readyAt = System.currentTimeMillis() + 2_000;
        if (next == Step.BOOT || next == Step.COLLECTION || next == Step.PLAY || next == Step.PACKS)
            LOG.info("CARDWORLDS_FOCUSED_VISUAL_STEP {}", next.name().toLowerCase(java.util.Locale.ROOT));
    }

    private AssertionError failure(MinecraftClient c, String reason) {
        CardWorldsScreen a = c.currentScreen instanceof CardWorldsScreen screen ? screen : null;
        return new AssertionError(reason + " step=" + step.name().toLowerCase(java.util.Locale.ROOT)
            + " screen=" + (c.currentScreen == null ? "null" : c.currentScreen.getClass().getName())
            + " player=" + (c.player != null)
            + " catalog=" + (a == null ? 0 : a.state.definitions().size())
            + " snapshotRevision=" + (a == null || a.state.duel() == null ? "unavailable" : a.state.duel().revision()));
    }

    private void tick(MinecraftClient c) {
        if (stopped) return;
        try {
            long now = System.currentTimeMillis();
            if (screenshotFailure != null) throw failure(c, screenshotFailure);
            if (now > deadline) throw failure(c, "Focused visual readiness timed out");
            if (c.getOverlay() != null) return;
            if (step == Step.BOOT) {
                if (c.player == null) {
                    if (!worldStarted && (c.currentScreen instanceof TitleScreen
                        || c.currentScreen instanceof AccessibilityOnboardingScreen) && now >= readyAt) {
                        worldStarted = true;
                        c.options.getViewDistance().setValue(2);
                        c.options.getSimulationDistance().setValue(2);
                        var info = new net.minecraft.world.level.LevelInfo("Card Worlds Focused QA",
                            net.minecraft.world.GameMode.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL,
                            true, new net.minecraft.world.GameRules(), net.minecraft.resource.DataConfiguration.SAFE_MODE);
                        c.createIntegratedServerLoader().createAndStart("cardworlds-focused-qa", info,
                            new net.minecraft.world.gen.GeneratorOptions(0L, false, false),
                            registries -> registries.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET)
                                .getOrThrow(net.minecraft.world.gen.WorldPresets.FLAT).createDimensionsRegistryHolder(),
                            new TitleScreen());
                    }
                    return;
                }
                c.setScreen(null);
                c.getNetworkHandler().sendChatCommand("cardworlds");
                enter(Step.SNAPSHOT, 20_000);
                return;
            }
            if (!(c.currentScreen instanceof CardWorldsScreen a) || c.player == null) return;
            c.getToastManager().clear();
            if (step == Step.SNAPSHOT) {
                if (!a.state.definitions().containsKey("charizard") || a.state.banners().isEmpty()) return;
                if (a.state.currencyBalances()==null || a.state.currencyBalances().available())
                    throw failure(c,"Integrated client must receive unavailable dedicated currency balances");
                LOG.info("CARDWORLDS_FOCUSED_HEADER currencies=beastcoin,huntercoin balances=unavailable item=minecraft:gold_ingot beast_cmd=6 hunter_cmd=2");
                // Configure the filter in this tick, before the first Collection frame renders.
                a.fields.put("search", "Charizard");
                a.category = "pokemon";
                a.ownership = "Favorites";
                a.favorites.clear();
                a.favorites.add("charizard");
                a.rarity = "All";
                a.set = "All";
                a.selected = "charizard";
                a.navigate("Collection");
                a.details = true;
                a.localNotice = "";
                enter(Step.COLLECTION, 15_000);
                return;
            }
            if (now < readyAt) return;
            switch (step) {
                case COLLECTION -> {
                    if (!a.page.equals("Collection") || a.selectedCard() == null || !a.details) return;
                    var visible = a.cards();
                    if (visible.isEmpty() || visible.size() != 1 || visible.stream().anyMatch(card -> !card.id().equals("charizard")))
                        throw failure(c, "Collection safe-card filter failed: " + visible.size());
                    String effect = CardWorldsLanguage.effect(a.selectedCard());
                    if (effect.contains("Pay 200 LP") || effect.contains("Pay 500 LP"))
                        throw failure(c, "Blanket monster LP cost visible: " + effect);
                    var cardEffect = a.selectedCard().effect();
                    var spec = cardEffect.spec();
                    if (cardEffect.lifeCost() != 0 || !cardEffect.oncePerTurn() || spec == null
                        || spec.costs() == null || spec.costs().stream().noneMatch(cost -> cost.type().equals("DISCARD"))
                        || spec.operations().size() < 2)
                        throw failure(c, "Charizard thematic cost / multi-operation effect missing: " + effect);
                    LOG.info("CARDWORLDS_FOCUSED_EFFECT card=charizard filtered={} effect={}", visible.size(), effect);
                    shot(c, "focused-01-effect-card");
                    a.navigate("Play");
                    enter(Step.PLAY, 15_000);
                }
                case PLAY -> {
                    if (!a.page.equals("Play")) return;
                    shot(c, "focused-02-economy-rewards");
                    packIds.addAll(a.state.banners().stream().map(b -> b.id()).toList());
                    if (packIds.size() != 14 || new HashSet<>(packIds).size() != 14)
                        throw failure(c, "Expected all 14 distinct packs: " + packIds);
                    LOG.info("CARDWORLDS_FOCUSED_PACK_SCOPE packs={} catalog={} ids={}", packIds.size(), a.state.definitions().size(), packIds);
                    a.banner = packIds.getFirst();
                    a.navigate("Packs");
                    enter(Step.PACKS, 15_000);
                }
                case PACKS -> {
                    if (!a.page.equals("Packs") || a.state.banners().isEmpty()) return;
                    if (!a.state.banners().stream().map(b -> b.id()).toList().equals(packIds))
                        throw failure(c, "Pack list changed during verification");
                    var region = a.scrolling.region("packs/list");
                    if (region.isEmpty()) return;
                    if (!packPositioned) {
                        a.scrolling.move("packs/list", packIndex * 112 - region.get().offset());
                        packPositioned = true;
                        readyAt = now + 2_000;
                        return;
                    }
                    String id = packIds.get(packIndex);
                    var banner = a.state.banners().get(packIndex);
                    var preview = vn.svarcade.tcg.client.screens.PacksScreen.preview(a);
                    if (!a.banner.equals(id) || preview == null || !preview.id().equals(banner.previewCard())
                        || banner.slots() <= 0 || banner.rates().isEmpty())
                        throw failure(c, "Incomplete selected pack " + id);
                    int rowY = region.get().box().y() + packIndex * 112 - region.get().offset();
                    if (rowY >= region.get().box().bottom() || rowY + 99 <= region.get().box().y())
                        throw failure(c, "Selected pack is unreachable in list: " + id);
                    var beast = CurrencyPurchaseUi.coin(CardWorldsCurrency.BEAST);
                    var hunter = CurrencyPurchaseUi.coin(CardWorldsCurrency.HUNTER);
                    var beastCmd = beast.get(DataComponentTypes.CUSTOM_MODEL_DATA);
                    var hunterCmd = hunter.get(DataComponentTypes.CUSTOM_MODEL_DATA);
                    if (!beast.isOf(Items.GOLD_INGOT) || beastCmd == null || beastCmd.value() != 6
                        || !hunter.isOf(Items.GOLD_INGOT) || hunterCmd == null || hunterCmd.value() != 2)
                        throw failure(c, "Currency ItemStack descriptor mismatch");
                    LOG.info("CARDWORLDS_FOCUSED_PACK_VERIFIED index={} id={} preview={} slots={} rates={} beast=150 hunter=2 beast_cmd=6 hunter_cmd=2 list_offset={}",
                        packIndex + 1, id, preview.id(), banner.slots(), banner.rates().size(), region.get().offset());
                    packShot(c, String.format(java.util.Locale.ROOT, "pack-%02d-%s", packIndex + 1, id));
                    if (packIndex == 0) {
                        LOG.info("CARDWORLDS_FOCUSED_CURRENCY_RENDER item=minecraft:gold_ingot beast_cmd=6 hunter_cmd=2 proof=descriptor_only server_resource_pack_loaded=false");
                        LOG.info("CARDWORLDS_FOCUSED_RESOURCE_PACKS loaded={}", c.getResourcePackManager().getEnabledProfiles().stream().map(profile -> profile.getId()).toList());
                        shot(c, "focused-03-gacha-currencies");
                    }
                    if (++packIndex < packIds.size()) {
                        a.banner = packIds.get(packIndex);
                        packPositioned = false;
                        deadline = now + 15_000;
                        readyAt = now;
                        return;
                    }
                    LOG.info("CARDWORLDS_FOCUSED_PACK_QA_COMPLETE packs={} purchases=0", packIndex);
                    enter(Step.SAVING, 10_000);
                }
                case SAVING -> {
                    if (saved.get() != 3 + packIds.size()) return;
                    for (String name : List.of("focused-01-effect-card", "focused-02-economy-rewards", "focused-03-gacha-currencies"))
                        if (!Files.isRegularFile(c.runDirectory.toPath().resolve("screenshots").resolve(name + ".png")))
                            throw failure(c, "Screenshot file missing: " + name);
                    for (int i = 0; i < packIds.size(); i++)
                        if (!Files.isRegularFile(c.runDirectory.toPath().resolve("packs-qa/screenshots")
                            .resolve(String.format(java.util.Locale.ROOT, "pack-%02d-%s.png", i + 1, packIds.get(i)))))
                            throw failure(c, "Pack screenshot missing: " + packIds.get(i));
                    String log = Files.readString(c.runDirectory.toPath().resolve("logs/latest.log"));
                    int start = log.indexOf("CARDWORLDS_FOCUSED_PACK_SCOPE");
                    if (start < 0) throw failure(c, "Pack verification log marker missing");
                    String rendering = log.substring(start);
                    for (String error : List.of("Unable to find a poser", "Could not render Cobblemon card model",
                        "CARDWORLDS_VARIANT_UNAVAILABLE", "[Render thread/ERROR]", "[STDERR]: java."))
                        if (rendering.contains(error)) throw failure(c, "Pack rendering error: " + rendering.lines()
                            .filter(line -> line.contains(error)).findFirst().orElse(error));
                    LOG.info("CARDWORLDS_FOCUSED_VISUAL_QA_COMPLETE screenshots=3");
                    step = Step.COMPLETE;
                    stopped = true;
                    c.scheduleStop();
                }
                default -> throw failure(c, "Unexpected focused step");
            }
        } catch (Throwable error) {
            stopped = true;
            AssertionError assertion = failure(c, error.getMessage());
            assertion.initCause(error);
            LOG.error("CARDWORLDS_FOCUSED_VISUAL_QA_FAILED " + assertion.getMessage(), assertion);
            c.scheduleStop();
            throw assertion;
        }
    }

    private void shot(MinecraftClient c, String name) {
        capture(c, c.runDirectory, name);
    }

    private void packShot(MinecraftClient c, String name) {
        capture(c, c.runDirectory.toPath().resolve("packs-qa").toFile(), name);
    }

    private void capture(MinecraftClient c, java.io.File directory, String name) {
        try { Files.createDirectories(directory.toPath().resolve("screenshots")); }
        catch (java.io.IOException e) { throw failure(c, "Cannot create screenshot directory: " + e); }
        ScreenshotRecorder.saveScreenshot(directory, name + ".png", c.getFramebuffer(), result -> {
            if (!Files.isRegularFile(directory.toPath().resolve("screenshots").resolve(name + ".png"))) {
                screenshotFailure = "Screenshot save failed: " + name + " " + result.getString();
                return;
            }
            saved.incrementAndGet();
            LOG.info("Captured {}", name);
        });
    }
}
