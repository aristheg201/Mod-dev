package vn.svframe.mythicmobsfabric.engine;

import java.util.UUID;

/** Optional platform capability used by particle mechanics that depend on caster facing. */
public interface RotationAwareSkillPlatform {
    float yaw(UUID entity);
    float pitch(UUID entity);
}
