import vn.svframe.mythicmobsfabric.engine.SkillContext;
import vn.svframe.mythicmobsfabric.engine.SkillDefinition;
import vn.svframe.mythicmobsfabric.engine.SkillPlatform;
import vn.svframe.mythicmobsfabric.engine.SkillRuntime;
import vn.svframe.mythicmobsfabric.engine.Vec3;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ParticleGeometrySmoke {
    public static void main(String[] args) {
        AtomicInteger particles = new AtomicInteger();
        UUID caster = UUID.randomUUID();

        SkillPlatform platform = (SkillPlatform) Proxy.newProxyInstance(
                ParticleGeometrySmoke.class.getClassLoader(),
                new Class<?>[]{SkillPlatform.class},
                (proxy, method, methodArgs) -> switch (method.getName()) {
                    case "exists", "alive", "living" -> true;
                    case "player" -> false;
                    case "position", "eyePosition", "velocity" -> new Vec3(0, 64, 0, "minecraft:overworld");
                    case "world" -> "minecraft:overworld";
                    case "entityType" -> "minecraft:player";
                    case "biome" -> "minecraft:plains";
                    case "mythicMobType", "getCustomData" -> "";
                    case "allPlayers", "allLiving", "nearby", "children" -> List.of();
                    case "dayTime" -> 6000L;
                    case "level", "health", "maxHealth", "absorption" -> 20.0d;
                    case "particle" -> {
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
                "particleatom{particle=crit;points=18;radius=1}"
        ))));

        SkillContext context = new SkillContext(
                "API",
                caster,
                null,
                new Vec3(0, 64, 0, "minecraft:overworld"),
                1.0f,
                Map.of(),
                Map.of());

        if (!runtime.cast("geometry", context)) {
            throw new AssertionError("geometry skill did not cast");
        }
        if (particles.get() != 92) {
            throw new AssertionError("expected 92 emitted particle points, got " + particles.get());
        }
        System.out.println("MYTHICMOBS_PARTICLE_GEOMETRY_M2_2=PASS particles=" + particles.get());
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
