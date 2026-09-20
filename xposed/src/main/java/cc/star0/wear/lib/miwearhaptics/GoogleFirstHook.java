package cc.star0.wear.lib.miwearhaptics;

import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat.Effect;
import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat.Policy;
import java.lang.reflect.InvocationTargetException;
import java.util.EnumMap;
import java.util.EnumSet;

/** Per target class loader: unhooked Google getter, Xiaomi reflection, then no haptics. */
final class GoogleFirstHook {
    interface OriginalGetter {
        int get() throws ReflectiveOperationException;
    }

    private final HapticConstantsResolver resolver;
    private final EnumMap<Effect, Integer> google = new EnumMap<>(Effect.class);
    private final EnumSet<Effect> resolving = EnumSet.noneOf(Effect.class);

    GoogleFirstHook(HapticConstantsResolver resolver) {
        this.resolver = resolver;
    }

    synchronized int get(Effect effect, OriginalGetter original) {
        // A vendor getter may delegate back to Google after Google has already failed. Reuse
        // its result (or unavailable state), without recursing into either SDK again.
        if (!resolving.add(effect)) {
            Integer value = google.get(effect);
            return value != null ? value : WearHapticFeedbackConstantsCompat.NO_HAPTICS;
        }
        try {
            if (!google.containsKey(effect)) {
                google.put(effect, readOriginal(original));
            }
            Integer value = google.get(effect);
            if (value == null) {
                // Reflecting Google through this resolver would invoke our own hook.
                value = resolver.resolve(effect, Policy.XIAOMI_ONLY);
            }
            return value != null ? value : WearHapticFeedbackConstantsCompat.NO_HAPTICS;
        } finally {
            resolving.remove(effect);
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
