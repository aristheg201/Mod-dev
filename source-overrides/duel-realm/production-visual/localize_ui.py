"""Targeted source overlay: visible labels become keys, stable actions/routes stay unchanged."""
import json,re
from pathlib import Path
here=Path(__file__).resolve().parent
aliases=json.loads((here/'resources/data/svarcade_tcg/ui_translation_aliases.json').read_text())
paths=list(Path('src/main/java/vn/svarcade/tcg/client/screens').glob('*.java'))+[Path('src/main/java/vn/svarcade/tcg/client/CardWorldsScreen.java'),Path('src/main/java/vn/svarcade/tcg/client/animation/PackReveal.java')]
def literal(match):
    prefix,raw=match.groups()
    value=json.loads('"'+raw+'"')
    return prefix+json.dumps(aliases.get(value,value),ensure_ascii=False)
for p in paths:
    if not p.exists():continue
    s=p.read_text()
    s=re.sub(r'((?:u|ui)\.(?:text|fit|paragraph|button|chip)\()"((?:[^"\\]|\\.)*)"',literal,s)
    s=re.sub(r'(a\.field\("[^"\\]*",)"((?:[^"\\]|\\.)*)"',literal,s)
    if p.name=='DuelScreen.java':
        s=s.replace(' + card.name() + (fieldDef', ' + (fieldDef==null?card.name():vn.svarcade.tcg.client.component.CardWorldsLanguage.name(fieldDef)) + (fieldDef')
    if p.name=='CollectionScreen.java':
        s=s.replace('card.name()', 'vn.svarcade.tcg.client.component.CardWorldsLanguage.name(card)').replace('card.text()', 'vn.svarcade.tcg.client.component.CardWorldsLanguage.effect(card)')
        s=s.replace('new Rect(r.x()-320,r.y()+40,308,242)','new Rect(r.x()-430,r.y()+12,418,Math.min(560,r.h()-24))')
    if p.name=='CardWorldsScreen.java':
        s=s.replace('c.name().toLowerCase(Locale.ROOT)', 'vn.svarcade.tcg.client.component.CardWorldsLanguage.name(c).toLowerCase(Locale.ROOT)')
    p.write_text(s)
p=Path('src/main/java/vn/svarcade/tcg/client/component/Ui.java');s=p.read_text()
s=s.replace('return Text.literal(s);','return Text.literal(CardWorldsLanguage.translate(s));')
s=s.replace('public void fit(String s,Rect r,int size,int color){while', 'public void fit(String s,Rect r,int size,int color){s=CardWorldsLanguage.translate(s);while')
s=s.replace('int w=260,x=Math.min((int)mx+12,1260-w),y=Math.max(8,(int)my-70);Rect r=new Rect(x,y,w,76);', 'int w=370,h=Math.min(440,Math.max(90,MinecraftClient.getInstance().textRenderer.wrapLines(styled(tooltip),300).size()*18+24)),x=Math.min((int)mx+12,1260-w),y=Math.max(8,Math.min((int)my-70,700-h));Rect r=new Rect(x,y,w,h);')
p.write_text(s)
p=Path('src/main/java/vn/svarcade/tcg/client/card/CardRenderer.java');s=p.read_text()
s=s.replace('d.name()','vn.svarcade.tcg.client.component.CardWorldsLanguage.name(d)')
s=s.replace('String rulesText=d.text()==null||d.text().isBlank()?(pokemon?"No effect.":""):d.text();','String rulesText=vn.svarcade.tcg.client.component.CardWorldsLanguage.effect(d);')
s=s.replace('d.type().toUpperCase()', 'vn.svarcade.tcg.client.component.CardWorldsLanguage.t("cardworlds.type."+d.type()).toUpperCase(java.util.Locale.ROOT)')
p.write_text(s)
