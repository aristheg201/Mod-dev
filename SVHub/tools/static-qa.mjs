import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const failures = [];
function walk(directory, extension, files = []) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) walk(target, extension, files);
    else if (entry.name.endsWith(extension)) files.push(target);
  }
  return files;
}
function checkBalanced(file) {
  const source = fs.readFileSync(file, "utf8");
  const stack = [], pairs = { ")": "(", "]": "[", "}": "{" };
  let quote = null, escaped = false, lineComment = false, blockComment = false;
  for (let index = 0; index < source.length; index++) {
    const char = source[index], next = source[index + 1];
    if (lineComment) { if (char === "\n") lineComment = false; continue; }
    if (blockComment) { if (char === "*" && next === "/") { blockComment = false; index++; } continue; }
    if (quote) {
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === quote) quote = null;
      continue;
    }
    if (char === "/" && next === "/") { lineComment = true; index++; continue; }
    if (char === "/" && next === "*") { blockComment = true; index++; continue; }
    if (char === '"' || char === "'") { quote = char; continue; }
    if (char === "(" || char === "[" || char === "{") stack.push(char);
    else if (pairs[char] && stack.pop() !== pairs[char]) { failures.push(`${file}: unmatched ${char}`); return; }
  }
  if (stack.length || quote || blockComment) failures.push(`${file}: unterminated delimiter, quote, or comment`);
}
const kotlinFiles = walk(path.join(root, "src"), ".kt");
kotlinFiles.forEach(checkBalanced);
const screenPath = path.join(root, "src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/NativePlatformScreen.kt");
const screen = fs.readFileSync(screenPath, "utf8");
const translationSources = kotlinFiles;
const translationKeys = translationSources.flatMap((file) => {
  const source = fs.readFileSync(file, "utf8");
  const direct = [...source.matchAll(/(?<![A-Za-z])tr\("([^"]+)"\)/g)].map((match) => match[1]);
  const semantic = [...source.matchAll(/"(gui\.svhub\.[a-z0-9_.-]+)"/g)].map((match) => match[1]);
  return [...direct, ...semantic].filter((key) => !key.includes("$") && !key.endsWith("."));
});
for (const locale of ["en_us", "vi_vn"]) {
  const lang = JSON.parse(fs.readFileSync(path.join(root, `src/main/resources/assets/svhub/lang/${locale}.json`), "utf8"));
  const missing = [...new Set(translationKeys)].filter((key) => !(key in lang));
  if (missing.length) failures.push(`${locale}: missing ${missing.join(", ")}`);
  else console.log(`${locale}: ${translationKeys.length}/${translationKeys.length} native UI translation calls covered`);
}
const properties = fs.readFileSync(path.join(root, "gradle.properties"), "utf8");
const metadata = fs.readFileSync(path.join(root, "src/main/resources/fabric.mod.json"), "utf8");
if (!/^mod_version=0\.4\.5\s*$/m.test(properties)) failures.push("gradle.properties: expected mod_version=0.4.5");
if (!metadata.includes('"version": "${version}"')) failures.push("fabric.mod.json: Gradle version expansion marker missing");
for (const [name, marker] of [
  ["responsive layout", "NativeLayout.resolve(width, height)"],
  ["real companion presentation", "VanillaCompanionModelRenderer.render"],
  ["pixel art", "NativePixelArt.icon"],
  ["TFT renderer", "TftGameRenderer.render"],
  ["shared board scene", "NativeBoardSceneRenderer.render"]
]) if (!screen.includes(marker)) failures.push(`${screenPath}: missing ${name} marker`);

const read = (relative) => fs.readFileSync(path.join(root, relative), "utf8");
if (!/^cobblemon_version=1\.8\.1\+1\.21\.1\s*$/m.test(properties)) failures.push("gradle.properties: expected Cobblemon 1.8.1 compile target");
if (!metadata.includes('"config": "svhub.client.mixins.json"')) failures.push("fabric.mod.json: client poser compatibility mixin missing");
const poseSanitizer = read("src/main/kotlin/io/github/aristheg201/svhub/compat/CobblemonPoseSanitizer.kt");
const poseMixin = read("src/client/kotlin/io/github/aristheg201/svhub/mixin/client/VaryingModelRepositoryMixin.kt");
for (const marker of ["removeInvalidAnimationEntries", "removedNamedAnimations", "normalizeSingularAnimation"]) {
  if (!poseSanitizer.includes(marker)) failures.push(`CobblemonPoseSanitizer.kt: missing ${marker}`);
}
for (const marker of ["loadJsonPoser", "svhubSanitizePoserJson", "require = 1"]) {
  if (!poseMixin.includes(marker)) failures.push(`VaryingModelRepositoryMixin.kt: missing ${marker}`);
}
const nativePayloads = read("src/main/kotlin/io/github/aristheg201/svhub/native/network/NativePayloads.kt");
const nativeClient = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/NativePlatformClient.kt");
const nativeNetwork = read("src/main/kotlin/io/github/aristheg201/svhub/native/network/NativePlatformNetwork.kt");
const hubScreen = read("src/client/kotlin/io/github/aristheg201/svhub/client/gui/HubScreen.kt");
const profileStore = read("src/main/kotlin/io/github/aristheg201/svhub/native/NativeProfileStore.kt");
const rewardService = read("src/main/kotlin/io/github/aristheg201/svhub/native/NativeRewardService.kt");
const gachaTxn = read("src/main/kotlin/io/github/aristheg201/svhub/native/NativeGachaTransactionService.kt");
const gachaRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/GachaRouletteRenderer.kt");
const sceneRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/PokemonScene3D.kt");
const arenaRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/MinecraftArenaRenderer.kt");
const boardSceneRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/NativeBoardSceneRenderer.kt");
const visualRegistry = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/NativeGameVisualRegistry.kt");
const pokemonRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/cobblemon/PokemonModelRenderer.kt");
const fakemonCatalogProvider = read("src/client/kotlin/io/github/aristheg201/svhub/client/cobblemon/BundledFakemonCatalog.kt");
const spawnEcologyRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/gui/SpawnEcologyRenderer.kt");
const tftRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/TftGameRenderer.kt");
const cardTableRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/CardTable3DRenderer.kt");
const companionRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/VanillaCompanionModelRenderer.kt");
const arcadeService = read("src/main/kotlin/io/github/aristheg201/svhub/native/NativeArcadeService.kt");
const arcadeLifecyclePolicy = read("src/main/kotlin/io/github/aristheg201/svhub/native/NativeArcadeLifecyclePolicy.kt");
const sessionStore = read("src/main/kotlin/io/github/aristheg201/svhub/native/NativeArcadeSessionStore.kt");
const engineRuntime = read("src/main/kotlin/io/github/aristheg201/svhub/native/NativeGameEngineRuntime.kt");
const gamePersistence = read("src/main/kotlin/io/github/aristheg201/svhub/native/game/NativeGamePersistence.kt");
const tftSessionCore = read("src/main/kotlin/io/github/aristheg201/svhub/native/game/TftSession.kt");
const tftCombatCore = read("src/main/kotlin/io/github/aristheg201/svhub/native/game/tft/TftCombatEngine.kt");
const sceneProjection = read("src/main/kotlin/io/github/aristheg201/svhub/ui/SceneProjection.kt");
const scrollbarLayout = read("src/main/kotlin/io/github/aristheg201/svhub/ui/ScrollbarLayout.kt");
const nativePlatform = read("src/main/kotlin/io/github/aristheg201/svhub/native/NativePlatform.kt");
const nativeSkinService = read("src/main/kotlin/io/github/aristheg201/svhub/native/NativeSkinService.kt");
const towerDefenseCore = read("src/main/kotlin/io/github/aristheg201/svhub/native/game/TowerDefenseSession.kt");

for (const marker of ["viewId", "replacesViewId", "NativeCloseS2C", "NativeCloseC2S"]) {
  if (!nativePayloads.includes(marker)) failures.push(`NativePayloads.kt: missing lifecycle marker ${marker}`);
}
if (!nativeClient.includes("closedViews") || !nativeClient.includes("current.viewId == payload.viewId")) {
  failures.push("NativePlatformClient.kt: stale-view protection missing");
}
for (const marker of ["prepareForServerReplacement", "ClientPlayConnectionEvents.DISCONNECT", "closedViews.clear()"]) {
  if (!nativeClient.includes(marker)) failures.push(`NativePlatformClient.kt: missing lifecycle reset marker ${marker}`);
}
for (const marker of ["explicitlyClosed", "payload.viewId in closedViews"]) {
  if (!nativeClient.includes(marker)) failures.push(`NativePlatformClient.kt: missing reopen-race marker ${marker}`);
}
if (!read("src/client/kotlin/io/github/aristheg201/svhub/client/SVHubClient.kt").includes("Keep a user-initiated open pending")) {
  failures.push("SVHubClient.kt: reconnect open-pending protection missing");
}
if (!read("src/client/kotlin/io/github/aristheg201/svhub/client/SVHubClient.kt").includes("ClientPlayConnectionEvents.JOIN.register") ||
    !read("src/client/kotlin/io/github/aristheg201/svhub/client/SVHubClient.kt").includes("HubRequestSnapshotC2S(false)")) {
  failures.push("SVHubClient.kt: reconnect handshake prefetch missing");
}
const hubNetwork = read("src/main/kotlin/io/github/aristheg201/svhub/network/SVHubNetwork.kt");
for (const marker of ["Send the open intent even while the store is warming up", "ServerPlayNetworking.send(player,HubOpenS2C"]) {
  if (!hubNetwork.includes(marker)) failures.push(`SVHubNetwork.kt: missing reconnect-open marker ${marker}`);
}
for (const marker of ['module=="game"', "NativeArcadeService.leave(player)"]) {
  if (!nativeNetwork.includes(marker)) failures.push(`NativePlatformNetwork.kt: missing game-close lifecycle marker ${marker}`);
}
for (const marker of ["supersededByServer", "prepareForServerReplacement", "if (!supersededByServer)"]) {
  if (!screen.includes(marker)) failures.push(`NativePlatformScreen.kt: missing server-replacement lifecycle marker ${marker}`);
}
if (hubScreen.includes("take(18)")) failures.push("HubScreen.kt: sidebar still truncates navigation with take(18)");
for (const marker of ["draggingSidebarScrollbar", "enableScissor(0, viewportTop", "setSidebarScrollFromThumb"]) {
  if (!hubScreen.includes(marker)) failures.push(`HubScreen.kt: missing sidebar scroll marker ${marker}`);
}
for (const marker of ["mutateDurableOnce", "appliedTransactions", "persistedRevision"]) {
  if (!profileStore.includes(marker)) failures.push(`NativeProfileStore.kt: missing durability marker ${marker}`);
}
for (const marker of ["reward-journal", "JournalRecord", "isTransactionDurable"]) {
  if (!rewardService.includes(marker)) failures.push(`NativeRewardService.kt: missing reward WAL marker ${marker}`);
}
for (const marker of ["GRANTING", "ownedQuantity", "debitTx", "finalTx", "refundTx", "mutateDurableOnce"]) {
  if (!gachaTxn.includes(marker)) failures.push(`NativeGachaTransactionService.kt: missing transaction marker ${marker}`);
}
for (const marker of ["bonusPokemonUuid", "bonusPokemonSpecies", "ensureBonusPokemon", "bonus_pending"]) {
  if (!gachaTxn.includes(marker) && !read("src/main/kotlin/io/github/aristheg201/svhub/native/NativeSkinService.kt").includes(marker)) {
    failures.push(`Gacha bonus durability marker missing: ${marker}`);
  }
}
const skinService = read("src/main/kotlin/io/github/aristheg201/svhub/native/NativeSkinService.kt");
for (const marker of ["Cobblemon.storage.getParty", "Cobblemon.storage.getPC", "bonusPokemonUuidForRequest", "party.add(pokemon)"]) {
  if (!skinService.includes(marker)) failures.push(`NativeSkinService.kt: missing exactly-once bonus marker ${marker}`);
}
if (skinService.includes("givepokemonother")) failures.push("NativeSkinService.kt: bonus Pokémon still uses non-idempotent command delivery");
for (const marker of ["lastRoll", "requestId", "DURATION_MS", "u * u * u * u * u"]) {
  if (!gachaRenderer.includes(marker)) failures.push(`GachaRouletteRenderer.kt: missing authoritative roulette marker ${marker}`);
}
for (const marker of ["PokemonSceneState", "PokemonSceneEntity", "project(", "pruneScene", "motionSerial", "SceneEffectSignal", "SceneEffectKind.PROJECTILE", "camera: SceneCameraPreset", "renderEffects", "depthStride", "arenaId", "MinecraftArenaRenderer.renderTile", "MinecraftArenaRenderer.renderFoundation", "pathRoute", "moving = moving"]) {
  if (!sceneRenderer.includes(marker)) failures.push(`PokemonScene3D.kt: missing shared scene marker ${marker}`);
}
for (const marker of ["MinecraftArenaRegistry", "BuiltInRegistries.ITEM.getOptional", "assets/svhub/arenas", "ArenaTileRole.PATH"]) {
  if (!arenaRenderer.includes(marker.replace("assets/svhub/arenas", "arenas/"))) failures.push(`MinecraftArenaRenderer.kt: missing arena marker ${marker}`);
}
for (const arena of ["chess", "xiangqi", "ludo", "tft", "tower_defense"]) {
  const file = path.join(root, `src/main/resources/assets/svhub/arenas/${arena}.json`);
  if (!fs.existsSync(file)) failures.push(`Missing arena definition: ${arena}`);
}
for (const arena of ["chess", "xiangqi", "ludo", "tft", "tower_defense"]) {
  const file = path.join(root, `src/main/resources/assets/svhub/arenas/${arena}.json`);
  if (!fs.existsSync(file)) continue;
  const def = JSON.parse(fs.readFileSync(file, "utf8"));
  if (!("depth" in def)) failures.push(`${arena}.json: missing board depth`);
  if (!("detailEvery" in def)) failures.push(`${arena}.json: missing sparse-detail cadence`);
  if (["chess", "xiangqi", "ludo"].includes(arena) && def.detailEvery !== 0) failures.push(`${arena}.json: board must not spam block items per cell`);
  if (!def.surface) failures.push(`${arena}.json: missing explicit surface composition mode`);
  if (!def.borderColor || !def.edgeColor) failures.push(`${arena}.json: missing platform border/edge colors`);
}
for (const marker of ["renderFoundation", "renderPathRoute", "ArenaSurfaceMode.CHECKER", "ArenaSurfaceMode.GRID", "ArenaSurfaceMode.TACTICAL", "fillQuad", "Sparse environmental detail only"]) {
  if (!arenaRenderer.includes(marker)) failures.push(`MinecraftArenaRenderer.kt: missing arena-composition marker ${marker}`);
}
for (const marker of ["SceneCameraPreset", "SceneCameras", "SceneProjectionMetrics", "depthFor"]) {
  if (!sceneProjection.includes(marker)) failures.push(`SceneProjection.kt: missing camera/projection marker ${marker}`);
}
for (const marker of ["TOUCH_HIT_WIDTH = 14", "MIN_THUMB = 24", "scrollFromPointer"]) {
  if (!scrollbarLayout.includes(marker)) failures.push(`ScrollbarLayout.kt: missing touch-scroll marker ${marker}`);
}
for (const marker of ["SceneModelKey", "renderScene(", "instanceId"]) {
  if (!pokemonRenderer.includes(marker)) failures.push(`PokemonModelRenderer.kt: missing per-entity scene renderer marker ${marker}`);
}
for (const marker of ["resolveScenePoseType", "PoseType.STAND", "PoseType.WALK", "pose.animations.isNotEmpty()", "VaryingModelRepository.getPoser"]) {
  if (!pokemonRenderer.includes(marker)) failures.push(`PokemonModelRenderer.kt: missing native scene-pose marker ${marker}`);
}
for (const marker of ["BundledFakemonCatalog", "sourceForSpecies", "sourceForForm"]) {
  if (!fakemonCatalogProvider.includes(marker)) failures.push(`BundledFakemonCatalog.kt: missing ${marker}`);
}
const livelyCatalogPath = path.join(root, "src/main/resources/assets/svhub/fakemon_catalog/lively_mons_1_11.json");
if (!fs.existsSync(livelyCatalogPath)) failures.push("Missing Lively Mons Fakemon catalog");
else {
  const lively = JSON.parse(fs.readFileSync(livelyCatalogPath, "utf8"));
  if ((lively.species || []).length !== 73) failures.push(`Lively Mons catalog: expected 73 species, got ${(lively.species || []).length}`);
  for (const id of ["cobblemon:peccareck", "cobblemon:porygondelta"]) if (!(lively.species || []).includes(id)) failures.push(`Lively Mons catalog missing ${id}`);
}
for (const marker of ["BIOMES", "renderChips", "bucketColor", "environmentColor"]) {
  if (!spawnEcologyRenderer.includes(marker)) failures.push(`SpawnEcologyRenderer.kt: missing ${marker}`);
}
if (!tftRenderer.includes("PokemonScene3D.render") || tftRenderer.includes("renderUnit(gui, font, cell")) {
  failures.push("TftGameRenderer.kt: TFT board is not using the shared scene renderer");
}
for (const marker of ['"chess"', '"xiangqi"', '"tower_defense"', '"ludo"', "PokemonScene3D.render", "parseLegalMoves", "pathPosition"]) {
  if (!boardSceneRenderer.includes(marker)) failures.push(`NativeBoardSceneRenderer.kt: missing ${marker}`);
}
for (const marker of ["game_visuals/$gameId.json", "NativePieceVisual", "resourceManager.getResource"]) {
  if (!visualRegistry.includes(marker)) failures.push(`NativeGameVisualRegistry.kt: missing ${marker}`);
}
for (const game of ["chess", "xiangqi", "ludo"]) {
  const file = path.join(root, `src/main/resources/assets/svhub/game_visuals/${game}.json`);
  if (!fs.existsSync(file)) failures.push(`Missing game visual definition: ${game}`);
  else {
    const parsed = JSON.parse(fs.readFileSync(file, "utf8"));
    if (!parsed.pieces && !parsed.teams) failures.push(`${game}.json: no pieces/teams visual mapping`);
  }
}
for (const marker of ["sceneFrame?.layout?.pick", "actionPayload(action)", 'gameAct("deploy"', "unoPendingCard", 'tr("gui.svhub.uno.$color")']) {
  if (!screen.includes(marker)) failures.push(`NativePlatformScreen.kt: missing interaction marker ${marker}`);
}
if (screen.includes('"color" to "red"')) failures.push("NativePlatformScreen.kt: UNO wild color is still hardcoded to red");
if (!screen.includes("UUID.randomUUID().toString()") || !screen.includes("GachaRouletteRenderer.render")) {
  failures.push("NativePlatformScreen.kt: gacha request id / roulette integration missing");
}
for (const marker of ["CardTable3DRenderer.render", "VanillaCompanionModelRenderer.render", "draggingModuleScrollbar", "setModuleScrollFromThumb", "moduleMaxScroll", "ScrollbarLayout.resolve", "gui.svhub.arena.energy"]) {
  if (!screen.includes(marker)) failures.push(`NativePlatformScreen.kt: missing production UI marker ${marker}`);
}
if (screen.includes('arena.str("log")')) failures.push("NativePlatformScreen.kt: raw companion arena log is still rendered");
for (const marker of ["ScrollbarLayout.resolve", "scrollbar.hitRect.contains", "setSidebarScrollFromThumb"]) {
  if (!hubScreen.includes(marker)) failures.push(`HubScreen.kt: missing touch sidebar marker ${marker}`);
}
if (screen.includes("NativePixelArt.companion(")) failures.push("NativePlatformScreen.kt: companion arena still uses sprite placeholder");
if (screen.includes("coerceIn(0,1200)")) failures.push("NativePlatformScreen.kt: native module scroll still uses hardcoded 1200 clamp");
for (const marker of ["PokemonModelRenderer.renderScene", "Axis.ZP.rotationDegrees", '"uno"', '"pokecards"', "unoCardLabel", "unoTopLabel", 'I18n.get("gui.svhub.uno."']) {
  if (!cardTableRenderer.includes(marker)) failures.push(`CardTable3DRenderer.kt: missing ${marker}`);
}
if (cardTableRenderer.includes("active.uppercase()")) failures.push("CardTable3DRenderer.kt: UNO active color is still rendered raw");
if (!tftRenderer.includes('tr("gui.svhub.game.tft.title")')) failures.push("TftGameRenderer.kt: TFT title is still hardcoded");
for (const marker of ["InventoryScreen.renderEntityInInventoryFollowsMouse", "BuiltInRegistries.ENTITY_TYPE", "LivingEntity"]) {
  if (!companionRenderer.includes(marker)) failures.push(`VanillaCompanionModelRenderer.kt: missing ${marker}`);
}

for (const marker of ["NativeArcadeSessionStore.start", "restoreLoadedSessions", "persistSession", "NativeArcadeSessionStore.delete", "RESTART_RECONNECT_GRACE_MS"]) {
  if (!arcadeService.includes(marker)) failures.push(`NativeArcadeService.kt: missing recovery marker ${marker}`);
}
for (const marker of ["hasActiveSession", "existingSession", "gui.svhub.arcade.resumed"]) {
  if (!arcadeService.includes(marker)) failures.push(`NativeArcadeService.kt: missing resume marker ${marker}`);
}
for (const marker of ["ActiveSessionResolution.RESUME", "ActiveSessionResolution.STALE", "sessionPresent && viewPresent"]) {
  if (!arcadeLifecyclePolicy.includes(marker)) failures.push(`NativeArcadeLifecyclePolicy.kt: missing ${marker}`);
}
for (const marker of ["pendingOpen", 'module == "arcade" && NativeArcadeService.hasActiveSession(player)', '"leave_active"']) {
  if (!nativePlatform.includes(marker)) failures.push(`NativePlatform.kt: missing reconnect/arcade marker ${marker}`);
}
if (!nativeNetwork.includes("lastIntentAt.remove(player.uuid);ServerPlayNetworking.send(player,NativeOpenS2C")) {
  failures.push("NativePlatformNetwork.kt: new native views inherit stale intent throttle");
}
for (const marker of ["gui.svhub.arcade.current_game", 'intent("resume"', 'intent("leave_active"']) {
  if (!screen.includes(marker)) failures.push(`NativePlatformScreen.kt: missing active-game recovery control ${marker}`);
}
if (arcadeService.includes("Files.") || arcadeService.includes("Files.read")) {
  failures.push("NativeArcadeService.kt: server lifecycle must not perform direct disk IO");
}
for (const marker of ["AtomicFiles.writeUtf8", 'MessageDigest.getInstance("SHA-256")', "ConcurrentLinkedQueue", "SAVE_RETRY_MS", "MAX_JSON_CHARS"]) {
  if (!sessionStore.includes(marker)) failures.push(`NativeArcadeSessionStore.kt: missing durable store marker ${marker}`);
}
if (sessionStore.includes("root.resolve(sessionId")) failures.push("NativeArcadeSessionStore.kt: raw session id must not be used as a filesystem path");
for (const marker of ["snapshotState(): JsonObject", "captureState(", "stateJson", "snapshotEpochMs"]) {
  if (!engineRuntime.includes(marker)) failures.push(`NativeGameEngineRuntime.kt: missing actor snapshot marker ${marker}`);
}
if (!gamePersistence.includes('"tft" ->')) failures.push("NativeGamePersistence.kt: TFT restore codec missing");
for (const marker of ["override fun snapshotState", "pool.snapshotCounts", "restoreSnapshot", "TftCombatEngine(set, savedMatch.combat)", "setDefinition = set"]) {
  if (!tftSessionCore.includes(marker)) failures.push(`TftSession.kt: missing recovery marker ${marker}`);
}
for (const marker of ["TftCombatSnapshot", "NativeStatefulRandom", "constructor(set: TftSetDefinition, snapshot: TftCombatSnapshot)", "fun snapshotState(): TftCombatSnapshot"]) {
  if (!tftCombatCore.includes(marker)) failures.push(`TftCombatEngine.kt: missing live-combat recovery marker ${marker}`);
}

for (const marker of ["gameTitleFor", "localizedGameStatus", "localizedGameAction", "trOr("]) {
  if (!screen.includes(marker)) failures.push(`NativePlatformScreen.kt: missing semantic localization marker ${marker}`);
}
if (screen.includes('gui.drawString(font,fit(view.str("status")')) {
  failures.push("NativePlatformScreen.kt: raw server game status is still rendered directly");
}
if (tftRenderer.includes("fit(font, status, 250)")) {
  failures.push("TftGameRenderer.kt: raw server TFT status is still rendered directly");
}
const requiredSemanticKeys = [
  "gui.svhub.game.chess.title",
  "gui.svhub.game.xiangqi.title",
  "gui.svhub.game.ludo.title",
  "gui.svhub.game.uno.title",
  "gui.svhub.game.pokecards.title",
  "gui.svhub.game.tower_defense.title",
  "gui.svhub.game.tft.title",
  "gui.svhub.game.turn",
  "gui.svhub.game.finished",
  "gui.svhub.game.finished_winner",
  "gui.svhub.action.resign",
  "gui.svhub.action.offer_draw",
  "gui.svhub.action.accept_draw",
  "gui.svhub.action.roll",
  "gui.svhub.action.move",
  "gui.svhub.action.draw",
  "gui.svhub.action.start_wave",
  "gui.svhub.chess.check",
  "gui.svhub.xiangqi.check",
  "gui.svhub.ludo.roll_wait",
  "gui.svhub.ludo.rolled",
  "gui.svhub.ludo.move_piece",
  "gui.svhub.pokecards.pick",
  "gui.svhub.pokecards.waiting",
  "gui.svhub.td.prepare",
  "gui.svhub.td.wave_running",
  "gui.svhub.tft.eliminated",
  "gui.svhub.tft.vs",
  "gui.svhub.uno.playable",
  "gui.svhub.uno.card.number",
  "gui.svhub.uno.card.skip",
  "gui.svhub.uno.card.reverse",
  "gui.svhub.uno.card.draw2",
  "gui.svhub.uno.card.wild",
  "gui.svhub.uno.card.wild4",
  "gui.svhub.profile.loading",
  "gui.svhub.error.no_action",
  "gui.svhub.error.invalid_module",
  "gui.svhub.error.invalid_action",
  "gui.svhub.companion.selected",
  "gui.svhub.companion.invalid",
  "gui.svhub.arena.started",
  "gui.svhub.arena.updated",
  "gui.svhub.arena.select_first",
  "gui.svhub.arena.victory",
  "gui.svhub.arena.defeat",
  "gui.svhub.arena.turn",
  "gui.svhub.arena.energy",
  "gui.svhub.game.action_applied",
  "gui.svhub.game.action_rejected",
  "gui.svhub.skin.not_found",
  "gui.svhub.skin.shop_opened",
  "gui.svhub.skin.backend_unavailable",
  "gui.svhub.skin.equipped",
  "gui.svhub.skin.removed",
  "gui.svhub.skin.operation_failed",
  "gui.svhub.skin.inventory_opened",
  "gui.svhub.arcade.recovering",
  "gui.svhub.arcade.invalid_game",
  "gui.svhub.arcade.server_busy",
  "gui.svhub.arcade.invalid_mode",
  "gui.svhub.arcade.active_exists",
  "gui.svhub.arcade.queued",
  "gui.svhub.arcade.matched",
  "gui.svhub.arcade.started",
  "gui.svhub.arcade.queue_left",
  "gui.svhub.arcade.not_queued",
  "gui.svhub.arcade.no_active",
  "gui.svhub.arcade.session_expired",
  "gui.svhub.arcade.processing",
  "gui.svhub.arcade.worker_busy",
  "gui.svhub.arcade.left",
  "gui.svhub.arcade.restored",
  "gui.svhub.arcade.resumed",
  "gui.svhub.arcade.current_game",
  "gui.svhub.leave_game",
  "gui.svhub.td.card_stats",
  "gui.svhub.editor",
  "gui.svhub.sidebar.contents",
  "gui.svhub.not_found"
];
for (const locale of ["en_us", "vi_vn"]) {
  const lang = JSON.parse(fs.readFileSync(path.join(root, `src/main/resources/assets/svhub/lang/${locale}.json`), "utf8"));
  for (const key of requiredSemanticKeys) if (!(key in lang)) failures.push(`${locale}: missing semantic game key ${key}`);
}
const requiredMessageKeys = [
  "message.svhub.reward.both",
  "message.svhub.reward.tokens",
  "message.svhub.reward.tickets",
  "message.svhub.profile_loading"
];
for (const locale of ["en_us", "vi_vn"]) {
  const lang = JSON.parse(fs.readFileSync(path.join(root, `src/main/resources/assets/svhub/lang/${locale}.json`), "utf8"));
  for (const key of requiredMessageKeys) if (!(key in lang)) failures.push(`${locale}: missing system message key ${key}`);
}

for (const marker of ["TftLayoutResolver.resolve", "PokemonModelRenderer.render", "renderTraits", "renderPlayers", "renderBoard", "renderFooter", 'hooks.action("refresh"', 'hooks.action("buy_xp"', 'hooks.action("sell"', 'hooks.action("equip_item"', "observeCombat", "SceneCameras.TFT", "effects = effectSignals", "unitCatalog", "traitCatalog", "unitTooltip", "traitTooltip", "renderHoverTooltip", 'arenaId = fields.str("arenaId"']) {
  if (!tftRenderer.includes(marker)) failures.push(`TftGameRenderer.kt: missing ${marker}`);
}
for (const marker of ["targetId.orEmpty()", "unit.casts", "unit.damageDone", "unit.healingDone", "encodeUnitCatalog(observed, includeShop = !scouting)", "encodeTraitCatalog(observed, includeShop = !scouting)", "abilityName", "teamEffects"]) {
  if (!tftSessionCore.includes(marker)) failures.push(`TftSession.kt: missing render metadata marker ${marker}`);
}
for (const marker of ["fireSerial", "targetEnemyId", '"towerEncoding" to "v2"']) {
  if (!towerDefenseCore.includes(marker)) failures.push(`TowerDefenseSession.kt: missing projectile metadata marker ${marker}`);
}
for (const marker of ["SceneCameras.LANE", 'SceneEffectSignal("td:shot:', "SceneCameras.XIANGQI", "SceneCameras.LUDO"]) {
  if (!boardSceneRenderer.includes(marker)) failures.push(`NativeBoardSceneRenderer.kt: missing camera/effect marker ${marker}`);
}
for (const marker of ["publicMessage(result)", "gui.svhub.game.action_applied", "gui.svhub.game.action_rejected"]) {
  if (!engineRuntime.includes(marker)) failures.push(`NativeGameEngineRuntime.kt: missing semantic result boundary ${marker}`);
}
if (rewardService.includes('Component.literal("SVHub reward')) failures.push("NativeRewardService.kt: reward message is still a raw literal");
for (const marker of ["message.svhub.reward.both", "message.svhub.reward.tokens", "message.svhub.reward.tickets"]) {
  if (!rewardService.includes(marker)) failures.push(`NativeRewardService.kt: missing localized reward marker ${marker}`);
}
for (const marker of ["gui.svhub.skin.equipped", "gui.svhub.skin.removed", "gui.svhub.skin.operation_failed"]) {
  if (!nativeSkinService.includes(marker)) failures.push(`NativeSkinService.kt: missing semantic skin result ${marker}`);
}
for (const marker of ["message.svhub.profile_loading", "gui.svhub.error.invalid_action", "gui.svhub.arena.select_first"]) {
  if (!nativePlatform.includes(marker)) failures.push(`NativePlatform.kt: missing semantic platform marker ${marker}`);
}
// Generic TFT/TD runtimes must never acquire dependencies on shipped content identifiers.
const genericRuntimeFiles = [
  "src/main/kotlin/io/github/aristheg201/svhub/native/game/TftBotPlanner.kt",
  "src/main/kotlin/io/github/aristheg201/svhub/native/game/TowerDefenseSession.kt",
  "src/main/kotlin/io/github/aristheg201/svhub/native/game/RouteCombatSystems.kt"
];
const shippedContentIds = [
  "one_piece", "monsterverse", "dc_universe", "green_lantern_corps", "than_tai",
  "sector_2814", "grand_line", "gotham_rooftops", "monster_island", "celestial_vault"
];
for (const relative of genericRuntimeFiles) {
  const source = read(relative);
  for (const id of shippedContentIds) {
    if (source.includes(`"${id}"`)) failures.push(`${relative}: generic runtime hardcodes shipped content id ${id}`);
  }
}
if (boardSceneRenderer.includes('definition("ludo")')) failures.push("NativeBoardSceneRenderer.kt: shipped Ludo arena lookup must come from authoritative state");
if (/minOf\(96|until 96/.test(boardSceneRenderer)) failures.push("NativeBoardSceneRenderer.kt: TD capacity must derive from board dimensions");
if (read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/CardTable3DRenderer.kt").includes("TABLE_UNO")) failures.push("CardTable3DRenderer.kt: shipped UNO theme must come from resources");

if (failures.length) { console.error(failures.join("\n")); process.exit(1); }
console.log(`Kotlin delimiter scan: ${kotlinFiles.length} files passed`);
console.log("SVHub 0.4.5 structural QA passed (runtime/gameplay/visual verification is separate)");
