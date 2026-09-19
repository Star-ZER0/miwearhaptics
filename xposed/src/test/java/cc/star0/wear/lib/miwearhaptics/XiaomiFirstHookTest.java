package cc.star0.wear.lib.miwearhaptics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat.Effect;
import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat.Policy;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

public final class XiaomiFirstHookTest {
    private static final String MISSING = "test.missing.WearHapticFeedbackConstants";

    @Before
    public void reset() {
        Xiaomi.calls.set(0);
        BrokenXiaomi.calls.set(0);
        BrokenXiaomi.failure = new IllegalStateException("unavailable");
        DelegatingXiaomi.delegate = null;
    }

    @Test
    public void xiaomiWinsForAllThreeEffectsWithoutCallingGoogle() throws Exception {
        XiaomiFirstHook hook = hook(Xiaomi.class);
        XiaomiFirstHook.OriginalGetter google = () -> {
            throw new AssertionError("Google must not be called");
        };
        for (int i = 0; i < 2; i++) {
            assertEquals(101, hook.get(Effect.SCROLL_ITEM_FOCUS, google));
            assertEquals(102, hook.get(Effect.SCROLL_TICK, google));
            assertEquals(103, hook.get(Effect.SCROLL_LIMIT, google));
        }
        assertEquals(3, Xiaomi.calls.get());
    }

