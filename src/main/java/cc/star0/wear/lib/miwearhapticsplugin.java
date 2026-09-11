package cc.star0.wear.lib;

import com.android.build.api.instrumentation.InstrumentationScope;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import com.android.build.api.variant.LibraryAndroidComponentsExtension;
import java.util.Set;
import kotlin.Unit;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** Adapts SDK call sites in Android applications and libraries, including third-party dependencies. */
public final class miwearhapticsplugin implements Plugin<Project> {
    private static final String RUNTIME = "cc.star0.wear.lib:miwearhaptics:1.0.0";

    @Override
    public void apply(Project project) {
        WearHapticsExtension extension =
                project.getExtensions().create("wearHaptics", WearHapticsExtension.class);
        project.getPlugins().withId("com.android.application", ignored -> register(
                project, extension,
                project.getExtensions().getByType(ApplicationAndroidComponentsExtension.class),
                InstrumentationScope.ALL));
        project.getPlugins().withId("com.android.library", ignored -> register(
                project, extension,
                project.getExtensions().getByType(LibraryAndroidComponentsExtension.class),
                InstrumentationScope.PROJECT));
    }

    private static void register(
            Project project, WearHapticsExtension extension,
            AndroidComponentsExtension<?, ?, ?> components, InstrumentationScope scope) {
        // The included build supplies this Java-only module through composite dependency substitution.
        project.getDependencies().add("implementation", RUNTIME);
        components.onVariants(components.selector().all(), variant -> {
            if (!extension.getEnabled().get()) {
                return;
            }
            Set<String> variants = extension.getVariants().get();
            if (!variants.isEmpty() && !variants.contains(variant.getName())) {
                return;
            }
            Set<String> methods = extension.getMethods().get();
            if (!WearHapticsClassVisitor.SDK_GETTERS.containsAll(methods)) {
                throw new GradleException("wearHaptics.methods supports only "
                        + WearHapticsClassVisitor.SDK_GETTERS + "; requested " + methods);
            }
            variant.getInstrumentation().transformClassesWith(
                    WearHapticsInstrumentation.class, scope, parameters -> {
                        parameters.getIncludedClassPrefixes().set(extension.getIncludedClassPrefixes());
                        parameters.getExcludedClassPrefixes().set(extension.getExcludedClassPrefixes());
                        parameters.getMethods().set(extension.getMethods());
                        parameters.getFailOnUnsupportedCalls().set(extension.getFailOnUnsupportedCalls());
                        return Unit.INSTANCE;
                    });
        });
    }
}
