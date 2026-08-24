import vn.svframe.mythicmobsfabric.engine.MythicMobsParity;
import vn.svframe.mythicmobsfabric.engine.SkillPlatform;
import vn.svframe.mythicmobsfabric.engine.SkillRuntime;
import vn.svframe.mythicmobsfabric.engine.Vec3;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;

public final class MythicMobsParitySurfaceSmoke {
    public static void main(String[] args) {
        SkillPlatform platform = (SkillPlatform) Proxy.newProxyInstance(
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

        SkillRuntime runtime = new SkillRuntime(platform);
        int mechanics = runtime.mechanicCount();
        int conditions = runtime.conditionCount();
        int targeters = runtime.targeterCount();
        if (mechanics < 157 || conditions < 93 || targeters < 72) {
            throw new AssertionError("M2.4 parity registry regressed: " + mechanics + "/" + conditions + "/" + targeters);
        }
        System.out.println("MYTHICMOBS_PARITY_M2_4=PASS mechanics=" + mechanics + " conditions=" + conditions + " targeters=" + targeters);
    }
}
