package cc.star0.wear.lib;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import java.util.List;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.jspecify.annotations.NonNull;
import org.objectweb.asm.ClassVisitor;

/** All parameters are Gradle inputs so changing selection invalidates cached transformations. */
public abstract class WearHapticsInstrumentation
        implements AsmClassVisitorFactory<WearHapticsInstrumentation.Parameters> {
    public interface Parameters extends InstrumentationParameters {
        @Input ListProperty<String> getIncludedClassPrefixes();
        @Input ListProperty<String> getExcludedClassPrefixes();
        @Input SetProperty<String> getMethods();
        @Input Property<Boolean> getFailOnUnsupportedCalls();
    }

    @Override
    public boolean isInstrumentable(ClassData classData) {
        Parameters parameters = getParameters().get();
        return includesClass(classData.getClassName(),
                parameters.getIncludedClassPrefixes().get(),
                parameters.getExcludedClassPrefixes().get());
    }

    static boolean includesClass(String name, List<String> includes, List<String> excludes) {
        // Never adapt the resolver or device SDK implementations themselves.
        if (name.startsWith("cc.star0.wear.lib.miwearhaptics.")
                || name.startsWith("com.google.wear.input.")
                || name.startsWith("com.xiaomi.miwear.input.")) {
            return false;
        }
        return (includes.isEmpty() || includes.stream().anyMatch(name::startsWith))
                && excludes.stream().noneMatch(name::startsWith);
    }

    @Override
    public @NonNull ClassVisitor createClassVisitor(
            @NonNull ClassContext classContext, @NonNull ClassVisitor nextClassVisitor) {
        Parameters parameters = getParameters().get();
        return new WearHapticsClassVisitor(nextClassVisitor,
                parameters.getMethods().get(), parameters.getFailOnUnsupportedCalls().get());
    }
}
