package vn.svframe.mythicmobsfabric.engine;

import java.util.UUID;

/** Resolves targeted particle mechanics before delegating to their geometry implementation. */
public final class ParticleGeometryDispatch {
    private ParticleGeometryDispatch() {}

    public static boolean cast(SkillPlatform platform, SkillContext context, SkillLine line, UUID entity, Vec3 location) {
        if (SkillLine.normalize(line.mechanic()).equals("particlelineequation")) {
            UUID triggerTarget = context.triggerEntity();
            boolean defaultSelfTarget = entity == null || entity.equals(context.caster());
            if (defaultSelfTarget && triggerTarget != null && !triggerTarget.equals(context.caster())) {
                Vec3 targetLocation = platform.position(triggerTarget);
                if (targetLocation != null) return ParticleGeometryCompat.cast(platform, context, line, triggerTarget, targetLocation);
            }
        }
        return ParticleGeometryCompat.cast(platform, context, line, entity, location);
    }
}
