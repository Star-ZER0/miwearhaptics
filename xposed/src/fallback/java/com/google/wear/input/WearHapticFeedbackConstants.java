package com.google.wear.input;

import java.util.function.IntUnaryOperator;

/** Missing-class compatibility entry, not a device SDK implementation. */
public final class WearHapticFeedbackConstants {
    // Only bootstrap types cross loaders; the module supplies a target-specific resolver.
    public static volatile IntUnaryOperator fallback;

    private WearHapticFeedbackConstants() {}

    public static int getScrollItemFocus() { return get(0); }
    public static int getScrollTick() { return get(1); }
    public static int getScrollLimit() { return get(2); }

    private static int get(int effect) {
        IntUnaryOperator current = fallback;
        return current == null ? -1 : current.applyAsInt(effect);
    }
}
