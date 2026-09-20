package cc.star0.wear.lib.miwearhaptics;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.InMemoryDexClassLoader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.function.IntUnaryOperator;

/** Adds only the three-getter bridge to a missing SDK's target dex path. */
final class MissingGoogleClass {
    static final String GOOGLE = "com.google.wear.input.WearHapticFeedbackConstants";

    private MissingGoogleClass() {}

    static void install(ClassLoader target, IntUnaryOperator fallback)
            throws ReflectiveOperationException, IOException {
        if (!(target instanceof BaseDexClassLoader)) {
            throw new IllegalArgumentException("Target is not a BaseDexClassLoader");
        }
        byte[] dex;
        try (InputStream input = MissingGoogleClass.class.getResourceAsStream("/miwearhaptics/classes.dex")) {
            if (input == null) {
                throw new IOException("Missing Google fallback dex resource");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            dex = output.toByteArray();
        }
        ClassLoader bridge = new InMemoryDexClassLoader(ByteBuffer.wrap(dex), target);
        // Do not replace the parent, prepend the module APK, or expose other module classes.
        // Access to these Android internals depends on the hosting Xposed framework.
        Field pathList = BaseDexClassLoader.class.getDeclaredField("pathList");
        pathList.setAccessible(true);
        Object targetPath = pathList.get(target);
        Object bridgePath = pathList.get(bridge);
        Field elements = targetPath.getClass().getDeclaredField("dexElements");
        elements.setAccessible(true);
        synchronized (target) {
            // Another package callback may have already made the class available.
            try {
                Class.forName(GOOGLE, false, target);
                return;
            } catch (ClassNotFoundException missing) {
                // Append so existing application and shared-library classes keep precedence.
            }
            Object existing = elements.get(targetPath);
            Object extra = elements.get(bridgePath);
            int length = Array.getLength(existing);
            Object combined = Array.newInstance(existing.getClass().getComponentType(),
                    length + Array.getLength(extra));
            System.arraycopy(existing, 0, combined, 0, length);
            System.arraycopy(extra, 0, combined, length, Array.getLength(extra));
            elements.set(targetPath, combined);
            Class<?> installed = Class.forName(GOOGLE, true, target);
            installed.getField("fallback").set(null, fallback);
        }
    }
}
