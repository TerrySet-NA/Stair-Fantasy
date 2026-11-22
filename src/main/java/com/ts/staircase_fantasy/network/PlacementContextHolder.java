package com.ts.staircase_fantasy.network;

public class PlacementContextHolder {
    private static final ThreadLocal<Boolean> SPECIAL_MODE = ThreadLocal.withInitial(() -> false);

    public static void set(boolean enabled) {
        SPECIAL_MODE.set(enabled);
    }

    public static boolean isSpecialModeEnabled() {
        return SPECIAL_MODE.get();
    }

    public static void clear() {
        SPECIAL_MODE.remove();
    }
}