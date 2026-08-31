package vn.svframe.svquest.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import vn.svframe.svquest.SVQuest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Optional-mod adapter layer. It intentionally has zero compile-time references to Cobblemon/GTS/
 * SkiesShop/SVFrameMMO/SoulBreeding. Missing or changed integrations are isolated and logged instead
 * of taking down the game during Fabric entrypoint initialization.
 */
public final class ReflectionIntegrationBridge {
    private final MinecraftServer server;
    private final QuestEngine engine;
    private final Map<UUID, Integer> learnedSkillCounts = new HashMap<>();
    private final Map<UUID, String> skillBindings = new HashMap<>();
    private final Map<UUID, Object> breedingListeners = new HashMap<>();
    private int tick;

    public ReflectionIntegrationBridge(MinecraftServer server, QuestEngine engine) {
        this.server = server;
        this.engine = engine;
    }

    public void install() {
        safeInstall("Cobblemon", "cobblemon", this::installCobblemon);
        safeInstall("GTS", "gts", this::installGts);
        safeInstall("SkiesShop", "skiesshop", this::installSkiesShop);
        safeInstall("SVFrameMMO", "svframemmo", this::installSvFrameMmo);
        safeInstall("CobbleDollars", "cobbledollars", this::installCobbleDollars);
        ServerTickEvents.END_SERVER_TICK.register(s -> pollPlayerState());
    }

    public void onJoin(ServerPlayerEntity player) {
        if (loaded("cobblemon")) migrateExistingCobblemonPlayer(player);
        snapshotSkills(player, false);
        if (loaded("soulbreeding")) subscribeSoulBreeding(player);
    }

    public void onQuit(ServerPlayerEntity player) {
        learnedSkillCounts.remove(player.getUuid());
        skillBindings.remove(player.getUuid());
        unsubscribeSoulBreeding(player);
    }

    /**
     * Existing players cannot re-run Cobblemon's one-time starter chooser. If they already own any
     * Pokémon in party or PC, satisfy only the starter objective when that objective is active.
     */
    private void migrateExistingCobblemonPlayer(ServerPlayerEntity player) {
        try {
            Class<?> cobblemon = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object instance = cobblemon.getField("INSTANCE").get(null);
            Object storage = cobblemon.getMethod("getStorage").invoke(instance);
            if (storage == null) return;

            Object party = storage.getClass().getMethod("getParty", ServerPlayerEntity.class).invoke(storage, player);
            int occupied = intValue(invoke(party, "occupied"));
            if (occupied > 0) {
                engine.signal(player, "starter");
                return;
            }

            Object pc = storage.getClass().getMethod("getPC", ServerPlayerEntity.class).invoke(storage, player);
            if (pc instanceof Iterable<?> iterable && iterable.iterator().hasNext()) {
                engine.signal(player, "starter");
            }
        } catch (Throwable t) {
            SVQuest.LOGGER.debug("Existing-player starter migration failed safely for {}: {}", player.getName().getString(), t.toString());
        }
    }

