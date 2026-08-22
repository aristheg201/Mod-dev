package vn.svframe.mythiclibfabric.runtime;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import vn.svframe.mythiclibfabric.runtime.skill.*;
import vn.svframe.compat.YamlLite;
import java.util.Map;
import vn.svframe.mythiclibfabric.runtime.script.*;

public final class MythicLibRuntimeSmoke {
    public static void main(String[] args) throws Exception {
        StatMap stats = new StatMap();
        stats.setBase("attack_damage", 100);
        stats.put(StatModifier.permanent("attack_damage", "flat", 20, ModifierOperation.ADD));
        stats.put(StatModifier.permanent("attack_damage", "base-percent", .10, ModifierOperation.MULTIPLY_BASE));
        stats.put(StatModifier.permanent("attack_damage", "total-percent", .25, ModifierOperation.MULTIPLY_TOTAL));
        require(close(stats.value("attack_damage", 0), 162.5), "stat aggregation");

        CombatPipeline combat = new CombatPipeline();
        combat.register(100, (ctx, damage) -> CombatPipeline.Result.allow(scale(damage, 1.5)));
        combat.register(0, (ctx, damage) -> CombatPipeline.Result.allow(scale(damage, .5)));
        DamagePacket packet = new DamagePacket().add(DamageType.PHYSICAL, 20).add(DamageType.SKILL, 10);
        require(close(combat.process(new CombatPipeline.Context("a", "b", false, true), packet).damage().total(), 22.5), "combat pipeline");
        DamagePacket originalPacket = new DamagePacket(100, DamageType.WEAPON, DamageType.PHYSICAL);
        originalPacket.additiveModifier(-.25);
        originalPacket.multiplicativeModifier(.5);
        require(close(originalPacket.getFinalValue(), 37.5), "original damage packet modifiers");
        require(originalPacket.hasType(DamageType.WEAPON) && originalPacket.hasAnyType(java.util.List.of(DamageType.MAGIC, DamageType.PHYSICAL)), "original damage packet type surface");
        require(DamageType.listFromConfig("WEAPON, PHYSICAL").equals(java.util.List.of(DamageType.WEAPON, DamageType.PHYSICAL)), "damage type config parsing");
        require(close(DefenseFormula.calculateDamage(false, 5, 20), 15), "natural defense formula");
        require(close(DefenseFormula.calculateDamage(true, 5, 20), 20d * (1d - 5d / 105d)), "elemental defense formula");

        EventBus bus = new EventBus();
        AtomicInteger hits = new AtomicInteger();
        AutoCloseable closeable = bus.subscribe(Number.class, ignored -> hits.incrementAndGet());
        bus.publish(4);
        closeable.close();
        bus.publish(5);
        require(hits.get() == 1, "event subscription lifecycle");
        ScalingFormula formula = new ScalingFormula(10, 2, 0, 100);
        require(close(formula.evaluate(5), 18), "scaling formula");
        SkillEngine skillEngine = new SkillEngine();
        skillEngine.register(new SkillEngine.Skill() {
            public String id() { return "blink"; }
            public long cooldownMillis(SkillMetadata m) { return 1000; }
            public boolean canCast(SkillMetadata m) { return m.getNumber("mana", 0) >= 5; }
            public SkillEngine.CastResult cast(SkillMetadata m) { return SkillEngine.CastResult.ok(); }
        });
        UUID caster = UUID.randomUUID();
        SkillMetadata sm = new SkillMetadata(caster, null, 3).putNumber("mana", 10);
        require(skillEngine.cast("blink", sm, 100).success(), "skill first cast");
        require(!skillEngine.cast("blink", sm, 200).success(), "skill cooldown");
        require(skillEngine.cast("blink", sm, 1200).success(), "skill cooldown expiry");
        String legacyYaml = "TEST:\n  source: mythicmobs:TEST_WRAP\n  name: Test\n  trigger: TIMER\n  parameters:\n    damage:\n      player:\n        base: 10\n        per-level: 2\n        min: 0\n        max: 100\n      item: 3\n";
        Map<String,Object> ly = YamlLite.map(YamlLite.parse(legacyYaml));
        LegacySkillDefinition ld = LegacySkillDefinition.from("TEST", YamlLite.map(ly.get("TEST")));
        require(close(ld.parameters().get("damage").player().evaluate(5), 18), "legacy skill yaml");
        ExpressionRuntime expr = new ExpressionRuntime();
        require(close(expr.evaluate("(<x>+2)*3", Map.of("x",4d)),18), "expression runtime");
        TriggerRuntime triggers = new TriggerRuntime();
        AtomicInteger triggerHits = new AtomicInteger(); triggers.register("attack",10,c->{triggerHits.incrementAndGet();return true;});
        require(triggers.fire(new TriggerRuntime.Context(caster,"attack",Map.of())) && triggerHits.get()==1, "trigger runtime");
        System.out.println("MYTHICLIB_RECONSTRUCTED_RUNTIME=PASS");
    }

    private static DamagePacket scale(DamagePacket input, double factor) {
        DamagePacket out = new DamagePacket();
        input.parts().forEach((type, amount) -> out.add(type, amount * factor));
        return out;
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-9; }
    private static void require(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
}
