package vn.svframe.mythiclibfabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.compat.YamlLite;
import vn.svframe.mythiclibfabric.runtime.DamagePacket;
import vn.svframe.mythiclibfabric.runtime.DamageType;
import vn.svframe.mythiclibfabric.runtime.DefenseFormula;
import vn.svframe.mythiclibfabric.runtime.EventBus;
import vn.svframe.mythiclibfabric.runtime.NativeCombatEffectRegistry;
import vn.svframe.mythiclibfabric.runtime.NativeCombatEvents;
import vn.svframe.mythiclibfabric.runtime.NativeDamageMetadata;
import vn.svframe.mythiclibfabric.runtime.NativeElementRegistry;
import vn.svframe.mythiclibfabric.runtime.StatProviderRegistry;
import vn.svframe.mythiclibfabric.runtime.script.ScriptContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native Fabric implementation of MythicLib 1.7.1 attack effects,
 * mitigation mechanics and elemental damage application.
 */
public final class MythicLibCombatRuntime {
    private static final Logger LOG = Logger.getLogger("MythicLib-Fabric/CombatRuntime");
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MythicLib");
    private static final Pattern PLACEHOLDER = Pattern.compile("<([^<>]+)>");
    private static final NativeElementRegistry ELEMENTS = new NativeElementRegistry();
    private static final NativeCombatEffectRegistry MITIGATIONS = new NativeCombatEffectRegistry(NativeCombatEffectRegistry.Kind.MITIGATION);
    private static final NativeCombatEffectRegistry ON_HIT = new NativeCombatEffectRegistry(NativeCombatEffectRegistry.Kind.ON_HIT);
    private static final EventBus EVENTS = new EventBus();
    private static final Map<UUID, LegacyCooldownMap> COOLDOWNS = new ConcurrentHashMap<>();
    private static volatile boolean skipElementalDamageApplication;

    private MythicLibCombatRuntime() {}

    public static EventBus events() { return EVENTS; }
    public static NativeElementRegistry elements() { return ELEMENTS; }
    public static NativeCombatEffectRegistry mitigations() { return MITIGATIONS; }
    public static NativeCombatEffectRegistry onHitEffects() { return ON_HIT; }

