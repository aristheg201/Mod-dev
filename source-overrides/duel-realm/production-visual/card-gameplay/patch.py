from pathlib import Path
import shutil,json
here=Path(__file__).resolve().parent
base=Path('src/main')
for p in (here/'test').glob('*.java'):
 dst=Path('src/test/java/vn/svarcade/tcg/duel')/p.name;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dst)
# Labels describe the executable rules beneath them; no generated recipe filler.
p=base/'java/vn/svarcade/tcg/client/component/CardWorldsLanguage.java'
s=p.read_text()
s=s.replace('String conditions=EffectSpec.list(spec.conditions()).isEmpty()', 'String identity=vn.svarcade.tcg.data.CardGameplayEffects.name(card);\n            String conditions=EffectSpec.list(spec.conditions()).isEmpty()')
s=s.replace('lines.add((effect.speed()>1?', 'lines.add((identity.isBlank()?"":identity+": ")+(effect.speed()>1?')
s=s.replace('t("cardworlds.cost."+c.type().toLowerCase(Locale.ROOT),c.amount())+(c.counter()==null?"":" "+t("cardworlds.counter."+c.counter()))', 'costText(c)')
s=s.replace('t("cardworlds.cost."+cost.type().toLowerCase(Locale.ROOT),cost.amount())+(cost.counter()==null?"":" "+t("cardworlds.counter."+cost.counter()))', 'costText(cost)')
# Stage conditions were omitted by the old renderer: e.g. Hydro Cannon must select Defense Position.
s=s.replace('lines.add((e.speed()>1?', 'String stageConditions=EffectSpec.list(spec.conditions()).isEmpty()?"":String.join("; ",spec.conditions().stream().map(CardWorldsLanguage::condition).toList())+"; ";\n            lines.add(stage.id().replace(\'_\',\' \')+": "+(e.speed()>1?')
s=s.replace('+intro+": "+String.join("; ",costs)', '+intro+": "+stageConditions+String.join("; ",costs)')
anchor='    private static String buildEffect(Catalog.Card card) {'
assert anchor in s
s=s.replace(anchor,'''    private static String costText(EffectSpec.Cost cost) {
        String result=t("cardworlds.cost."+cost.type().toLowerCase(Locale.ROOT),cost.amount());
        if(cost.counter()!=null)result+=" "+t("cardworlds.counter."+cost.counter());
        if(cost.filter()!=null){String restriction=filter(cost.filter());if(!restriction.isBlank())result+=" ("+restriction+")";}
        return result;
    }
'''+anchor)
p.write_text(s)
# Remove the reported machine-generated deck footer and the obsolete disabled NPC payout claim.
p=base/'java/vn/svarcade/tcg/client/screens/DeckBuilderScreen.java';s=p.read_text()
old='"cardworlds.ui.catalog_e8ad6"+a.state.total()+" cards  ·  "+templates.size()+" generated deck templates"'
assert old in s;s=s.replace(old,'CardWorldsLanguage.t("cardworlds.ui.deck_catalog_summary",a.state.total(),templates.size())');p.write_text(s)
p=base/'java/vn/svarcade/tcg/client/screens/WorldScreen.java';s=p.read_text()
old=r'Pewter Challenger\nWin Onix and 100 HunterCoin.\nUp to 5 rewards per day.'
assert old in s;s=s.replace(old,'cardworlds.world.practice_rewards');p.write_text(s)
translations={
 'en_us':{'cardworlds.ui.deck_catalog_summary':'%s cards · %s deck templates','cardworlds.world.practice_rewards':'Easy: practice, no currency reward.\nNormal: win 15 Beast Coin.\nHard: win 20 / loss 5 Beast Coin.','cardworlds.counter.shell':'Shell','cardworlds.counter.splash':'Splash'},
 'vi_vn':{'cardworlds.ui.deck_catalog_summary':'%s lá bài · %s bộ bài mẫu','cardworlds.world.practice_rewards':'Dễ: luyện tập, không nhận tiền.\nThường: thắng nhận 15 Beast Coin.\nKhó: thắng nhận 20 / thua nhận 5 Beast Coin.','cardworlds.counter.shell':'Vỏ giáp','cardworlds.counter.splash':'Tung tóe'},
}
for lang,additions in translations.items():
 p=base/f'resources/assets/svarcade_tcg/lang/{lang}.json';j=json.loads(p.read_text());j.update(additions)
 for k in ['cardworlds.ui.generated_deck_templates','cardworlds.ui.pewter_challenger_win_onix_and_100_huntercoin_up_to_5_rewards_per_day']:j.pop(k,None)
 p.write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n')
