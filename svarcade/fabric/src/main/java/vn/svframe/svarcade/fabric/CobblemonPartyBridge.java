package vn.svframe.svarcade.fabric;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.systems.loadout.LoadoutAccess;

/** Read-only Cobblemon boundary. Reflection keeps SVArcade loadable when Cobblemon is absent. */
final class CobblemonPartyBridge {
    private static final String COBBLEMON = "com.cobblemon.mod.common.Cobblemon";
    private static final String POKEMON = "com.cobblemon.mod.common.pokemon.Pokemon";

    private final Object cobblemon;
    private final Method storageGetter;
    private final Method partyGetter;
    private final Method uuidGetter, speciesGetter, formGetter, aspectsGetter, typesGetter, levelGetter, moveSetGetter, abilityGetter, heldItemGetter;
    private final Method speciesIdentifierGetter, formIdGetter, moveSetMovesGetter, moveNameGetter, abilityNameGetter, typeNameGetter;

    private CobblemonPartyBridge(Object cobblemon, Method storageGetter, Method partyGetter, Class<?> pokemon) throws ReflectiveOperationException {
        this.cobblemon = cobblemon; this.storageGetter = storageGetter; this.partyGetter = partyGetter;
        uuidGetter = pokemon.getMethod("getUuid"); speciesGetter = pokemon.getMethod("getSpecies"); formGetter = pokemon.getMethod("getForm");
        aspectsGetter = pokemon.getMethod("getAspects"); typesGetter = pokemon.getMethod("getTypes"); levelGetter = pokemon.getMethod("getLevel");
        moveSetGetter = pokemon.getMethod("getMoveSet"); abilityGetter = pokemon.getMethod("getAbility"); heldItemGetter = pokemon.getMethod("heldItem");
        Class<?> species = speciesGetter.getReturnType(), form = formGetter.getReturnType(), moveSet = moveSetGetter.getReturnType(), ability = abilityGetter.getReturnType();
        speciesIdentifierGetter = species.getMethod("getResourceIdentifier");
        formIdGetter = first(form, "showdownId", "getName");
        moveSetMovesGetter = moveSet.getMethod("getMoves");
        Class<?> move = Class.forName("com.cobblemon.mod.common.api.moves.Move", false, pokemon.getClassLoader()); moveNameGetter = move.getMethod("getName");
        abilityNameGetter = ability.getMethod("getName");
        Class<?> type = Class.forName("com.cobblemon.mod.common.api.types.ElementalType", false, pokemon.getClassLoader()); typeNameGetter = type.getMethod("getShowdownId");
    }

    static Optional<CobblemonPartyBridge> discover() {
        try {
            ClassLoader loader = CobblemonPartyBridge.class.getClassLoader(); Class<?> root = Class.forName(COBBLEMON, false, loader);
            Object instance = root.getField("INSTANCE").get(null); Method storage = root.getMethod("getStorage"); Object manager = storage.invoke(instance);
            Method party = Arrays.stream(manager.getClass().getMethods()).filter(method -> method.getName().equals("getParty") && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(ServerPlayerEntity.class)).findFirst().orElseThrow(NoSuchMethodException::new);
            Class<?> pokemon = Class.forName(POKEMON, false, loader); return Optional.of(new CobblemonPartyBridge(instance, storage, party, pokemon));
        } catch (ReflectiveOperationException | LinkageError failure) { return Optional.empty(); }
    }

    List<LoadoutAccess.Snapshot> snapshots(ServerPlayerEntity player, int maximum) {
        Objects.requireNonNull(player); if (maximum < 1 || maximum > 64) throw new IllegalArgumentException("Party snapshot limit");
        try {
            Object manager = storageGetter.invoke(cobblemon); Object party = partyGetter.invoke(manager, player);
            if (!(party instanceof Iterable<?> iterable)) throw new IllegalStateException("Cobblemon party is not iterable");
            List<LoadoutAccess.Snapshot> result = new ArrayList<>();
            for (Object pokemon : iterable) {
                if (result.size() >= maximum) throw new IllegalStateException("Cobblemon party exceeded configured snapshot limit");
                result.add(snapshot(pokemon));
            }
            return List.copyOf(result);
        } catch (InvocationTargetException e) { throw new IllegalStateException("Cobblemon party snapshot failed", e.getCause()); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Cobblemon party API changed", e); }
    }

    private LoadoutAccess.Snapshot snapshot(Object pokemon) throws ReflectiveOperationException {
        UUID uuid = (UUID) uuidGetter.invoke(pokemon); Object species = speciesGetter.invoke(pokemon), form = formGetter.invoke(pokemon);
        Id speciesId = Id.of(speciesIdentifierGetter.invoke(species).toString()); String formId = normalizeRaw(String.valueOf(formIdGetter.invoke(form)));
        Set<Id> aspects = ids((Iterable<?>) aspectsGetter.invoke(pokemon), "aspect/"); Set<Id> types = objectIds((Iterable<?>) typesGetter.invoke(pokemon), typeNameGetter, "");
        int level = ((Number) levelGetter.invoke(pokemon)).intValue(); Object moveSet = moveSetGetter.invoke(pokemon);
        Set<Id> moves = objectIds((Iterable<?>) moveSetMovesGetter.invoke(moveSet), moveNameGetter, ""); Object ability = abilityGetter.invoke(pokemon);
        Id abilityId = externalId("", String.valueOf(abilityNameGetter.invoke(ability))); String heldItem = heldItem(heldItemGetter.invoke(pokemon));
        return new LoadoutAccess.Snapshot("cobblemon:" + uuid, speciesId, formId, aspects, types, level, moves, abilityId, heldItem);
    }

    private static Set<Id> ids(Iterable<?> values, String prefix) {
        Set<Id> result = new LinkedHashSet<>(); for (Object value : values) result.add(externalId(prefix, String.valueOf(value))); return Set.copyOf(result);
    }
    private static Set<Id> objectIds(Iterable<?> values, Method getter, String prefix) throws ReflectiveOperationException {
        Set<Id> result = new LinkedHashSet<>(); for (Object value : values) result.add(externalId(prefix, String.valueOf(getter.invoke(value)))); return Set.copyOf(result);
    }
    private static Id externalId(String prefix, String raw) { return Id.of("cobblemon:" + prefix + normalizeRaw(raw)); }
    private static String normalizeRaw(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        if (value.isBlank() || value.length() > 120) throw new IllegalArgumentException("Invalid Cobblemon identifier: " + raw); return value;
    }
    private static String heldItem(Object raw) {
        if (!(raw instanceof ItemStack stack) || stack.isEmpty()) return ""; return Registries.ITEM.getId(stack.getItem()).toString();
    }
    private static Method first(Class<?> type, String... names) throws NoSuchMethodException {
        for (String name : names) try { return type.getMethod(name); } catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException(type.getName() + " missing " + Arrays.toString(names));
    }
}
