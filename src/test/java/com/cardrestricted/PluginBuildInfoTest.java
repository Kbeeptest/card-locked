package com.cardrestricted;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PluginBuildInfoTest
{
    @Test
    public void developmentRuntimeRequiresExplicitPropertyAndMarker()
    {
        String key = PluginBuildInfo.DEVELOPER_TESTING_PROPERTY;
        String previous = System.getProperty(key);
        try
        {
            System.clearProperty(key);
            assertFalse(PluginBuildInfo.isDeveloperRuntime());
            System.setProperty(key, "true");
            assertTrue(
                "Test runtime must carry the development marker.",
                PluginBuildInfo.isDeveloperRuntime());
        }
        finally
        {
            if (previous == null)
            {
                System.clearProperty(key);
            }
            else
            {
                System.setProperty(key, previous);
            }
        }
    }
}
