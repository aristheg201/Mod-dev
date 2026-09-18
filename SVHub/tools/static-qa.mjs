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
  return [...direct, ...semantic].filter((key) => !key.includes("$"));
});
for (const locale of ["en_us", "vi_vn"]) {
  const lang = JSON.parse(fs.readFileSync(path.join(root, `src/main/resources/assets/svhub/lang/${locale}.json`), "utf8"));
  const missing = [...new Set(translationKeys)].filter((key) => !(key in lang));
  if (missing.length) failures.push(`${locale}: missing ${missing.join(", ")}`);
  else console.log(`${locale}: ${translationKeys.length}/${translationKeys.length} native UI translation calls covered`);
}
const properties = fs.readFileSync(path.join(root, "gradle.properties"), "utf8");
const metadata = fs.readFileSync(path.join(root, "src/main/resources/fabric.mod.json"), "utf8");
if (!/^mod_version=0\.4\.1\s*$/m.test(properties)) failures.push("gradle.properties: expected mod_version=0.4.1");
if (!metadata.includes('"version": "${version}"')) failures.push("fabric.mod.json: Gradle version expansion marker missing");
for (const [name, marker] of [
  ["responsive layout", "NativeLayout.resolve(width, height)"],
  ["real companion presentation", "VanillaCompanionModelRenderer.render"],
  ["pixel art", "NativePixelArt.icon"],
  ["TFT renderer", "TftGameRenderer.render"],
  ["shared board scene", "NativeBoardSceneRenderer.render"]
]) if (!screen.includes(marker)) failures.push(`${screenPath}: missing ${name} marker`);

const read = (relative) => fs.readFileSync(path.join(root, relative), "utf8");
const nativePayloads = read("src/main/kotlin/io/github/aristheg201/svhub/native/network/NativePayloads.kt");
const nativeClient = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/NativePlatformClient.kt");
const hubScreen = read("src/client/kotlin/io/github/aristheg201/svhub/client/gui/HubScreen.kt");
const profileStore = read("src/main/kotlin/io/github/aristheg201/svhub/native/NativeProfileStore.kt");
const rewardService = read("src/main/kotlin/io/github/aristheg201/svhub/native/NativeRewardService.kt");
const gachaTxn = read("src/main/kotlin/io/github/aristheg201/svhub/native/NativeGachaTransactionService.kt");
const gachaRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/GachaRouletteRenderer.kt");
const sceneRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/PokemonScene3D.kt");
const boardSceneRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/NativeBoardSceneRenderer.kt");
const visualRegistry = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/NativeGameVisualRegistry.kt");
const pokemonRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/cobblemon/PokemonModelRenderer.kt");
const tftRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/TftGameRenderer.kt");
const cardTableRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/CardTable3DRenderer.kt");
const companionRenderer = read("src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/VanillaCompanionModelRenderer.kt");

for (const marker of ["viewId", "replacesViewId", "NativeCloseS2C", "NativeCloseC2S"]) {
  if (!nativePayloads.includes(marker)) failures.push(`NativePayloads.kt: missing lifecycle marker ${marker}`);
}
if (!nativeClient.includes("closedViews") || !nativeClient.includes("current.viewId == payload.viewId")) {
  failures.push("NativePlatformClient.kt: stale-view protection missing");
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
for (const marker of ["lastRoll", "requestId", "DURATION_MS", "u * u * u * u * u"]) {
  if (!gachaRenderer.includes(marker)) failures.push(`GachaRouletteRenderer.kt: missing authoritative roulette marker ${marker}`);
}
for (const marker of ["PokemonSceneState", "PokemonSceneEntity", "project(", "pruneScene", "motionSerial"]) {
  if (!sceneRenderer.includes(marker)) failures.push(`PokemonScene3D.kt: missing shared scene marker ${marker}`);
}
for (const marker of ["SceneModelKey", "renderScene(", "instanceId"]) {
  if (!pokemonRenderer.includes(marker)) failures.push(`PokemonModelRenderer.kt: missing per-entity scene renderer marker ${marker}`);
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
for (const marker of ["CardTable3DRenderer.render", "VanillaCompanionModelRenderer.render", "draggingModuleScrollbar", "setModuleScrollFromThumb", "moduleMaxScroll"]) {
  if (!screen.includes(marker)) failures.push(`NativePlatformScreen.kt: missing production UI marker ${marker}`);
}
if (screen.includes("NativePixelArt.companion(")) failures.push("NativePlatformScreen.kt: companion arena still uses sprite placeholder");
if (screen.includes("coerceIn(0,1200)")) failures.push("NativePlatformScreen.kt: native module scroll still uses hardcoded 1200 clamp");
for (const marker of ["PokemonModelRenderer.renderScene", "Axis.ZP.rotationDegrees", '"uno"', '"pokecards"']) {
  if (!cardTableRenderer.includes(marker)) failures.push(`CardTable3DRenderer.kt: missing ${marker}`);
}
for (const marker of ["InventoryScreen.renderEntityInInventoryFollowsMouse", "BuiltInRegistries.ENTITY_TYPE", "LivingEntity"]) {
  if (!companionRenderer.includes(marker)) failures.push(`VanillaCompanionModelRenderer.kt: missing ${marker}`);
}

for (const marker of ["TftLayoutResolver.resolve", "PokemonModelRenderer.render", "renderTraits", "renderPlayers", "renderBoard", "renderFooter", 'hooks.action("refresh"', 'hooks.action("buy_xp"', 'hooks.action("sell"', 'hooks.action("equip_item"']) {
  if (!tftRenderer.includes(marker)) failures.push(`TftGameRenderer.kt: missing ${marker}`);
}
if (failures.length) { console.error(failures.join("\n")); process.exit(1); }
console.log(`Kotlin delimiter scan: ${kotlinFiles.length} files passed`);
console.log("SVHub 0.4.1 structural QA passed (runtime/gameplay/visual verification is separate)");
