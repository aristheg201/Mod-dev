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
const clientUiFiles = walk(path.join(root, "src/client/kotlin/io/github/aristheg201/svhub/client"), ".kt");
const translationKeys = clientUiFiles.flatMap((file) => {
  const source = fs.readFileSync(file, "utf8");
  return [...source.matchAll(/(?<![A-Za-z])tr\("([^"]+)"\)/g)].map((match) => match[1]).filter((key) => !key.includes("$"));
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
  ["compact companion presentation", "renderCompanionsCompact"],
  ["pixel art", "NativePixelArt.icon"],
  ["TFT renderer", "TftGameRenderer.render"]
]) if (!screen.includes(marker)) failures.push(`${screenPath}: missing ${name} marker`);
const tftRenderer = fs.readFileSync(path.join(root, "src/client/kotlin/io/github/aristheg201/svhub/client/nativeui/TftGameRenderer.kt"), "utf8");
for (const marker of ["TftLayoutResolver.resolve", "PokemonModelRenderer.render", "renderTraits", "renderPlayers", "renderBoard", "renderFooter", 'hooks.action("refresh"', 'hooks.action("buy_xp"', 'hooks.action("sell"', 'hooks.action("equip_item"']) {
  if (!tftRenderer.includes(marker)) failures.push(`TftGameRenderer.kt: missing ${marker}`);
}
if (failures.length) { console.error(failures.join("\n")); process.exit(1); }
console.log(`Kotlin delimiter scan: ${kotlinFiles.length} files passed`);
console.log("SVHub 0.4.1 static QA passed (not a gameplay or visual test)");
