from pathlib import Path

p = Path("src/qa/java/vn/svarcade/tcg/qa/VisualRun.java")
s = p.read_text()
start = s.find('    case 9->{')
end = s.find('    case 14->{', start)
if start < 0 or end < 0:
    raise SystemExit("Effective VisualRun pack-flow anchors missing")

replacement = (
    '    case 9->{shot(c,"07-packs");'
    'org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info('
    '"CARDWORLDS_QA_PACK_FLOW_SKIPPED reason=duel_runtime_scope");'
    'a.navigate("Play");duelDeadline=now+30000;'
    'a.send("pve",a.deckName,"HARD");step=14;}\n'
    '    case 10,11,12,13->{throw new AssertionError('
    '"Deprecated pack-opening path entered full Duel QA: step="+step);}\n'
)
ns = s[:start] + replacement + s[end:]
p.write_text(ns)

check = p.read_text()
if "CARDWORLDS_QA_PACK_FLOW_SKIPPED" not in check:
    raise SystemExit("Effective VisualRun marker missing after patch")
if 'case 9->{shot(c,"07-packs");a.banner="crossroads"' in check:
    raise SystemExit("Legacy pack purchase path survived effective QA patch")
print("CARDWORLDS_EFFECTIVE_QA_DRIVER_PATCHED duel_scope=true")
