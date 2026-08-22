package vn.svframe.waypoints;

import vn.svframe.waypoints.navigation.NavigationManager;

public final class SvfWaypoints {
    private static final SvfWaypoints INSTANCE = new SvfWaypoints();
    private final NavigationManager navigation = new NavigationManager();

    private SvfWaypoints() {}

    public static SvfWaypoints instance() { return INSTANCE; }
    public NavigationManager navigation() { return navigation; }
}
