package cc.star0.wear.lib.miwearhaptics;

import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat.Effect;
import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat.Policy;
import java.lang.reflect.InvocationTargetException;
import java.util.EnumMap;

/** Per target class loader: Xiaomi reflection first, then the unhooked Google getter. */
final class XiaomiFirstHook {
    interface OriginalGetter {
        int get() throws ReflectiveOperationException;
    }

    private final HapticConstantsResolver resolver;
    private final EnumMap<Effect, Integer> google = new EnumMap<>(Effect.class);
    private boolean resolving;

    XiaomiFirstHook(HapticConstantsResolver resolver) {
        this.resolver = resolver;
    }

    synchronized int get(Effect effect, OriginalGetter original) throws ReflectiveOperationException {
        // Some vendor getters delegate back to Google. The monitor is reentrant, so only the
        // resolving thread can take this branch; bypass Xiaomi to avoid a hook recursion loop.
        if (resolving) {
            return original.get();
        }
        resolving = true;
        try {
            // GOOGLE_FIRST/XIAOMI_FIRST on this resolver would reflect into our own hook.
            Integer value = resolver.resolve(effect, Policy.XIAOMI_ONLY);
            if (value != null) {
                return value;
            }
            if (!google.containsKey(effect)) {
                google.put(effect, readOriginal(original));
            }
            value = google.get(effect);
            return value != null ? value : WearHapticFeedbackConstantsCompat.NO_HAPTICS;
        } finally {
            resolving = false;
        }
    }

    private static Integer readOriginal(OriginalGetter original) {
        try {
            return original.get();
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof VirtualMachineError) {
                throw (VirtualMachineError) cause;
            }
            if (cause instanceof ThreadDeath) {
                throw (ThreadDeath) cause;
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return null;
        }
    }
}
