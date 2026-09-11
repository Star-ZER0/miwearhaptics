package cc.star0.wear.lib;

import java.util.List;
import java.util.Set;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

/** Selection controls for the wearHaptics Gradle DSL. Empty class/variant filters mean all. */
public abstract class WearHapticsExtension {
    public WearHapticsExtension() {
        getEnabled().convention(true);
        getIncludedClassPrefixes().convention(List.of());
        getExcludedClassPrefixes().convention(List.of());
        getVariants().convention(Set.of());
        getMethods().convention(WearHapticsClassVisitor.SDK_GETTERS);
        getFailOnUnsupportedCalls().convention(true);
    }

    public abstract Property<Boolean> getEnabled();

    public abstract ListProperty<String> getIncludedClassPrefixes();

    public abstract ListProperty<String> getExcludedClassPrefixes();

    public abstract SetProperty<String> getVariants();

    public abstract SetProperty<String> getMethods();

    public abstract Property<Boolean> getFailOnUnsupportedCalls();
}
