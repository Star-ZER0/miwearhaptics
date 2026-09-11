package cc.star0.wear.lib.miwearhaptics;

import java.util.Objects;

/**
 * Optional Google/Xiaomi Wear SDK haptic constants, independent of AndroidX and app packages.
 *
 * <p>The three SDK getters retain their original {@code ()I} JVM signatures. When no selected SDK
 * can supply an effect, they return {@link #NO_HAPTICS}. Source callers can use {@code getOrNull}
 * to skip the feedback call altogether. Declare the optional {@code wear-sdk} shared library in
 * the application's manifest; do not bundle a device SDK implementation in the APK.
 */
public final class WearHapticFeedbackConstantsCompat {
    /** Same value as android.view.HapticFeedbackConstants.NO_HAPTICS; never a substitute effect. */
    public static final int NO_HAPTICS = -1;

    public enum Effect {
        SCROLL_ITEM_FOCUS("getScrollItemFocus"),
        SCROLL_TICK("getScrollTick"),
        SCROLL_LIMIT("getScrollLimit");

        final String getter;

        Effect(String getter) {
            this.getter = getter;
        }
    }

    /** Selection is based on callable SDK methods, not manufacturer strings or API-level guesses. */
    public enum Policy {
        XIAOMI_FIRST,
        GOOGLE_FIRST,
        XIAOMI_ONLY,
        GOOGLE_ONLY,
        DISABLED
    }

    private static final HapticConstantsResolver RESOLVER = new HapticConstantsResolver(
            "com.xiaomi.miwear.input.WearHapticFeedbackConstants",
            "com.google.wear.input.WearHapticFeedbackConstants",
            WearHapticFeedbackConstantsCompat.class.getClassLoader());

    private static volatile Policy policy = Policy.GOOGLE_FIRST;

    private WearHapticFeedbackConstantsCompat() {}

    /** Set before UI/library initialization; constants already cached by a caller cannot change. */
    public static void setPolicy(Policy value) {
        policy = Objects.requireNonNull(value, "policy");
    }

    public static Policy getPolicy() {
        return policy;
    }

    public static int getScrollItemFocus() {
        return get(Effect.SCROLL_ITEM_FOCUS);
    }

    public static int getScrollTick() {
        return get(Effect.SCROLL_TICK);
    }

    public static int getScrollLimit() {
        return get(Effect.SCROLL_LIMIT);
    }

    public static int get(Effect effect) {
        return get(effect, policy);
    }

    /** Per-call selection without modifying the global policy used by instrumented code. */
    public static int get(Effect effect, Policy selection) {
        Integer value = getOrNull(effect, selection);
        return value != null ? value : NO_HAPTICS;
    }

    public static Integer getOrNull(Effect effect) {
        return getOrNull(effect, policy);
    }

    /** Returns null if this effect is unavailable under the requested policy. */
    public static Integer getOrNull(Effect effect, Policy selection) {
        return RESOLVER.resolve(
                Objects.requireNonNull(effect, "effect"),
                Objects.requireNonNull(selection, "policy"));
    }
}
