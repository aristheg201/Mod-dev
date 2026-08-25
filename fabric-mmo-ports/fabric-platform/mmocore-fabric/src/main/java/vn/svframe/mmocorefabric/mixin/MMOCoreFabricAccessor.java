package vn.svframe.mmocorefabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import vn.svframe.mmocorefabric.MMOCoreFabricMod;
import vn.svframe.mmocorefabric.runtime.gameplay.ClassRuntime;
import vn.svframe.mmocorefabric.runtime.progression.PlayerProgress;
import vn.svframe.mmocorefabric.runtime.social.GuildManager;
import vn.svframe.mmocorefabric.runtime.social.PartyManager;

import java.util.Map;
import java.util.UUID;

@Mixin(value = MMOCoreFabricMod.class, remap = false)
public interface MMOCoreFabricAccessor {
    @Accessor("PROFILES")
    static Map<UUID, PlayerProgress> mmocore$getProfiles() { throw new AssertionError(); }

    @Accessor("CLASSES")
    static ClassRuntime mmocore$getClasses() { throw new AssertionError(); }

    @Accessor("PARTIES")
    static PartyManager mmocore$getParties() { throw new AssertionError(); }

    @Accessor("GUILDS")
    static GuildManager mmocore$getGuilds() { throw new AssertionError(); }
}
