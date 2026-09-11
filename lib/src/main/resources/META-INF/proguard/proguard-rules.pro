# Device SDKs are optional. Preserve names/signatures if a consuming build supplies class stubs.
#noinspection ShrinkerUnresolvedReference
-keep,allowoptimization class com.google.wear.input.WearHapticFeedbackConstants {
    public static int getScrollItemFocus();
    public static int getScrollTick();
    public static int getScrollLimit();
}
#noinspection ShrinkerUnresolvedReference
-keep,allowoptimization class com.xiaomi.miwear.input.WearHapticFeedbackConstants {
    public static int getScrollItemFocus();
    public static int getScrollTick();
    public static int getScrollLimit();
}
-dontwarn com.google.wear.input.WearHapticFeedbackConstants
-dontwarn com.xiaomi.miwear.input.WearHapticFeedbackConstants