    @Test
    public void missingXiaomiFallsBackToGoogleAndCachesEachEffect() throws Exception {
        XiaomiFirstHook hook = hook(MISSING, getClass().getClassLoader());
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            assertEquals(201, hook.get(Effect.SCROLL_ITEM_FOCUS, () -> {
                calls.incrementAndGet();
                return 201;
            }));
            assertEquals(202, hook.get(Effect.SCROLL_TICK, () -> {
                calls.incrementAndGet();
                return 202;
            }));
        }
        assertEquals(2, calls.get());
    }

    @Test
    public void missingEffectsDoNotDisableWorkingXiaomiEffects() throws Exception {
        XiaomiFirstHook hook = hook(PartialXiaomi.class);
        assertEquals(202, hook.get(Effect.SCROLL_TICK, () -> 202));
        assertEquals(301, hook.get(Effect.SCROLL_ITEM_FOCUS, () -> 201));
        assertEquals(203, hook.get(Effect.SCROLL_LIMIT, () -> 203));
    }

    @Test
    public void rejectsInstanceMethodsAndNonIntReturnValues() throws Exception {
        XiaomiFirstHook hook = hook(InvalidXiaomi.class);
        assertEquals(201, hook.get(Effect.SCROLL_ITEM_FOCUS, () -> 201));
        assertEquals(202, hook.get(Effect.SCROLL_TICK, () -> 202));
        assertEquals(203, hook.get(Effect.SCROLL_LIMIT, () -> 203));
    }

    @Test
    public void xiaomiInvocationFailureIsCachedWhileGoogleStillWorks() throws Exception {
        XiaomiFirstHook hook = hook(BrokenXiaomi.class);
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            assertEquals(202, hook.get(Effect.SCROLL_TICK, () -> {
                calls.incrementAndGet();
                return 202;
            }));
        }
        assertEquals(1, BrokenXiaomi.calls.get());
        assertEquals(1, calls.get());
    }

    @Test
    public void unavailableGoogleAndXiaomiReturnNoHapticsAndCacheFailure() throws Exception {
        XiaomiFirstHook hook = hook(BrokenXiaomi.class);
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            assertEquals(-1, hook.get(Effect.SCROLL_TICK, () -> {
                calls.incrementAndGet();
                throw new InvocationTargetException(new UnsupportedOperationException());
            }));
        }
        assertEquals(1, BrokenXiaomi.calls.get());
        assertEquals(1, calls.get());
    }

    @Test
    public void googleRecoverableReflectionAndLinkageFailuresReturnNoHaptics() throws Exception {
        XiaomiFirstHook hook = hook(MISSING, getClass().getClassLoader());
        assertEquals(-1, hook.get(Effect.SCROLL_ITEM_FOCUS, () -> {
            throw new IllegalAccessException();
        }));
        assertEquals(-1, hook.get(Effect.SCROLL_TICK, () -> {
            throw new NoSuchMethodError();
        }));
        assertEquals(-1, hook.get(Effect.SCROLL_LIMIT, () -> {
            throw new IllegalStateException();
        }));
    }

    @Test
    public void xiaomiLinkageFailureFallsBackToGoogle() throws Exception {
        BrokenXiaomi.failure = new NoClassDefFoundError("firmware dependency");
        assertEquals(202, hook(BrokenXiaomi.class).get(Effect.SCROLL_TICK, () -> 202));
    }

    @Test
    public void allReturnedIntegersAreAcceptedIncludingNoHaptics() throws Exception {
        XiaomiFirstHook hook = hook(NegativeXiaomi.class);
        assertEquals(-1, hook.get(Effect.SCROLL_TICK, () -> 202));
        assertEquals(0, hook.get(Effect.SCROLL_LIMIT, () -> 203));
        assertEquals(-8, hook(MISSING, getClass().getClassLoader())
                .get(Effect.SCROLL_TICK, () -> -8));
    }

    @Test
    public void xiaomiFatalErrorsPropagateAndDoNotPoisonCacheOrRecursionGuard() throws Exception {
        XiaomiFirstHook hook = hook(BrokenXiaomi.class);
        Error[] failures = {new OutOfMemoryError("test"), new ThreadDeath()};
        for (Error failure : failures) {
            BrokenXiaomi.failure = failure;
            assertSame(failure, assertThrows(failure.getClass(),
                    () -> hook.get(Effect.SCROLL_TICK, () -> 202)));
        }
        BrokenXiaomi.failure = new IllegalStateException();
        assertEquals(202, hook.get(Effect.SCROLL_TICK, () -> 202));
        assertEquals(3, BrokenXiaomi.calls.get());
    }

    @Test
    public void googleFatalErrorsPropagateWrappedAndDirect() throws Exception {
        XiaomiFirstHook hook = hook(MISSING, getClass().getClassLoader());
        Error[] failures = {new OutOfMemoryError("test"), new ThreadDeath()};
        for (Error failure : failures) {
            assertSame(failure, assertThrows(failure.getClass(),
                    () -> hook.get(Effect.SCROLL_TICK, () -> {
                        throw new InvocationTargetException(failure);
                    })));
            assertSame(failure, assertThrows(failure.getClass(),
                    () -> hook.get(Effect.SCROLL_TICK, () -> {
                        throw failure;
                    })));
        }
        assertEquals(202, hook.get(Effect.SCROLL_TICK, () -> 202));
    }

    @Test
    public void usesTargetLoaderAndCachesFailedClassLookup() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        ClassLoader target = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(Xiaomi.class.getName())) {
                    loads.incrementAndGet();
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        XiaomiFirstHook hook = hook(Xiaomi.class.getName(), target);
        assertEquals(202, hook.get(Effect.SCROLL_TICK, () -> 202));
        assertEquals(202, hook.get(Effect.SCROLL_TICK, () -> 999));
        assertEquals(1, loads.get());
        assertEquals(0, Xiaomi.calls.get());
        assertEquals(102, hook(Xiaomi.class).get(Effect.SCROLL_TICK, () -> 202));
    }

    @Test(timeout = 10000)
    public void concurrentCallsResolveEachEffectOnlyOnce() throws Exception {
        XiaomiFirstHook hook = hook(BrokenXiaomi.class);
        AtomicInteger calls = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 32; i++) {
                results.add(workers.submit(() -> {
                    start.await();
                    return hook.get(Effect.SCROLL_TICK, () -> {
                        calls.incrementAndGet();
                        return 202;
                    });
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertEquals(202, result.get(5, TimeUnit.SECONDS).intValue());
            }
            assertEquals(1, calls.get());
            assertEquals(1, BrokenXiaomi.calls.get());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    public void vendorDelegationToGoogleDoesNotReenterXiaomi() throws Exception {
        XiaomiFirstHook hook = hook(DelegatingXiaomi.class);
        AtomicInteger calls = new AtomicInteger();
        XiaomiFirstHook.OriginalGetter google = () -> {
            calls.incrementAndGet();
            return 202;
        };
        DelegatingXiaomi.delegate = () -> hook.get(Effect.SCROLL_TICK, google);
        assertEquals(202, hook.get(Effect.SCROLL_TICK, google));
        assertEquals(202, hook.get(Effect.SCROLL_TICK, google));
        assertEquals(1, calls.get());
    }

    @Test
    public void originalRuntimeDefaultRemainsGoogleFirst() {
        assertEquals(Policy.GOOGLE_FIRST, WearHapticFeedbackConstantsCompat.getPolicy());
        HapticConstantsResolver resolver = new HapticConstantsResolver(
                Xiaomi.class.getName(), NegativeXiaomi.class.getName(), getClass().getClassLoader());
        assertEquals(Integer.valueOf(-1), resolver.resolve(Effect.SCROLL_TICK, Policy.GOOGLE_FIRST));
        assertEquals(Integer.valueOf(102), resolver.resolve(Effect.SCROLL_TICK, Policy.XIAOMI_FIRST));
    }

    private static XiaomiFirstHook hook(Class<?> sdk) {
        return hook(sdk.getName(), sdk.getClassLoader());
    }

    private static XiaomiFirstHook hook(String sdk, ClassLoader loader) {
        // An absent Google class also proves fallback uses the supplied original getter,
        // rather than reflectively calling a hooked Google method through the shared resolver.
        return new XiaomiFirstHook(new HapticConstantsResolver(sdk, MISSING, loader));
    }

    public static final class Xiaomi {
        static final AtomicInteger calls = new AtomicInteger();

        public static int getScrollItemFocus() { calls.incrementAndGet(); return 101; }
        public static int getScrollTick() { calls.incrementAndGet(); return 102; }
        public static int getScrollLimit() { calls.incrementAndGet(); return 103; }
    }

    public static final class PartialXiaomi {
        public static int getScrollItemFocus() { return 301; }
    }

    public static final class InvalidXiaomi {
        public int getScrollItemFocus() { throw new AssertionError(); }
        public static Integer getScrollTick() { throw new AssertionError(); }
        public static int getScrollLimit(int unused) { throw new AssertionError(); }
    }

    public static final class BrokenXiaomi {
        static final AtomicInteger calls = new AtomicInteger();
        static Throwable failure;

        public static int getScrollTick() throws Throwable {
            calls.incrementAndGet();
            throw failure;
        }
    }

    public static final class NegativeXiaomi {
        public static int getScrollTick() { return -1; }
        public static int getScrollLimit() { return 0; }
    }

    public static final class DelegatingXiaomi {
        static XiaomiFirstHook.OriginalGetter delegate;

        public static int getScrollTick() throws ReflectiveOperationException {
            return delegate.get();
        }
    }
}
