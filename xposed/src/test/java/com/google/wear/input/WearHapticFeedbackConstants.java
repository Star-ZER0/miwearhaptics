package com.google.wear.input;

/** JVM-only original methods for the framework adapter tests. */
public final class WearHapticFeedbackConstants {
    public static int calls;
    public static Throwable failure;

    public static int getScrollItemFocus() throws Throwable { return read(201); }
    public static int getScrollTick() throws Throwable { return read(202); }
    public static int getScrollLimit() throws Throwable { return read(203); }

    private static int read(int value) throws Throwable {
        calls++;
        if (failure != null) {
            throw failure;
        }
        return value;
    }
}
