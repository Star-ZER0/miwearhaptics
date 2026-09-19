package cc.star0.wear.lib.miwearhaptics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.wear.input.WearHapticFeedbackConstants;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.HookBuilder;
import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedInterface.Invoker;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/** Exercises the published API and actual module callbacks; does not simulate ART interception. */
public final class WearHapticsXposedTest {
    private final Map<Method, Registration> registrations = new LinkedHashMap<>();
    private final Map<Method, Invoker.Type> invokerTypes = new LinkedHashMap<>();
    private final List<String> logs = new ArrayList<>();
    private WearHapticsXposed module;

    @Before
    public void setUp() {
        WearHapticFeedbackConstants.calls = 0;
        WearHapticFeedbackConstants.failure = null;
        module = new WearHapticsXposed();
        XposedInterface framework = proxy(XposedInterface.class, (instance, method, args) -> {
            switch (method.getName()) {
                case "getInvoker":
                    Method original = (Method) args[0];
                    return proxy(Invoker.class, (invoker, operation, call) -> {
                        switch (operation.getName()) {
                            case "setType":
                                invokerTypes.put(original, (Invoker.Type) call[0]);
                                return invoker;
                            case "invoke":
                                assertSame(Invoker.Type.ORIGIN, invokerTypes.get(original));
                                return original.invoke(call[0], (Object[]) call[1]);
                            default:
                                throw new AssertionError(operation.getName());
                        }
                    });
                case "hook":
                    return builder((Method) args[0]);
                case "log":
                    logs.add((String) args[2]);
                    return null;
                default:
                    // Root/system capabilities, remote preferences and services are unnecessary
                    // for both LSPatch integrated mode and a normal framework process.
                    throw new AssertionError("Unexpected framework API: " + method.getName());
            }
        });
        // This test harness acts as the framework. Production module code never calls attachFramework.
        module.attachFramework(framework, () -> {});
        module.onModuleLoaded(process(false));
    }

    @Test
    public void installsModernHooksWithOriginInvokerAndFatalErrorPropagation() {
        module.onPackageReady(target("test.app", getClass().getClassLoader()));
        assertEquals(3, registrations.size());
        registrations.forEach((method, registration) -> {
            assertEquals("miwearhaptics." + method.getName(), registration.id);
            assertEquals(ExceptionMode.PASSTHROUGH, registration.mode);
            assertSame(Invoker.Type.ORIGIN, invokerTypes.get(method));
        });
        assertEquals(0, WearHapticFeedbackConstants.calls);
    }

    @Test
    public void googleFallbackUsesOriginalInvokerAndCachesResults() throws Throwable {
        module.onPackageReady(target("test.app", getClass().getClassLoader()));
        for (int i = 0; i < 2; i++) {
            assertEquals(201, invoke("getScrollItemFocus"));
            assertEquals(202, invoke("getScrollTick"));
            assertEquals(203, invoke("getScrollLimit"));
        }
        assertEquals(3, WearHapticFeedbackConstants.calls);
    }

    @Test
    public void unavailableOriginalIsCachedAndReturnsNoHaptics() throws Throwable {
        WearHapticFeedbackConstants.failure = new UnsupportedOperationException();
        module.onPackageReady(target("test.app", getClass().getClassLoader()));
        assertEquals(-1, invoke("getScrollTick"));
        assertEquals(-1, invoke("getScrollTick"));
        assertEquals(1, WearHapticFeedbackConstants.calls);
    }

    @Test
    public void originalFatalErrorReachesCaller() {
        OutOfMemoryError failure = new OutOfMemoryError("fixture");
        WearHapticFeedbackConstants.failure = failure;
        module.onPackageReady(target("test.app", getClass().getClassLoader()));
        assertSame(failure, assertThrows(OutOfMemoryError.class, () -> invoke("getScrollTick")));
    }

    @Test
    public void packagesSharingSdkMethodsDoNotInstallDuplicateHooks() {
        module.onPackageReady(target("test.first", getClass().getClassLoader()));
        module.onPackageReady(target("test.second", getClass().getClassLoader()));
        assertEquals(3, registrations.size());
        assertEquals(1, logs.size());
    }

    @Test
    public void missingGoogleClassInFinalLoaderIsSkipped() {
        ClassLoader withoutGoogle = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(WearHapticFeedbackConstants.class.getName())) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        module.onPackageReady(target("test.app", withoutGoogle));
        assertTrue(registrations.isEmpty());
        assertTrue(logs.get(0).contains("Google class unavailable"));
    }

    @Test
    public void skipsModuleAndroidAndAllSystemServerPackages() {
        module.onPackageReady(target("android", getClass().getClassLoader()));
        module.onPackageReady(target("cc.star0.wear.xposed.miwearhaptics", getClass().getClassLoader()));
        module.onModuleLoaded(process(true));
        module.onPackageReady(target("test.system", getClass().getClassLoader()));
        assertTrue(registrations.isEmpty());
    }

    private Object invoke(String name) throws Throwable {
        Hooker hooker = registrations.get(WearHapticFeedbackConstants.class.getMethod(name)).hooker;
        return hooker.intercept(proxy(Chain.class, (instance, method, args) -> {
            throw new AssertionError("Fallback must bypass the hook chain");
        }));
    }

    private HookBuilder builder(Method original) {
        Registration registration = new Registration();
        return proxy(HookBuilder.class, (builder, method, args) -> {
            switch (method.getName()) {
                case "setId":
                    registration.id = (String) args[0];
                    return builder;
                case "setExceptionMode":
                    registration.mode = (ExceptionMode) args[0];
                    return builder;
                case "intercept":
                    assertTrue("Duplicate hook", !registrations.containsKey(original));
                    registration.hooker = (Hooker) args[0];
                    registrations.put(original, registration);
                    return proxy(HookHandle.class, (handle, operation, call) -> {
                        if ("getExecutable".equals(operation.getName())) {
                            return original;
                        }
                        throw new AssertionError(operation.getName());
                    });
                default:
                    throw new AssertionError(method.getName());
            }
        });
    }

    private static PackageReadyParam target(String name, ClassLoader loader) {
        return proxy(PackageReadyParam.class, (instance, method, args) -> {
            if ("getPackageName".equals(method.getName())) {
                return name;
            }
            if ("getClassLoader".equals(method.getName())) {
                return loader;
            }
            throw new AssertionError("Must use the final class loader: " + method.getName());
        });
    }

    private static ModuleLoadedParam process(boolean systemServer) {
        return proxy(ModuleLoadedParam.class, (instance, method, args) -> {
            if ("isSystemServer".equals(method.getName())) {
                return systemServer;
            }
            if ("getProcessName".equals(method.getName())) {
                return "test.app:worker";
            }
            throw new AssertionError(method.getName());
        });
    }

    private static <T> T proxy(Class<T> api, InvocationHandler handler) {
        return api.cast(Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, handler));
    }

    private static final class Registration {
        String id;
        ExceptionMode mode;
        Hooker hooker;
    }
}
