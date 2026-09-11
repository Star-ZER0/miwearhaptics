package cc.star0.wear.lib.miwearhaptics;

import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat.Effect;
import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat.Policy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumMap;

/** Caches each SDK/effect independently, including failed lookups. */
final class HapticConstantsResolver {
    private final SdkConstants xiaomi;
    private final SdkConstants google;

    HapticConstantsResolver(String xiaomiClass, String googleClass, ClassLoader loader) {
        xiaomi = new SdkConstants(xiaomiClass, loader);
        google = new SdkConstants(googleClass, loader);
    }

    Integer resolve(Effect effect, Policy policy) {
        switch (policy) {
            case XIAOMI_FIRST:
                return firstAvailable(xiaomi, google, effect);
            case GOOGLE_FIRST:
                return firstAvailable(google, xiaomi, effect);
            case XIAOMI_ONLY:
                return xiaomi.get(effect);
            case GOOGLE_ONLY:
                return google.get(effect);
            case DISABLED:
                return null;
            default:
                throw new AssertionError(policy);
        }
    }

    private static Integer firstAvailable(SdkConstants first, SdkConstants second, Effect effect) {
        Integer value = first.get(effect);
        return value != null ? value : second.get(effect);
    }

    private static final class SdkConstants {
        private final String className;
        private final ClassLoader loader;
        private final EnumMap<Effect, Integer> constants = new EnumMap<>(Effect.class);

        SdkConstants(String className, ClassLoader loader) {
            this.className = className;
            this.loader = loader;
        }

        synchronized Integer get(Effect effect) {
            if (!constants.containsKey(effect)) {
                constants.put(effect, read(effect));
            }
            return constants.get(effect);
        }

        private Integer read(Effect effect) {
            try {
                // No direct SDK type reference: missing/partially implemented shared libraries are OK.
                Class<?> sdk = Class.forName(className, true, loader);
                Method getter = sdk.getMethod(effect.getter);
                if (!Modifier.isStatic(getter.getModifiers()) || getter.getReturnType() != int.class) {
                    return null;
                }
                return (Integer) getter.invoke(null);
            } catch (InvocationTargetException failure) {
                // Firmware failures are optional SDK failures; process-fatal errors are not.
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
}