p=base/'resources/data/svarcade_tcg/ui_translation_aliases.json';j=json.loads(p.read_text())
for k in [' generated deck templates','Pewter Challenger\nWin Onix and 100 HunterCoin.\nUp to 5 rewards per day.']:j.pop(k,None)
p.write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n')
# Existing signature test now exercises Charmander's actual low-LP Blaze ability.
p=Path('src/test/java/vn/svarcade/tcg/duel/IntegrationEffectsTest.java');s=p.read_text()
a=s.index('    @Test void signatureAdditionalAbilityUsesRealCostsAndIndependentLimit()');b=s.index('    @Test void allTwentyFourSpecialPrimaryGraphsExecuteWithPaidCounters()',a)
s=s[:a]+'''    @Test void blazeRequiresItsConditionAndRetainsItsOwnLimit(){
        var d=duel(catalog);var p=d.qaAdd(0,"charmander",Duel.Zone.FIELD);
        long revision=d.revision();assertThrows(IllegalArgumentException.class,()->act(d,0,"activate_stage",p.token,"blaze|"));assertEquals(revision,d.revision());
        p.grantedEffect=effect("{\\"operation\\":\\"composite\\",\\"speed\\":1,\\"target\\":\\"none\\",\\"phases\\":[\\"MAIN1\\"],\\"spec\\":{\\"triggers\\":[\\"ON_ACTIVATE\\"],\\"operations\\":[{\\"type\\":\\"DAMAGE_LP\\",\\"amount\\":5500,\\"target\\":\\"SELF\\"}]}}");
        act(d,0,"activate",p.token,"");resolve(d);open(d);p.grantedEffect=null;
        act(d,0,"activate_stage",p.token,"blaze|");resolve(d);open(d);
        assertEquals(2500,d.view(0).life().getFirst());assertTrue(p.boost>=250);
        assertThrows(IllegalArgumentException.class,()->act(d,0,"activate_stage",p.token,"blaze|"));
        assertThrows(IllegalArgumentException.class,()->act(d,0,"activate_stage",p.token,"invented|"));
    }
'''+s[b:]
s=s.replace("allCardsHaveDistinctExecutableGraphsAndChoreography","allCardsHaveDistinctExecutableGameplayAndValidPresentation")
p.write_text(s)
print('CARDWORLDS_GAMEPLAY_PATCH_READY exact_identity_lookup=true ordinal_gameplay=false')

# A queued end-of-turn event belongs to its captured turn, even after nextPhase
# advances the field. Apply this gameplay correctness fix to both engine versions
# so optimization determinism compares the same intended delayed-effect rule.
for p in [base/'java/vn/svarcade/tcg/duel/Duel.java',Path('src/test/java/vn/svarcade/tcg/duel/BaselineDuel.java')]:
 s=p.read_text()
 old='public record Event(long sequence, String kind, String card, int owner, int controller, Zone from, Zone to, Cause cause, String source, int chainLink) {}'
 assert old in s
 s=s.replace(old,'public record Event(long sequence, String kind, String card, int owner, int controller, Zone from, Zone to, Cause cause, String source, int chainLink, int sourceTurn) { public Event(long sequence,String kind,String card,int owner,int controller,Zone from,Zone to,Cause cause,String source,int chainLink){this(sequence,kind,card,owner,controller,from,to,cause,source,chainLink,-1);} }')
 old='Cause.SYSTEM,targetPiece==null?(kind.contains("TURN")||kind.contains("PHASE")?phase.name():""):targetPiece.token,chain.size());'
 assert old in s;s=s.replace(old,old.replace('chain.size());','chain.size(),turn);'))
 old='if(pending.expires()<turn){it.remove();continue;}'
 assert old in s;s=s.replace(old,'if(pending.expires()<(event.sourceTurn()<0?turn:event.sourceTurn())){it.remove();continue;}')
 p.write_text(s)

