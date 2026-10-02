from pathlib import Path
import re
base=Path('src/main/java/vn/svarcade/tcg');p=base/'client/CardWorldsScreen.java';s=p.read_text()
s=s.replace('public TcgMod.Snapshot state;', 'private final vn.svarcade.tcg.performance.CollectionCache collectionCache=new vn.svarcade.tcg.performance.CollectionCache();\n    public TcgMod.Snapshot state;')
a=s.index('    public List<Catalog.Card> cards()');b=s.index('    public int count(',a)
s=s[:a]+'''    public List<Catalog.Card> cards(){return collectionCache.get(state.definitions(),state.counts(),favorites,List.of(fields.getOrDefault("search",""),category,rarity,set,ownership),net.minecraft.client.MinecraftClient.getInstance().options.language,vn.svarcade.tcg.client.component.CardWorldsLanguage::name);}
    public List<String> rarityOptions(){return vn.svarcade.tcg.performance.CatalogIndex.of(state.definitions()).rarities;}
    public List<String> setOptions(){return vn.svarcade.tcg.performance.CatalogIndex.of(state.definitions()).sets;}
'''+s[b:]
# Preserve collection identity across dynamic snapshots with unchanged counts.
s=s.replace('state=next;if(starter)', 'if(state.counts().equals(next.counts()))next=next.withCounts(state.counts());state=next;if(starter)')
p.write_text(s)
p=base/'client/screens/CollectionScreen.java';s=p.read_text()
s=re.sub(r'java.util.stream.Stream.concat\(java.util.stream.Stream.of\("All"\),a.state.definitions\(\).values\(\).stream\(\).map\(c->c.rarity\(\)\).distinct\(\).sorted\(\)\).toList\(\)', 'a.rarityOptions()',s)
s=re.sub(r'java.util.stream.Stream.concat\(java.util.stream.Stream.of\("All"\),a.state.definitions\(\).values\(\).stream\(\).map\(c->c.set\(\)\).distinct\(\).sorted\(\)\).toList\(\)', 'a.setOptions()',s)
s=s.replace('for(int i=0;i<cards.size();i++)','for(int i=vn.svarcade.tcg.performance.CollectionCache.first(region.offset(),ch+gap,cols),end=vn.svarcade.tcg.performance.CollectionCache.last(region.offset(),grid.h(),ch+gap,cols,cards.size());i<end;i++)')
p.write_text(s)
p=base/'client/screens/DeckBuilderScreen.java';s=p.read_text();s=re.sub(r'for\(int i=0;i<(\w+)\.size\(\);i\+\+\)',lambda m:m.group(0),s);p.write_text(s)
# Existing model preparation cache keeps GPU operations on render thread; bound it with access-order eviction.
p=base/'client/render/PokemonModels.java';s=p.read_text()
s=re.sub(r'new HashMap<>\(\)', 'new java.util.LinkedHashMap<>(64,0.75f,true){@Override protected boolean removeEldestEntry(java.util.Map.Entry eldest){return size()>512;}}',s)
p.write_text(s)
# Card rule preparation is cached by immutable definition identity + language; resource reload clears it.
p=base/'client/component/CardWorldsLanguage.java';s=p.read_text()
s=s.replace('private static final Map<String,String> NAMES=new HashMap<>();','''private static final Map<String,String> NAMES=new LinkedHashMap<>(128,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,String> e){return size()>2048;}};
    private record RuleKey(Catalog.Card card,String language){public int hashCode(){return 31*System.identityHashCode(card)+language.hashCode();}public boolean equals(Object o){return o instanceof RuleKey k&&k.card==card&&k.language.equals(language);}}
    private static final Map<RuleKey,String> RULES=new LinkedHashMap<>(128,.75f,true){protected boolean removeEldestEntry(Map.Entry<RuleKey,String> e){return size()>512;}};''')
