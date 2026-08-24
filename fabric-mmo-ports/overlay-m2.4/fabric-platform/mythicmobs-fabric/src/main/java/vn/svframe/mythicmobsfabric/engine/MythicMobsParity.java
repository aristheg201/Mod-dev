package vn.svframe.mythicmobsfabric.engine;

/** Registers additional MythicMobs 5.6.2 behavior not covered by the M2.1 core runtime. */
public final class MythicMobsParity {
    private MythicMobsParity() {}
    public static void register(SkillRuntime runtime, SkillPlatform platform) {
        ParityMechanics.register(runtime, platform);
        ParityConditions.register(runtime, platform);
        ParityTargeters.register(runtime, platform);
    }
}