    public static boolean reload() {
        try {
            Map<String,Object> config = yaml(ROOT.resolve("config.yml"));
            skipElementalDamageApplication = bool(config.get("skip_elemental_damage_application"), false);
            ELEMENTS.load(yaml(ROOT.resolve("elements.yml")));
            MITIGATIONS.load(yaml(ROOT.resolve("mitigation_types.yml")));
            ON_HIT.load(yaml(ROOT.resolve("on_hit_effects.yml")));
            return true;
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "Failed to reload MythicLib combat runtime", exception);
            return false;
        }
    }

    public static void clearPlayer(UUID player) {
        if (player != null) COOLDOWNS.remove(player);
    }

    public static String summary() {
        return "elements=" + ELEMENTS.size() + ",mitigation=" + MITIGATIONS.size() + ",onhit=" + ON_HIT.size();
    }

    public static float process(LivingEntity target, DamageSource source, float vanillaDamage,
                                List<DamageType> classifiedTypes, MythicLibDamageSettings settings) {
        if (target == null || source == null || vanillaDamage <= 0.0f) return vanillaDamage;
        DamageType[] types = classifiedTypes == null ? new DamageType[0] : classifiedTypes.toArray(DamageType[]::new);
        NativeDamageMetadata damage = new NativeDamageMetadata(vanillaDamage, types);
        Entity rawAttacker = source.getAttacker();
        LivingEntity attacker = rawAttacker instanceof LivingEntity living ? living : null;

        /*
         * Bukkit 1.7.1 listener order is observable and therefore part of combat
         * parity. ElementalDamage and MitigationModule run at NORMAL (the
         * elemental listener is registered first), LegacyAttackEffects and
         * OnHitModule run at HIGH (legacy listener first), and DamageReduction
         * runs at HIGHEST. Keep the same sequence here instead of grouping
         * attack-side and defense-side work.
         */
        if (attacker != null) applyElementalDamage(attacker, target, damage, settings);

        if (target instanceof ServerPlayerEntity playerTarget) {
            ScriptContext mitigationContext = context(playerTarget.getUuid(), attacker == null ? null : attacker.getUuid(), damage);
            runMitigations(playerTarget, attacker, damage, mitigationContext);
            if (mitigationContext.cancelled()) return 0.0f;
            syncFromContext(damage, mitigationContext);
        }

        if (attacker instanceof ServerPlayerEntity player) {
            applyLegacyAttackEffects(player, target, damage);

            ScriptContext attackContext = context(player.getUuid(), target.getUuid(), damage);
            runOnHitEffects(player, target, damage, attackContext);
            if (attackContext.cancelled()) return 0.0f;
            syncFromContext(damage, attackContext);
        }

        if (target instanceof ServerPlayerEntity) {
            applyDamageReduction(target, attacker, source, damage, settings);
        }

        double value = damage.damage();
        if (!Double.isFinite(value)) return vanillaDamage;
        if (value > 0.0d) MythicLibIndicatorManager.damage(target, damage);
        return (float) Math.max(0.0d, Math.min(Float.MAX_VALUE, value));
    }

    /** Mirrors LegacyAttackEffects (HIGH, registered before OnHitModule). */
    private static void applyLegacyAttackEffects(ServerPlayerEntity attacker, LivingEntity target, NativeDamageMetadata damage) {
        for (DamageType type : DamageType.values()) {
            double offense = StatProviderRegistry.stat(attacker.getUuid(), type.getOffenseStat());
            if (offense != 0.0d) damage.additiveModifier(offense / 100.0d, type);
        }

        // EntityGroup was removed from modern Minecraft. The native 1.21.1
        // equivalent of Bukkit's undead entity group is the vanilla undead
        // entity-type tag, which tracks the same gameplay classification.
        if (target.getType().isIn(EntityTypeTags.UNDEAD)) {
            double undead = StatProviderRegistry.stat(attacker.getUuid(), "UNDEAD_DAMAGE");
            if (undead != 0.0d) damage.additiveModifier(undead / 100.0d);
        }

        double contextual = StatProviderRegistry.stat(attacker.getUuid(),
                target instanceof ServerPlayerEntity ? "PVP_DAMAGE" : "PVE_DAMAGE");
        if (contextual != 0.0d) damage.additiveModifier(contextual / 100.0d);
    }

    /** Mirrors DamageReduction (HIGHEST). */
    private static void applyDamageReduction(LivingEntity target, LivingEntity attacker, DamageSource source,
                                             NativeDamageMetadata damage, MythicLibDamageSettings settings) {
        UUID targetId = target.getUuid();

        applySpecificReduction(damage, StatProviderRegistry.stat(targetId, "DAMAGE_REDUCTION"));

        boolean byEntity = source.getSource() != null || source.getAttacker() != null;
        if (byEntity) {
            applySpecificReduction(damage, StatProviderRegistry.stat(targetId,
                    attacker instanceof ServerPlayerEntity ? "PVP_DAMAGE_REDUCTION" : "PVE_DAMAGE_REDUCTION"));
        }

        String cause = FabricDamageBridge.causeKey(source);
        if ("FIRE".equals(cause) || "FIRE_TICK".equals(cause) || "LAVA".equals(cause) || "MELTING".equals(cause)) {
            applySpecificReduction(damage, StatProviderRegistry.stat(targetId, "FIRE_DAMAGE_REDUCTION"));
        }
        if ("FALL".equals(cause)) {
            applySpecificReduction(damage, StatProviderRegistry.stat(targetId, "FALL_DAMAGE_REDUCTION"));
        }

        for (DamageType type : DamageType.values()) {
            double reduction = StatProviderRegistry.stat(targetId, type.name() + "_DAMAGE_REDUCTION");
            if (reduction != 0.0d)
                damage.multiplicativeModifier(Math.max(0.0d, 1.0d - reduction / 100.0d), type);
        }

        applyNaturalDefense(target, damage, settings);
    }

    private static void applySpecificReduction(NativeDamageMetadata damage, double reductionPercent) {
        if (reductionPercent == 0.0d) return;
        damage.multiplicativeModifier(1.0d - Math.min(reductionPercent / 100.0d, 1.0d));
    }

    private static void runOnHitEffects(ServerPlayerEntity attacker, LivingEntity target, NativeDamageMetadata damage, ScriptContext context) {
        LegacyCooldownMap cooldowns = cooldowns(attacker.getUuid());
        for (NativeCombatEffectRegistry.Effect effect : ON_HIT.values()) {
            if (effect.preScript() != null && !MythicLibFabricMod.castScript(effect.preScript(), context)) continue;
            if (effect.hasCooldown() && cooldowns.isOnCooldown(effect.cooldownPath())) continue;
            Map<String,Double> vars = formulaVariables(effect, context, attacker.getUuid(), damage);
            if (effect.hasRoll() && Math.random() > effect.roll(vars)) continue;
            if (!effect.skipEvent()) {
                NativeCombatEvents.OnHitEffect event = new NativeCombatEvents.OnHitEffect(attacker.getUuid(), target.getUuid(), effect, damage);
                EVENTS.publish(event);
                if (event.cancelled()) continue;
            }
            if (effect.hasCooldown()) cooldowns.apply(effect.cooldownPath(), effect.cooldown(vars));
            MythicLibFabricMod.castScript(effect.onScript(), context);
            if (context.cancelled()) return;
            syncFromContext(damage, context);
            seedContext(context, damage);
        }
    }

    private static void runMitigations(ServerPlayerEntity target, LivingEntity attacker, NativeDamageMetadata damage, ScriptContext context) {
        LegacyCooldownMap cooldowns = cooldowns(target.getUuid());
        for (NativeCombatEffectRegistry.Effect effect : MITIGATIONS.values()) {
            if (effect.preScript() != null && !MythicLibFabricMod.castScript(effect.preScript(), context)) continue;
            if (effect.hasCooldown() && cooldowns.isOnCooldown(effect.cooldownPath())) continue;
            Map<String,Double> vars = formulaVariables(effect, context, target.getUuid(), damage);
            if (effect.hasRoll() && Math.random() > effect.roll(vars)) continue;
            if (!effect.skipEvent()) {
                NativeCombatEvents.DamageMitigation event = new NativeCombatEvents.DamageMitigation(
                        target.getUuid(), attacker == null ? null : attacker.getUuid(), effect, damage);
                EVENTS.publish(event);
                if (event.cancelled()) continue;
            }
            if (effect.hasCooldown()) cooldowns.apply(effect.cooldownPath(), effect.cooldown(vars));
            MythicLibFabricMod.castScript(effect.onScript(), context);
            if (context.cancelled()) return;
            syncFromContext(damage, context);
            seedContext(context, damage);
        }
    }

    private static void applyElementalDamage(LivingEntity attacker, LivingEntity target, NativeDamageMetadata damage,
                                             MythicLibDamageSettings settings) {
        double critChance = StatProviderRegistry.stat(attacker.getUuid(), "CRITICAL_STRIKE_CHANCE") / 100.0d;
        double attackCooldown = attacker instanceof ServerPlayerEntity player
                ? Math.max(0.0d, Math.min(1.0d, player.getAttackCooldownProgress(0.5f)))
                : 1.0d;

        if (damage.hasType(DamageType.WEAPON)) {
            for (NativeElementRegistry.Element element : ELEMENTS.values()) {
                double extra = StatProviderRegistry.stat(attacker.getUuid(), element.id() + "_DAMAGE") * attackCooldown;
                if (extra == 0.0d) continue;
                if (!skipElementalDamageApplication) damage.add(extra, element.id());
                else if (attacker instanceof ServerPlayerEntity) {
                    ScriptContext context = context(attacker.getUuid(), target.getUuid(), damage);
                    context.numbers().put("stat." + element.id().toLowerCase(Locale.ROOT) + "_damage", extra);
                    applyElementScript(element, context, damage, critChance);
                }
            }
        }

        for (NativeElementRegistry.Element element : ELEMENTS.values()) {
            if (!damage.hasElement(element.id())) continue;
            double damagePercent = StatProviderRegistry.stat(attacker.getUuid(), element.id() + "_DAMAGE_PERCENT");
            damage.multiplicativeModifier(1.0d + Math.max(-1.0d, damagePercent / 100.0d), element.id());

            double weakness = StatProviderRegistry.stat(target.getUuid(), element.id() + "_WEAKNESS");
            damage.multiplicativeModifier(1.0d + Math.max(-1.0d, weakness / 100.0d), element.id());

            double elementalDefense = StatProviderRegistry.stat(target.getUuid(), element.id() + "_DEFENSE");
            double defensePercent = StatProviderRegistry.stat(target.getUuid(), element.id() + "_DEFENSE_PERCENT");
            elementalDefense *= 1.0d + Math.max(-1.0d, defensePercent / 100.0d);
            double before = damage.damage(element.id());
            if (before > 0.0d) {
                double after = DefenseFormula.calculateDamage(true, elementalDefense, before, settings.naturalFormula(), settings.elementalFormula());
                damage.multiplicativeModifier(after / before, element.id());
            }

            if (!skipElementalDamageApplication && attacker instanceof ServerPlayerEntity) {
                ScriptContext context = context(attacker.getUuid(), target.getUuid(), damage);
                applyElementScript(element, context, damage, critChance);
                if (!context.cancelled()) syncFromContext(damage, context);
            }
        }
    }

    private static void applyElementScript(NativeElementRegistry.Element element, ScriptContext context,
                                           NativeDamageMetadata damage, double critChance) {
        boolean critical = Math.random() < critChance;
        String script = element.skill(critical);
        MythicLibFabricMod.castScript(script, context);
        if (critical && element.criticalStrike() != null) damage.registerElementalCriticalStrike(element.id());
    }

    private static void applyNaturalDefense(LivingEntity target, NativeDamageMetadata damage, MythicLibDamageSettings settings) {
        double defense = StatProviderRegistry.stat(target.getUuid(), "DEFENSE");
        double before = damage.damage((String) null);
        if (before <= 0.0d || defense == 0.0d) return;
        double after = DefenseFormula.calculateDamage(false, defense, before, settings.naturalFormula(), settings.elementalFormula());
        damage.multiplicativeModifier(after / before, (String) null);
    }

    private static ScriptContext context(UUID caster, UUID target, NativeDamageMetadata damage) {
        ScriptContext context = new ScriptContext(caster, target);
        context.bindDamageBridge(new ScriptContext.DamageBridge() {
            @Override public double total() { return damage.damage(); }
            @Override public double type(String type) {
                try { return damage.damage(DamageType.valueOf(type.toUpperCase(Locale.ROOT))); }
                catch (IllegalArgumentException ignored) { return 0.0d; }
            }
            @Override public double element(String element) { return damage.damage(element); }
            @Override public void setTotal(double amount) {
                double before = damage.damage();
                if (before > 0.0d) damage.multiplicativeModifier(Math.max(0.0d, amount) / before);
                else if (amount > 0.0d) damage.add(amount);
            }
            @Override public void setType(String type, double amount) {
                DamageType parsed;
                try { parsed = DamageType.valueOf(type.toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ignored) { return; }
                double before = damage.damage(parsed);
                if (before > 0.0d) damage.multiplicativeModifier(Math.max(0.0d, amount) / before, parsed);
                else if (amount > 0.0d) damage.add(amount, parsed);
            }
            @Override public void setElement(String element, double amount) {
                double before = damage.damage(element);
                if (before > 0.0d) damage.multiplicativeModifier(Math.max(0.0d, amount) / before, element);
                else if (amount > 0.0d) damage.add(amount, element);
            }
            @Override public void multiplyAll(double coefficient) { damage.multiplicativeModifier(coefficient); }
            @Override public void multiplyType(String type, double coefficient) {
                try { damage.multiplicativeModifier(coefficient, DamageType.valueOf(type.toUpperCase(Locale.ROOT))); }
                catch (IllegalArgumentException ignored) { }
            }
            @Override public void multiplyElement(String element, double coefficient) { damage.multiplicativeModifier(coefficient, element); }
            @Override public void additiveAll(double multiplier) { damage.additiveModifier(multiplier); }
            @Override public void additiveType(String type, double multiplier) {
                try { damage.additiveModifier(multiplier, DamageType.valueOf(type.toUpperCase(Locale.ROOT))); }
                catch (IllegalArgumentException ignored) { }
            }
        });
        seedContext(context, damage);
        return context;
    }

    private static void seedContext(ScriptContext context, NativeDamageMetadata damage) {
        context.uncancel();
        context.damage(damage.damage());
        context.damageTypes().clear();
        context.damageByType().clear();
        for (DamageType type : damage.collectTypes()) {
            context.damageTypes().add(type.name());
            context.damage(type.name(), damage.damage(type));
        }
        context.damageByElement().clear();
        for (Map.Entry<String,Double> entry : damage.elementalDamage().entrySet()) {
            context.elementDamage(entry.getKey(), entry.getValue());
        }
        context.objects().put("attack.weapon_critical", damage.isWeaponCriticalStrike());
        context.objects().put("attack.skill_critical", damage.isSkillCriticalStrike());
    }

    private static void syncFromContext(NativeDamageMetadata damage, ScriptContext context) {
        if (context.cancelled()) return;
        if (context.damageBridge() != null) {
            if (context.damageTypes().stream().anyMatch(name -> name.equals("CRIT_WEAPON") || name.equals("CRIT_UNARMED")))
                damage.registerWeaponCriticalStrike();
            if (context.damageTypes().contains("CRIT_SKILL")) damage.registerSkillCriticalStrike();
            return;
        }
        double beforeTotal = damage.damage();
        double desiredTotal = context.damage();
        Map<DamageType,Double> ratios = new LinkedHashMap<>();
        for (DamageType type : damage.collectTypes()) {
            double before = damage.damage(type);
            double after = context.damage(type.name());
            if (before > 0.0d && Math.abs(after - before) > 1e-9) ratios.put(type, Math.max(0.0d, after / before));
        }

        if (!ratios.isEmpty()) {
            boolean same = true;
            Double ratio = null;
            for (double value : ratios.values()) {
                if (ratio == null) ratio = value;
                else if (Math.abs(value - ratio) > 1e-9) { same = false; break; }
            }
            double globalRatio = beforeTotal > 0.0d ? desiredTotal / beforeTotal : 1.0d;
            if (same && ratio != null && Math.abs(ratio - globalRatio) < 1e-9) {
                damage.multiplicativeModifier(Math.max(0.0d, globalRatio));
            } else {
                for (Map.Entry<DamageType,Double> entry : ratios.entrySet()) damage.multiplicativeModifier(entry.getValue(), entry.getKey());
                double afterTyped = damage.damage();
                if (afterTyped > 0.0d && desiredTotal >= 0.0d && Math.abs(afterTyped - desiredTotal) > 1e-8) {
                    damage.multiplicativeModifier(desiredTotal / afterTyped);
                }
            }
        } else if (beforeTotal > 0.0d && desiredTotal >= 0.0d && Math.abs(desiredTotal - beforeTotal) > 1e-9) {
            damage.multiplicativeModifier(desiredTotal / beforeTotal);
        }

        if (context.damageTypes().stream().anyMatch(name -> name.equals("CRIT_WEAPON") || name.equals("CRIT_UNARMED"))) {
            damage.registerWeaponCriticalStrike();
        }
        if (context.damageTypes().contains("CRIT_SKILL")) damage.registerSkillCriticalStrike();
    }

    private static Map<String,Double> formulaVariables(NativeCombatEffectRegistry.Effect effect, ScriptContext context,
                                                       UUID statSubject, NativeDamageMetadata damage) {
        HashMap<String,Double> vars = new HashMap<>();
        vars.put("attack.damage", damage.damage());
        for (DamageType type : DamageType.values()) vars.put("attack.damage_" + type.name().toLowerCase(Locale.ROOT), damage.damage(type));
        collectStats(effect.cooldownFormula(), statSubject, vars);
        collectStats(effect.rollFormula(), statSubject, vars);
        return vars;
    }

    private static void collectStats(String formula, UUID subject, Map<String,Double> vars) {
        if (formula == null || subject == null) return;
        Matcher matcher = PLACEHOLDER.matcher(formula);
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            if (key.toLowerCase(Locale.ROOT).startsWith("stat.")) {
                String stat = key.substring("stat.".length()).toUpperCase(Locale.ROOT);
                vars.put(key, StatProviderRegistry.stat(subject, stat));
            }
        }
    }

    private static LegacyCooldownMap cooldowns(UUID player) {
        return COOLDOWNS.computeIfAbsent(player, ignored -> new LegacyCooldownMap());
    }

    private static Map<String,Object> yaml(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return Map.of();
        return YamlLite.map(YamlLite.parse(path));
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