    private void installCobblemon() throws Exception {
        String events = "com.cobblemon.mod.common.api.events.CobblemonEvents";
        subscribeObservable(events, "STARTER_CHOSEN", event -> {
            ServerPlayerEntity p = playerGetter(event, "getPlayer");
            if (p != null) engine.signal(p, "starter");
        });
        subscribeObservable(events, "POKEMON_CAPTURED", event -> {
            ServerPlayerEntity p = playerGetter(event, "getPlayer");
            if (p != null) engine.signal(p, "capture");
        });
        subscribeObservable(events, "EVOLUTION_COMPLETE", event -> {
            ServerPlayerEntity p = ownerOfPokemon(invoke(event, "getPokemon"));
            if (p != null) engine.signal(p, "evolve");
        });
        subscribeObservable(events, "LEVEL_UP_EVENT", event -> {
            Object pokemon = invoke(event, "getPokemon");
            ServerPlayerEntity p = ownerOfPokemon(pokemon);
            if (p != null) engine.metric(p, "pokemon_level", intValue(invoke(pokemon, "getLevel")));
        });
        subscribeObservable(events, "HATCH_EGG_POST", event -> {
            ServerPlayerEntity p = playerGetter(event, "getPlayer");
            if (p != null) engine.signal(p, "hatch");
        });
        subscribeObservable(events, "COLLECT_EGG", event -> {
            ServerPlayerEntity p = playerGetter(event, "getPlayer");
            if (p != null) engine.signal(p, "collect_egg");
        });
        subscribeObservable(events, "BATTLE_VICTORY", event -> {
            Object winners = invoke(event, "getWinners");
            if (winners instanceof Iterable<?> iterable) {
                for (Object actor : iterable) forEachActorPlayer(actor, p -> engine.signal(p, "battle_win"));
            }
        });
        subscribeObservable(events, "TERASTALLIZATION", event ->
                forBattlePokemonPlayers(invoke(event, "getPokemon"), p -> engine.signal(p, "tera_use")));
        subscribeObservable(events, "MEGA_EVOLUTION", event ->
                forBattlePokemonPlayers(invoke(event, "getPokemon"), p -> engine.signal(p, "mega_use")));
    }

    private void installGts() throws Exception {
        String events = "org.pokesplash.gts.api.event.GtsEvents";
        subscribeSimpleEvent(events, "ADD", event -> {
            Object source = invoke(event, "getSource");
            if (source instanceof UUID id) withPlayer(id, p -> engine.signal(p, "gts_listing"));
        });
        subscribeSimpleEvent(events, "PURCHASE", event -> {
            Object buyer = invoke(event, "getBuyer");
            if (buyer instanceof UUID id) withPlayer(id, p -> engine.signal(p, "gts_trade"));
        });
    }

