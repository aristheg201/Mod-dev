import vn.svframe.mythicmobsfabric.engine.RotationAwareSkillPlatform;
import vn.svframe.mythicmobsfabric.engine.SkillContext;
import vn.svframe.mythicmobsfabric.engine.SkillDefinition;
import vn.svframe.mythicmobsfabric.engine.SkillPlatform;
import vn.svframe.mythicmobsfabric.engine.SkillRuntime;
import vn.svframe.mythicmobsfabric.engine.Vec3;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ParticleGeometrySmoke {
    public static void main(String[] args) {
        AtomicInteger particles = new AtomicInteger();
        List<Vec3> emitted = new ArrayList<>();
        UUID caster = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        SkillPlatform platform = (SkillPlatform) Proxy.newProxyInstance(
                ParticleGeometrySmoke.class.getClassLoader(),
                new Class<?>[]{SkillPlatform.class, RotationAwareSkillPlatform.class},
                (proxy, method, methodArgs) -> switch (method.getName()) {
                    case "exists", "alive", "living" -> true;
                    case "player" -> false;
                    case "position", "eyePosition" -> {
                        UUID entity = (UUID) methodArgs[0];
                        yield entity.equals(caster)
                                ? new Vec3(-4, 64, 0, "minecraft:overworld")
                                : new Vec3(0, 64, 0, "minecraft:overworld");
                    }
                    case "yaw" -> 90.0f;
                    case "pitch" -> 0.0f;
                    case "velocity" -> new Vec3(0, 0, 0, "minecraft:overworld");
                    case "world" -> "minecraft:overworld";
                    case "entityType" -> "minecraft:player";
                    case "biome" -> "minecraft:plains";
                    case "mythicMobType", "getCustomData" -> "";
                    case "allPlayers", "allLiving", "nearby", "children" -> List.of();
                    case "dayTime" -> 6000L;
                    case "level", "health", "maxHealth", "absorption" -> 20.0d;
                    case "particle" -> {
                        emitted.add((Vec3) methodArgs[0]);
                        particles.addAndGet((Integer) methodArgs[2]);
                        yield true;
                    }
                    case "schedule" -> {
                        ((Runnable) methodArgs[1]).run();
                        yield true;
                    }
                    default -> defaultValue(method.getReturnType());
                });

        SkillRuntime runtime = new SkillRuntime(platform);
        runtime.register(SkillDefinition.from("geometry", Map.of("Skills", List.of(
                "particlering{particle=crit;points=12;radius=1}",
                "particlesphere{particle=crit;points=18;radius=1}",
                "particlebox{particle=crit;amount=24;radius=1}",
                "particletornado{particle=crit;points=20;height=3;radius=1.5;turns=3}",
                "particleatom{particle=crit;points=18;radius=1}",
                "particleequation{particle=crit;equation=0;boundx=1;boundy=1;boundz=1;resolution=1}",
                "particlelineequation{particle=crit;distancebetween=1;equationx=0;equationy=0;equationz=0;maxdistance=256;forwardoffset=1;sideoffset=0.5}"
        ))));

        SkillContext context = new SkillContext(
                "API",
                caster,
                target,
                new Vec3(0, 64, 0, "minecraft:overworld"),
                1.0f,
                Map.of(),
                Map.of());

        if (!runtime.cast("geometry", context)) throw new AssertionError("geometry skill did not cast");
        if (particles.get() != 124) throw new AssertionError("expected 124 emitted particle points, got " + particles.get());

        Vec3 firstLinePoint = emitted.get(emitted.size() - 5);
        assertClose(firstLinePoint.x(), -5.0, "line start x");
        assertClose(firstLinePoint.y(), 64.0, "line start y");
        assertClose(firstLinePoint.z(), -0.5, "line start z");
        System.out.println("MYTHICMOBS_PARTICLE_GEOMETRY_M2_2=PASS particles=" + particles.get());
    }

    private static void assertClose(double actual, double expected, String label) {
        if (Math.abs(actual - expected) > 1.0e-9) throw new AssertionError(label + " expected " + expected + ", got " + actual);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0d;
        if (type == char.class) return '\0';
        if (type == String.class) return "";
        if (java.util.Collection.class.isAssignableFrom(type)) return List.of();
        return null;
    }
}
