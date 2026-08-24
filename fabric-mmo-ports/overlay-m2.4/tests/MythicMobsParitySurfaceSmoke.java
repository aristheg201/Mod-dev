import vn.svframe.mythicmobsfabric.engine.SkillPlatform;
import vn.svframe.mythicmobsfabric.engine.SkillRuntime;
import vn.svframe.mythicmobsfabric.engine.Vec3;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class MythicMobsParitySurfaceSmoke {
    public static void main(String[] args) throws Exception {
        SkillPlatform platform = (SkillPlatform) ProxySupport.platform();

        SkillRuntime runtime = new SkillRuntime(platform);
        int mechanics = runtime.mechanicCount();
        int conditions = runtime.conditionCount();
        int targeters = runtime.targeterCount();
        if (mechanics < 157 || conditions < 93 || targeters < 72) {
            throw new AssertionError("M2.4 parity registry regressed: " + mechanics + "/" + conditions + "/" + targeters);
        }

        Map<String, ?> mechanicMap = registry(runtime, "mechanics");
        Map<String, ?> targeterMap = registry(runtime, "targeters");
        require(mechanicMap, "cmd");
        require(mechanicMap, "effect:particlebox");
        require(mechanicMap, "endprojectile");
        require(mechanicMap, "tracklocation");
        require(mechanicMap, "water");
        require(targeterMap, "eno");
        require(targeterMap, "everyone");
        require(targeterMap, "eirr");
        require(targeterMap, "rao");
        require(targeterMap, "t");

        // Keep the historical M2.4 minimum marker consumed by the CI workflow.
        System.out.println("MYTHICMOBS_PARITY_M2_4=PASS mechanics=157 conditions=93 targeters=72");
        System.out.println("MYTHICMOBS_CORE_ALIAS_PARITY=PASS mechanics=" + mechanics + " conditions=" + conditions + " targeters=" + targeters);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> registry(SkillRuntime runtime, String name) throws Exception {
        Field field = SkillRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<String, ?>) field.get(runtime);
    }

    private static void require(Map<String, ?> registry, String id) {
        if (!registry.containsKey(id)) throw new AssertionError("Missing original MythicMobs alias: " + id);
    }

    private static final class ProxySupport {
        static Object platform() {
            return java.lang.reflect.Proxy.newProxyInstance(
                    SkillPlatform.class.getClassLoader(),
                    new Class<?>[]{SkillPlatform.class},
                    (proxy, method, values) -> {
                        Class<?> type = method.getReturnType();
                        if (type == boolean.class) return false;
                        if (type == int.class) return 0;
                        if (type == long.class) return 0L;
                        if (type == float.class) return 0.0f;
                        if (type == double.class) return 0.0d;
                        if (Collection.class.isAssignableFrom(type)) return List.of();
                        if (type == String.class) return "";
                        if (type == Vec3.class) return new Vec3(0, 0, 0, "minecraft:overworld");
                        return null;
                    });
        }
    }
}
