package com.cardrestricted;

/** Single visible build identifier used by the UI and release checks. */
public final class PluginBuildInfo
{
    public static final String VERSION = "0.81.04";
    public static final String CHANNEL = "Beta candidate";
    public static final String DEVELOPER_TESTING_PROPERTY =
        "cardlocked.developerTesting";
    private static final String DEVELOPER_MARKER =
        "/META-INF/card-locked-development.marker";

    private PluginBuildInfo()
    {
    }

    /**
     * Economy-altering test controls require both an explicit launch opt-in
     * and a resource which is present only in the development artifact.
     * Standard release JARs cannot enable these controls through stale
     * configuration or a JVM property alone.
     */
    public static boolean isDeveloperRuntime()
    {
        return Boolean.getBoolean(DEVELOPER_TESTING_PROPERTY)
            && PluginBuildInfo.class.getResource(DEVELOPER_MARKER) != null;
    }
}
