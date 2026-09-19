package cc.star0.wear.lib.miwearhaptics;

import android.util.Log;
import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat.Effect;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/** Modern libxposed API 102 entry point for rootful frameworks and JingMatrix/LSPatch. */
public final class WearHapticsXposed extends XposedModule {
    private static final String MODULE_PACKAGE = "cc.star0.wear.xposed.miwearhaptics";
    private static final String GOOGLE = "com.google.wear.input.WearHapticFeedbackConstants";
    private static final String XIAOMI = "com.xiaomi.miwear.input.WearHapticFeedbackConstants";
    private static final Object[] NO_ARGS = new Object[0];
    private final Set<Method> hooked = new HashSet<>();
    private String processName;
    private boolean systemServer;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        systemServer = param.isSystemServer();
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        // LSPatch also supplies this lifecycle callback with the target's final class loader.
        // Neither root access nor a companion/service connection is required by this module.
        if (systemServer || "android".equals(param.getPackageName())
                || MODULE_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        ClassLoader loader = param.getClassLoader();
        Class<?> google;
        try {
            // Do not initialize SDK classes until all available getters have been hooked.
            google = Class.forName(GOOGLE, false, loader);
        } catch (ClassNotFoundException | LinkageError | RuntimeException unavailable) {
            logPackage(param, "Google class unavailable; no hooks installed");
            return;
        }

        // The module's own loader does not necessarily see the target's wear-sdk shared library.
        XiaomiFirstHook selection = new XiaomiFirstHook(
                new HapticConstantsResolver(XIAOMI, GOOGLE, loader));
        int installed = 0;
        for (Effect effect : Effect.values()) {
            try {
                Method getter = google.getDeclaredMethod(effect.getter);
                if (!Modifier.isPublic(getter.getModifiers())
                        || !Modifier.isStatic(getter.getModifiers())
                        || Modifier.isAbstract(getter.getModifiers())
                        || getter.getReturnType() != int.class) {
                    logPackage(param, "Unsupported signature: " + effect.getter);
                    continue;
                }
                synchronized (hooked) {
                    // Several packages in one process may resolve the same shared SDK Method.
                    if (hooked.contains(getter)) {
                        continue;
                    }
                    Invoker<?, Method> original = getInvoker(getter).setType(Invoker.Type.ORIGIN);
                    hook(getter)
                            .setId("miwearhaptics." + effect.getter)
                            // PROTECTIVE would swallow VM errors thrown by the resolver.
                            .setExceptionMode(ExceptionMode.PASSTHROUGH)
                            .intercept(chain -> selection.get(effect,
                                    () -> (Integer) original.invoke(null, NO_ARGS)));
                    hooked.add(getter);
                    installed++;
                }
            } catch (NoSuchMethodException | LinkageError | RuntimeException unavailable) {
                // A partially implemented SDK must not prevent other effects from working.
                logPackage(param, "Cannot hook " + effect.getter + ": "
                        + unavailable.getClass().getSimpleName());
            }
        }
        if (installed != 0) {
            logPackage(param, "Installed " + installed + " getter hooks (XIAOMI_FIRST)");
        }
    }

    private void logPackage(PackageReadyParam param, String message) {
        log(Log.INFO, "MiWearHaptics", param.getPackageName() + "/"
                + processName + ": " + message);
    }
}