    private void installSkiesShop() throws Exception {
        String listenerName = "com.pokeskies.skiesshop.utils.ShopTransactionEvent";
        Class<?> listener = Class.forName(listenerName);
        Object event = listener.getField("EVENT").get(null);
        Object callback = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (proxy, method, args) -> {
            if (method.getName().equals("execute") && args != null && args.length >= 2) {
                try {
                    ServerPlayerEntity player = args[0] instanceof ServerPlayerEntity p ? p : null;
                    Object transaction = args[1];
                    Object type = publicField(transaction, "type");
                    if (player != null && type != null && "BUY".equalsIgnoreCase(type.toString())) engine.signal(player, "shop_purchase");
                } catch (Throwable t) {
                    SVQuest.LOGGER.debug("SkiesShop quest callback failed safely: {}", t.toString());
                }
                return ActionResult.PASS;
            }
            return defaultValue(method.getReturnType());
        });
        registerFabricEvent(event, callback);
    }

    private void installSvFrameMmo() throws Exception {
        String eventName = "vn.svframe.svframemmo.api.event.PlayerLevelChangeEvent";
        registerFabricListener(eventName, "EVENT", eventName + "$Listener", (method, args) -> {
            if (!method.equals("onLevelChange") || args == null || args.length == 0) return;
            Object event = args[0];
            Object main = invoke(event, "isMainLevel");
            if (Boolean.FALSE.equals(main)) return;
            ServerPlayerEntity p = playerGetter(event, "getPlayer");
            if (p != null) engine.metric(p, "trainer_level", intValue(invoke(event, "getNewLevel")));
        });
    }

    private void installCobbleDollars() throws Exception {
        subscribeObservable("fr.harmex.cobbledollars.common.api.event.CobbleDollarsEvents", "COBBLE_DOLLARS_EARNED", event -> {
            Object p = invoke(event, "getPlayer");
            Object amount = invoke(event, "getAmount");
            if (p instanceof ServerPlayerEntity player && amount instanceof BigInteger n && n.signum() > 0) {
                engine.signal(player, "currency_earn", 1);
            }
        });
    }

    private void pollPlayerState() {
        if (++tick % 20 != 0) return;
        if (!loaded("svframemmo")) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try {
                Object data = svFrameData(player);
                if (data == null) continue;
                engine.metric(player, "trainer_level", intValue(invoke(data, "getLevel")));
                snapshotSkills(player, true);
            } catch (Throwable t) {
                SVQuest.LOGGER.debug("SVFrameMMO poll failed safely for {}: {}", player.getName().getString(), t.toString());
            }
        }
    }

    private void snapshotSkills(ServerPlayerEntity player, boolean signalChanges) {
        if (!loaded("svframemmo")) return;
        try {
            Object data = svFrameData(player);
            if (data == null) return;
            Object levels = invoke(data, "getSkillLevels");
            int count = levels instanceof Map<?, ?> map ? map.size() : 0;
            Integer previous = learnedSkillCounts.put(player.getUuid(), count);
            if (signalChanges && previous != null && count > previous) engine.signal(player, "skill_purchase", count - previous);

            Object bindingsObject = invoke(data, "getSkillBindings");
            String bindings = bindingsObject instanceof Map<?, ?> map ? map.toString() : "{}";
            String previousBindings = skillBindings.put(player.getUuid(), bindings);
            if (signalChanges && previousBindings != null && !bindings.equals(previousBindings)) engine.signal(player, "skill_bind");
        } catch (Throwable t) {
            SVQuest.LOGGER.debug("Skill state snapshot failed safely for {}: {}", player.getName().getString(), t.toString());
        }
    }

    private Object svFrameData(ServerPlayerEntity player) throws Exception {
        Class<?> root = Class.forName("vn.svframe.svframemmo.SVFrameMMO");
        Object manager = root.getMethod("playerData").invoke(null);
        return manager.getClass().getMethod("get", ServerPlayerEntity.class).invoke(manager, player);
    }

    private void subscribeSoulBreeding(ServerPlayerEntity player) {
        try {
            Class<?> eventsClass = Class.forName("org.dev.fil.soulbreeding.breeding.BreedingEvents");
            Class<?> listenerClass = Class.forName("org.dev.fil.soulbreeding.breeding.BreedingEvents$Listener");
            Object singleton = eventsClass.getField("INSTANCE").get(null);
            Object listener = Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class<?>[]{listenerClass}, (proxy, method, args) -> {
                int amount = args != null && args.length > 0 && args[0] instanceof Number n ? Math.max(1, n.intValue()) : 1;
                switch (method.getName()) {
                    case "onEggClaimed" -> engine.signal(player, "collect_egg", amount);
                    case "onEggHatched" -> engine.signal(player, "hatch", amount);
                    default -> { }
                }
                return defaultValue(method.getReturnType());
            });
            eventsClass.getMethod("subscribe", UUID.class, listenerClass).invoke(singleton, player.getUuid(), listener);
            breedingListeners.put(player.getUuid(), listener);
        } catch (Throwable t) {
            SVQuest.LOGGER.warn("SoulBreeding integration disabled safely for {}: {}", player.getName().getString(), t.toString());
        }
    }

    private void unsubscribeSoulBreeding(ServerPlayerEntity player) {
        Object listener = breedingListeners.remove(player.getUuid());
        if (listener == null) return;
        try {
            Class<?> eventsClass = Class.forName("org.dev.fil.soulbreeding.breeding.BreedingEvents");
            Class<?> listenerClass = Class.forName("org.dev.fil.soulbreeding.breeding.BreedingEvents$Listener");
            Object singleton = eventsClass.getField("INSTANCE").get(null);
            eventsClass.getMethod("unsubscribe", UUID.class, listenerClass).invoke(singleton, player.getUuid(), listener);
        } catch (Throwable ignored) { }
    }

    private void subscribeObservable(String ownerClass, String fieldName, Consumer<Object> callback) throws Exception {
        Class<?> owner = Class.forName(ownerClass);
        Object observable = owner.getField(fieldName).get(null);
        Method subscribe = observable.getClass().getMethod("subscribe", Consumer.class);
        subscribe.invoke(observable, (Consumer<Object>) event -> safeCallback(ownerClass + "." + fieldName, callback, event));
    }

    private void subscribeSimpleEvent(String ownerClass, String fieldName, Consumer<Object> callback) throws Exception {
        Class<?> owner = Class.forName(ownerClass);
        Object event = owner.getField(fieldName).get(null);
        Method subscribe = event.getClass().getMethod("subscribe", Consumer.class);
        subscribe.invoke(event, (Consumer<Object>) value -> safeCallback(ownerClass + "." + fieldName, callback, value));
    }

    private interface ListenerBody { void call(String method, Object[] args) throws Throwable; }

    private void registerFabricListener(String ownerClass, String eventField, String listenerClassName, ListenerBody body) throws Exception {
        Class<?> owner = Class.forName(ownerClass);
        Class<?> listenerClass = Class.forName(listenerClassName);
        Object fabricEvent = owner.getField(eventField).get(null);
        Object listener = Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class<?>[]{listenerClass}, (proxy, method, args) -> {
            try { body.call(method.getName(), args); }
            catch (Throwable t) { SVQuest.LOGGER.debug("{} callback failed safely: {}", ownerClass, t.toString()); }
            return defaultValue(method.getReturnType());
        });
        registerFabricEvent(fabricEvent, listener);
    }

    private static void registerFabricEvent(Object event, Object listener) throws Exception {
        Method register = null;
        for (Method m : event.getClass().getMethods()) {
            if (m.getName().equals("register") && m.getParameterCount() == 1) { register = m; break; }
        }
        if (register == null) throw new NoSuchMethodException("Fabric Event.register");
        register.invoke(event, listener);
    }

    private void safeInstall(String label, String modId, ThrowingRunnable installer) {
        if (!loaded(modId)) {
            SVQuest.LOGGER.info("SVQuest optional integration: {} not installed", label);
            return;
        }
        try {
            installer.run();
            SVQuest.LOGGER.info("SVQuest optional integration enabled: {}", label);
        } catch (Throwable t) {
            SVQuest.LOGGER.warn("SVQuest optional integration {} disabled safely: {}", label, t.toString());
        }
    }

    private boolean loaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }

    private void withPlayer(UUID id, Consumer<ServerPlayerEntity> action) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
        if (p != null) action.accept(p);
    }

    private void forEachActorPlayer(Object actor, Consumer<ServerPlayerEntity> action) {
        if (actor == null) return;
        Object ids = invoke(actor, "getPlayerUUIDs");
        if (ids instanceof Iterable<?> iterable) for (Object id : iterable) if (id instanceof UUID uuid) withPlayer(uuid, action);
    }

    private void forBattlePokemonPlayers(Object battlePokemon, Consumer<ServerPlayerEntity> action) {
        if (battlePokemon == null) return;
        forEachActorPlayer(invoke(battlePokemon, "getActor"), action);
    }

    private static ServerPlayerEntity ownerOfPokemon(Object pokemon) {
        Object owner = invoke(pokemon, "getOwnerPlayer");
        return owner instanceof ServerPlayerEntity p ? p : null;
    }

    private static ServerPlayerEntity playerGetter(Object event, String getter) {
        Object p = invoke(event, getter);
        return p instanceof ServerPlayerEntity player ? player : null;
    }

    private static Object publicField(Object target, String name) {
        if (target == null) return null;
        try { return target.getClass().getField(name).get(target); }
        catch (Throwable ignored) { return null; }
    }

    private static Object invoke(Object target, String method) {
        if (target == null) return null;
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (Throwable ignored) { return null; }
    }

    private static int intValue(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static void safeCallback(String label, Consumer<Object> callback, Object event) {
        try { callback.accept(event); }
        catch (Throwable t) { SVQuest.LOGGER.debug("{} callback failed safely: {}", label, t.toString()); }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
