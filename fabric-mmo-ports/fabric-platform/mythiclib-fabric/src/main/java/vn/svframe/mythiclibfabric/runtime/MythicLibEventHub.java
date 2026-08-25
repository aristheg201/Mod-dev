package vn.svframe.mythiclibfabric.runtime;

/** Shared public event bus for native Fabric ports of MythicLib Bukkit events. */
public final class MythicLibEventHub {
    private static final EventBus BUS = new EventBus();

    private MythicLibEventHub() { }

    public static EventBus events() {
        return BUS;
    }
}