# The real three-link trap scenario pays the newly authored discard costs.
p=base/'java/vn/svarcade/tcg/duel/Duel.java';s=p.read_text();anchor='        Piece counter=qaAdd(actor,"counter_seal",Zone.SUPPORT);'
assert anchor in s;s=s.replace(anchor,'        qaAdd(actor,"potion",Zone.HAND);qaAdd(1-actor,"potion",Zone.HAND);\n'+anchor);p.write_text(s)
p=Path('src/test/java/vn/svarcade/tcg/EngineTest.java');s=p.read_text()
start=s.index('    @Test void qaSpellTrapScenarioRunsThreeLinkChainAndResolves()');end=s.index('    @Test',start+10) if '    @Test' in s[start+10:] else len(s)
s=s[:start]+s[start:end].replace('assertEquals(List.of(7500,7200),d.view(0).life());','assertEquals(List.of(7800,7400),d.view(0).life());')+s[end:]
s=s.replace('assertTrue(d.view(0).cards().stream().filter(c->c.name().equals("Counter Seal")||c.name().equals("Mirror Barrier")).allMatch(c->c.zone()==Duel.Zone.DISCARD));','assertTrue(d.view(0).cards().stream().filter(c->c.name().equals("Counter Seal")).allMatch(c->c.zone()==Duel.Zone.DISCARD));assertTrue(d.view(0).cards().stream().filter(c->c.name().equals("Mirror Barrier")).allMatch(c->c.zone()==Duel.Zone.BANISHED));')
p.write_text(s)
# Names use the real move translations. The body always describes executable data.
p=base/'java/vn/svarcade/tcg/client/component/CardWorldsLanguage.java';s=p.read_text()
s=s.replace('String identity=vn.svarcade.tcg.data.CardGameplayEffects.name(card);','String identity=identityName(card);')
s=s.replace("stage.id().replace('_',' ')+\": \"",'stageName(stage.id())+": "')
anchor='    private static String costText(EffectSpec.Cost cost) {'
assert anchor in s
s=s.replace(anchor,'''    private static String identityName(Catalog.Card card) {
        var d=vn.svarcade.tcg.data.CardGameplayEffects.definition(card);if(d==null)return "";
        if(d.moves()!=null&&!d.moves().isEmpty())return String.join(" / ",d.moves().stream().map(id->{String key="cobblemon.move."+id;return I18n.hasTranslation(key)?t(key):id;}).toList());
        String key="cardworlds.identity."+vn.svarcade.tcg.data.CardGameplayEffects.key(card).replaceAll("[^a-z0-9]","_")+".name";
        return I18n.hasTranslation(key)?t(key):d.name();
    }
    private static String stageName(String id) {
        String key="cardworlds.stage."+id;if(I18n.hasTranslation(key))return t(key);
        key="cobblemon.move."+id.replace("_","");return I18n.hasTranslation(key)?t(key):id.replace('_',' ');
    }
'''+anchor)
anchor='    private static String operation(EffectSpec.Operation op) {'
assert anchor in s
s=s.replace(anchor,anchor+'''
        if(op.type().equals("IF")){
            String body=String.join("; "+t("cardworlds.effect.then")+" ",EffectSpec.list(op.children()).stream().map(CardWorldsLanguage::operation).toList());
            String result=t("cardworlds.effect.if",condition(op.condition()),body);
            if(!EffectSpec.list(op.otherwise()).isEmpty())result+="; "+t("cardworlds.effect.else")+": "+String.join("; ",op.otherwise().stream().map(CardWorldsLanguage::operation).toList());
            return result;
        }
''')
old='return t("cardworlds.condition."+condition.type().toLowerCase(Locale.ROOT),value,target(condition.target()));'
assert old in s
s=s.replace(old,'return t("cardworlds.condition."+condition.type().toLowerCase(Locale.ROOT),value,target(condition.target()))+(condition.type().startsWith("COUNTER_")?" ["+t("cardworlds.counter."+condition.value())+"]":"");')
p.write_text(s)
vi_names={
 'charmander':'Tàn lửa','charmeleon':'Vuốt thiêu đốt','charizard':'Đà lửa','squirtle':'Thu mình','wartortle':'Quật đuôi','blastoise':'Pháo đài mai rùa','bulbasaur':'Vườn ươm','ivysaur':'Dây leo trói buộc','venusaur':'Vườn hút sinh lực','pikachu':'Tích điện','gastly':'Màn đêm','haunter':'Ăn giấc mơ','gengar':'Thoát thân nguyền rủa','eevee':'Dẫn lối tiến hóa','onix':'Trói đá','lapras':'Khúc ca hồi phục','cloyster':'Phá vỏ','gyarados':'Cuồng nộ','feraligatr':'Hàm nghiền','swampert':'Bờ bùn','empoleon':'Vệ binh hoàng đế','greninja':'Phi tiêu nước','inteleon':'Bắn tỉa','quagsire':'Phớt lờ','milotic':'Vảy hồi phục','suicune':'Sương thanh tẩy','kyogre':'Thủy triều khởi nguyên','ditto':'Biến hình','smeargle':'Phác họa','wobbuffet':'Phản đòn kiên nhẫn','magikarp':'Cú nhảy bất ngờ','unown':'Tập hợp cổ tự','mega_charizard':'Bổ nhào địa ngục','ancient_mew':'Ký ức cổ đại','shadow_lugia':'Bão bóng tối','arceus_defense':'Thánh địa','arceus_judgement':'Phán quyết','ultimate_arceus':'Phán quyết cuối cùng','training_ground':'Đội hình luyện tập','mirror_barrier':'Kết giới gương','counter_seal':'Ấn phản chế','palkia':'Cắt không gian','dialga':'Tiếng gầm thời gian','giratina':'Cổng méo mó','mewtwo':'Đòn tâm linh','rayquaza':'Thăng long',
}
stage_names={
 'blaze':('Blaze','Bùng cháy'),'hydro_cannon':('Hydro Cannon','Thần Pháo Nước'),'regrowth':('Regrowth','Tái sinh'),'thunderbolt':('Thunderbolt','Sấm Sét'),'haunt':('Haunt','Ám ảnh'),'substitute':('Substitute','Thế thân'),'shadow_return':('Shadow Return','Bóng tối trở về'),'destiny_bond':('Destiny Bond','Đồng mệnh'),
}
definitions=json.loads((base/'resources/data/svarcade_tcg/pokemon_effects.json').read_text())['definitions']
for lang in ('en_us','vi_vn'):
 p=base/f'resources/assets/svarcade_tcg/lang/{lang}.json';j=json.loads(p.read_text());j['cardworlds.effect.if']='If %s, %s' if lang=='en_us' else 'Nếu %s: %s';j['cardworlds.counter.poison']='Poison' if lang=='en_us' else 'Độc'
 for key,d in definitions.items():
  if d['authorship']!='explicit card design':continue
  name=d['name'] if lang=='en_us' else ('Ấn cổ tự '+key.split('character-')[-1].upper() if key.startswith('unown|character-') else vi_names.get(key,d['name']))
  import re
  j['cardworlds.identity.'+re.sub('[^a-z0-9]','_',key)+'.name']=name
 for id,names in stage_names.items():j['cardworlds.stage.'+id]=names[0 if lang=='en_us' else 1]
 p.write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n')
# Retain every presentation shape; obsolete ordinal gameplay vocabulary is unused.
p=base/'resources/data/svarcade_tcg/identity_recipes.json';j=json.loads(p.read_text());p.write_text(json.dumps({'shapes':j['shapes']},indent=2)+'\n')