s=s.replace('NAMES.clear();','NAMES.clear();RULES.clear();')
s=s.replace('public static String effect(Catalog.Card card) {','public static String effect(Catalog.Card card){return RULES.computeIfAbsent(new RuleKey(card,language()),k->buildEffect(card));}\n    private static String buildEffect(Catalog.Card card) {')
p.write_text(s)
p=base/'client/render/PokemonModels.java';s=p.read_text().replace('CACHE = new LinkedHashMap<>();','CACHE = new LinkedHashMap<>(64,.75f,true);');p.write_text(s)
p=base/'client/CardWorldsScreen.java';s=p.read_text();s=s.replace('private final vn.svarcade.tcg.performance.CollectionCache collectionCache=', 'private final vn.svarcade.tcg.performance.CollectionCache deckPoolCache=new vn.svarcade.tcg.performance.CollectionCache();\n    private final vn.svarcade.tcg.performance.CollectionCache collectionCache=');s=s.replace('    public List<String> rarityOptions()', '    public List<Catalog.Card> deckCards(){return deckPoolCache.get(state.definitions(),state.counts(),Set.of(),List.of(fields.getOrDefault("search",""),"all","All","All","Owned"),CardWorldsLanguage.language(),CardWorldsLanguage::name);}\n    public List<String> rarityOptions()');s=s.replace('Files.write(FabricLoader.getInstance().getConfigDir().resolve("cardworlds-favorites.txt"),favorites);','TcgClient.saveFavorites(FabricLoader.getInstance().getConfigDir().resolve("cardworlds-favorites.txt"),List.copyOf(favorites));');p.write_text(s)
p=base/'client/screens/DeckBuilderScreen.java';s=p.read_text();a=s.index('  var cards=');b=s.index('\n',a);s=s[:a]+'  var cards=a.deckCards();'+s[b:];s=s.replace('for(int i=0;i<cards.size();i++)','for(int i=vn.svarcade.tcg.performance.CollectionCache.first(poolScroll.offset(),170,3),end=vn.svarcade.tcg.performance.CollectionCache.last(poolScroll.offset(),cardPool.h(),170,3,cards.size());i<end;i++)');p.write_text(s)
# Same-language resource reloads also invalidate filtered names/rules without scanning every frame.
p=base/'client/component/CardWorldsLanguage.java';s=p.read_text().replace('private static final Map<String,String> NAMES=', 'private static long presentationRevision;public static long revision(){return presentationRevision;}\n    private static final Map<String,String> NAMES=').replace('NAMES.clear();RULES.clear();','presentationRevision++;NAMES.clear();RULES.clear();');p.write_text(s)
p=base/'performance/CollectionCache.java';s=p.read_text();s=s.replace('private long rebuilds;','private long rebuilds,presentationRevision;')
s=s.replace('Function<Catalog.Card,String> name){if(', 'Function<Catalog.Card,String> name){return get(defs,owned,fav,filters,lang,0,name);}\n public List<Catalog.Card> get(Map<String,Catalog.Card> defs,Map<String,Integer> owned,Set<String> fav,List<String> filters,String lang,long revision,Function<Catalog.Card,String> name){if(presentationRevision==revision&&')
s=s.replace('language=lang;String search=', 'language=lang;presentationRevision=revision;String search=');p.write_text(s)
p=base/'client/CardWorldsScreen.java';s=p.read_text().replace('net.minecraft.client.MinecraftClient.getInstance().options.language,vn.svarcade.tcg.client.component.CardWorldsLanguage::name','CardWorldsLanguage.language(),CardWorldsLanguage.revision(),vn.svarcade.tcg.client.component.CardWorldsLanguage::name').replace('CardWorldsLanguage.language(),CardWorldsLanguage::name','CardWorldsLanguage.language(),CardWorldsLanguage.revision(),CardWorldsLanguage::name').replace('if(state.counts().equals(next.counts()))','if(state.counts()!=next.counts()&&state.counts().equals(next.counts()))');p.write_text(s)
for filename,box,scroll,stride,cols in [('CollectionScreen','grid','region','ch+gap','cols'),('DeckBuilderScreen','cardPool','poolScroll','170','3')]:
 p=base/'client/screens'/f'{filename}.java';s=p.read_text();s=s.replace(f'CollectionCache.first({scroll}.offset(),{stride},{cols})',f'CollectionCache.first(Math.max(0,{scroll}.offset()+u.clipped({box}).y()-{box}.y()),{stride},{cols})').replace(f'CollectionCache.last({scroll}.offset(),{box}.h(),{stride},{cols},cards.size())',f'CollectionCache.last(Math.max(0,{scroll}.offset()+u.clipped({box}).y()-{box}.y()),u.clipped({box}).h(),{stride},{cols},cards.size())');p.write_text(s)
# Iterate only visible booster rows; every pack remains reachable by scrolling.
p=base/'client/screens/PacksScreen.java';s=p.read_text().replace('for(int i=0;i<a.state.banners().size();i++){','for(int i=vn.svarcade.tcg.performance.CollectionCache.first(Math.max(0,listScroll.offset()+u.clipped(list).y()-list.y()),112,1),end=vn.svarcade.tcg.performance.CollectionCache.last(Math.max(0,listScroll.offset()+u.clipped(list).y()-list.y()),u.clipped(list).h(),112,1,a.state.banners().size());i<end;i++){');p.write_text(s)
