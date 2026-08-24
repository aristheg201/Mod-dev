package vn.svframe.mythiclibfabric.runtime;

import vn.svframe.mythiclibfabric.PassiveSkillRuntime;

/** Runtime attachment contract implemented on projectile entities by the snapshot mixin. */
public interface ProjectilePassiveSnapshotHolder {
    PassiveSkillRuntime.Snapshot mythiclib$getPassiveSnapshot();
    void mythiclib$setPassiveSnapshot(PassiveSkillRuntime.Snapshot snapshot);
}
