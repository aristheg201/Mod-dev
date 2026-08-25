package vn.svframe.mythiclibfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import vn.svframe.compat.YamlLite;
import vn.svframe.mythiclibfabric.runtime.script.ScriptContext;
import vn.svframe.mythiclibfabric.runtime.script.ScriptEngine;
import vn.svframe.mythiclibfabric.runtime.skill.LegacySkillDefinition;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MythicLibFabricMod implements ModInitializer {
    public static final String ID = "mythiclibfabric";
    private static final Logger LOG = Logger.getLogger("MythicLib-Fabric");
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MythicLib");
    private static final Map<String, LegacySkillDefinition> SKILLS = new ConcurrentHashMap<>();
    private static final Map<String, String> SCRIPT_IDS = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Scheduled> SCHEDULED = new ConcurrentLinkedQueue<>();
    private static volatile ScriptEngine scripts = new ScriptEngine(new FabricScriptPlatform());
    private static volatile MythicLibGeneralSettings settings;
    private static volatile int customTriggers;
    private static volatile MinecraftServer server;
    private static volatile long tick;

    @Override public void onInitialize() {
        try { MythicLibDefaultFiles.ensure(); } catch (IOException exception) { throw new IllegalStateException("Could not install MythicLib default configuration", exception); }
        reload();
        FabricDamageBridge.reload();
        MythicLibIndicatorManager.reload();
        MythicLibWorkbenchMod.initialize();
        ServerLifecycleEvents.SERVER_STARTED.register(value -> { server = value; LOG.info("MythicLib Fabric online; " + definitionSummary()); });
        ServerLifecycleEvents.SERVER_STOPPING.register(value -> { MythicLibCastingDelayManager.clear(); MythicLibIndicatorManager.clear(); server = null; SCHEDULED.clear(); });
        ServerTickEvents.END_SERVER_TICK.register(value -> { tick++; runScheduled(); MythicLibCastingDelayManager.tick(value, tick); MythicLibIndicatorManager.tick(tick); });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> MythicLibCommands.register(dispatcher));
    }

    public static MinecraftServer server() { return server; }
    public static long currentTick() { return tick; }
    public static String definitionSummary() { return "skills=" + SKILLS.size() + ",scripts=" + SCRIPT_IDS.size() + ",customTriggers=" + customTriggers; }
    public static MythicLibGeneralSettings settings() { return settings; }
    public static Path configRoot() { return ROOT; }
    public static boolean hasSkill(String id) { return SKILLS.containsKey(norm(id)) || SCRIPT_IDS.containsKey(norm(id)); }
    public static Map<String, LegacySkillDefinition> skillDefinitions() { return Map.copyOf(SKILLS); }
    public static boolean castScript(String id, UUID caster, UUID target, Map<String, ?> parameters) { return castScript(id, context(caster, target, parameters)); }
    public static boolean castScript(String id, ScriptContext context) { if (context == null) return false; String actual = SCRIPT_IDS.get(normScriptId(id)); return actual != null && scripts.cast(actual, context); }
    public static boolean castInline(Object input, ScriptContext context) {
        if (input == null || context == null) return false;
        if (input instanceof String named) return castScript(named, context);
        if (input instanceof List<?> list) { List<String> mechanics = new ArrayList<>(); for (Object value : list) mechanics.add(String.valueOf(value)); return scripts.cast(new ScriptEngine.Definition("inline", false, List.of(), mechanics), context); }
        if (input instanceof Map<?, ?> raw) { @SuppressWarnings("unchecked") Map<String,Object> section=(Map<String,Object>)raw; return scripts.cast(new ScriptEngine.Definition("inline", false, strings(section.get("conditions")), strings(section.get("mechanics"))), context); }
        return false;
    }
    public static boolean castSkill(String id, UUID caster, UUID target, Map<String, ?> parameters) {
        if (caster != null && MythicLibCastingDelayManager.isCasting(caster)) return false;
        LegacySkillDefinition definition = SKILLS.get(norm(id));
        if (definition == null) return castScript(id, caster, target, parameters);
        Map<String,Object> resolved=definition.resolveParameters(parameters); ScriptContext context=context(caster,target,resolved); double delay=number(resolved.get("delay"),0d);
        if (delay>0d) { int ticks=Math.max(1,(int)(delay*20d)); return MythicLibCastingDelayManager.begin(caster,ticks,context,()->castResolved(definition,caster,target,resolved,context)); }
        return castResolved(definition,caster,target,resolved,context);
    }
    private static boolean castResolved(LegacySkillDefinition definition, UUID caster, UUID target, Map<String,?> parameters, ScriptContext context) {
        String source=definition.source()==null?"":definition.source().trim();
        if(source.isEmpty()){String script=SCRIPT_IDS.get(norm(definition.id()));return script!=null&&scripts.cast(script,context);} int colon=source.indexOf(':'); String provider=colon<0?"default":source.substring(0,colon).trim().toLowerCase(Locale.ROOT); String sourceId=colon<0?source:source.substring(colon+1).trim();
        return switch(provider){case "mythicmobs","mythic"->castMythicMobs(sourceId,caster,target,parameters);case "script","mythiclib"->castScript(sourceId,context);case "default"->castDefault(sourceId,definition.id(),context);default->false;};
    }
    public static void schedule(int delayTicks,Runnable task){if(task==null)return;if(delayTicks<=0){MinecraftServer value=server;if(value!=null)value.execute(task);else task.run();return;}SCHEDULED.add(new Scheduled(tick+delayTicks,task));}
    private static boolean castDefault(String sourceId,String skillId,ScriptContext context){String sourceScript=SCRIPT_IDS.get(norm(sourceId));if(sourceScript!=null)return scripts.cast(sourceScript,context);String skillScript=SCRIPT_IDS.get(norm(skillId));return skillScript!=null&&scripts.cast(skillScript,context);}
    private static boolean castMythicMobs(String id,UUID caster,UUID target,Map<String,?> parameters){if(!FabricLoader.getInstance().isModLoaded("mythicmobsfabric"))return false;try{Class<?> type=Class.forName("vn.svframe.mythicmobsfabric.MythicMobsFabricMod");Method method=type.getMethod("castExternal",String.class,UUID.class,UUID.class,Map.class);Object result=method.invoke(null,id,caster,target,parameters);return result instanceof Boolean value&&value;}catch(ReflectiveOperationException e){LOG.log(Level.WARNING,"Could not dispatch MythicMobs skill "+id,e);return false;}}
    private static ScriptContext context(UUID caster,UUID target,Map<String,?> parameters){ScriptContext context=new ScriptContext(caster,target==null?caster:target);if(parameters!=null)for(Map.Entry<String,?> entry:parameters.entrySet()){Object value=entry.getValue();String key=entry.getKey();context.objects().put(key,value);context.objects().put("parameter."+key,value);context.objects().put("modifier."+key,value);if(value instanceof Number number){double numeric=number.doubleValue();context.numbers().put(key,numeric);context.numbers().put("parameter."+key,numeric);context.numbers().put("modifier."+key,numeric);}}return context;}
    public static boolean reloadAll(){boolean ok=reload();ok&=MythicLibStatMod.reload();ok&=FabricDamageBridge.reload();ok&=MythicLibIndicatorManager.reload();MinecraftServer current=server;if(current!=null)MythicLibHealthScale.onReload(current.getPlayerManager().getPlayerList());return ok;}
    private static boolean reload(){try{Map<String,LegacySkillDefinition> nextSkills=new LinkedHashMap<>();Map<String,String> nextScriptIds=new LinkedHashMap<>();ScriptEngine nextScripts=new ScriptEngine(new FabricScriptPlatform());MythicLibGeneralSettings nextSettings=MythicLibGeneralSettings.load(ROOT.resolve("config.yml"));int nextCustomTriggers=MythicLibTriggerRegistry.reload(ROOT.resolve("triggers.yml"));loadSkills(ROOT.resolve("skill"),nextSkills);loadScripts(ROOT.resolve("script"),nextScripts,nextScriptIds);SKILLS.clear();SKILLS.putAll(nextSkills);SCRIPT_IDS.clear();SCRIPT_IDS.putAll(nextScriptIds);scripts=nextScripts;settings=nextSettings;customTriggers=nextCustomTriggers;MinecraftServer currentServer=server;if(currentServer!=null)MythicLibHealthScale.onReload(currentServer.getPlayerManager().getPlayerList());MythicLibIndicatorManager.reload();return true;}catch(Exception e){LOG.log(Level.SEVERE,"Failed to load MythicLib legacy configuration",e);return false;}}
    private static void loadSkills(Path directory,Map<String,LegacySkillDefinition> target)throws IOException{for(Path file:yamlFiles(directory)){Map<String,Object> root=YamlLite.map(YamlLite.parse(file));for(Map.Entry<String,Object> entry:root.entrySet()){if(!(entry.getValue() instanceof Map<?,?> raw))continue;@SuppressWarnings("unchecked")Map<String,Object> section=(Map<String,Object>)raw;target.put(norm(entry.getKey()),LegacySkillDefinition.from(entry.getKey(),section));}}}
    private static void loadScripts(Path directory,ScriptEngine engine,Map<String,String> ids)throws IOException{for(Path file:yamlFiles(directory)){Map<String,Object> root=YamlLite.map(YamlLite.parse(file));for(Map.Entry<String,Object> entry:root.entrySet()){if(!(entry.getValue() instanceof Map<?,?> raw))continue;@SuppressWarnings("unchecked")Map<String,Object> section=(Map<String,Object>)raw;engine.register(new ScriptEngine.Definition(entry.getKey(),bool(section.get("public"),true),strings(section.get("conditions")),strings(section.get("mechanics"))));ids.put(norm(entry.getKey()),entry.getKey());}}}
    private static List<Path> yamlFiles(Path directory)throws IOException{if(!Files.isDirectory(directory))return List.of();try(var stream=Files.walk(directory)){return stream.filter(Files::isRegularFile).filter(path->{String name=path.getFileName().toString().toLowerCase(Locale.ROOT);return name.endsWith(".yml")||name.endsWith(".yaml");}).sorted().toList();}}
    private static List<String> strings(Object value){if(value==null)return List.of();if(value instanceof List<?> list){List<String> out=new ArrayList<>(list.size());for(Object element:list)out.add(String.valueOf(element));return List.copyOf(out);}return List.of(String.valueOf(value));}
    private static boolean bool(Object value,boolean fallback){if(value instanceof Boolean flag)return flag;return value==null?fallback:Boolean.parseBoolean(String.valueOf(value));}
    private static double number(Object value,double fallback){try{return value instanceof Number n?n.doubleValue():value==null?fallback:Double.parseDouble(String.valueOf(value));}catch(NumberFormatException ignored){return fallback;}}
    private static String norm(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);} private static String normScriptId(String value){String normalized=norm(value);return normalized.startsWith("mythiclib:")?normalized.substring("mythiclib:".length()):normalized;}
    private static void runScheduled(){int size=SCHEDULED.size();for(int i=0;i<size;i++){Scheduled scheduled=SCHEDULED.poll();if(scheduled==null)break;if(scheduled.tick<=tick){try{scheduled.task.run();}catch(Throwable throwable){LOG.log(Level.SEVERE,"Scheduled MythicLib task failed",throwable);}}else SCHEDULED.add(scheduled);}}
    private record Scheduled(long tick,Runnable task){}
}
