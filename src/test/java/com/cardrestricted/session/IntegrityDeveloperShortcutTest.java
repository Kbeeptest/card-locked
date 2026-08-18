package com.cardrestricted.session;

import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.collection.ProfileSetupOptions;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.domain.RestrictionPreset;
import com.cardrestricted.identity.CharacterKeyDeriver;
import com.cardrestricted.starter.StarterRewardChoice;
import java.nio.file.Files;
import java.time.Clock;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.fail;

public final class IntegrityDeveloperShortcutTest
{
    @Test
    public void integrityProfileRejectsPersistedTestingBalance() throws Exception
    {
        CollectionSessionService session = integritySession();
        assertRejectedInDeveloperRuntime(
            () -> session.applyTestingBalances(1_000_000L, 1_000_000L),
            "Integrity profile accepted developer balances.");
    }

    @Test
    public void integrityProfileRejectsMappedTestPacks() throws Exception
    {
        CollectionSessionService session = integritySession();
        assertRejectedInDeveloperRuntime(
            () -> session.purchaseTierFoilTestPack(new Random(1L)),
            "Integrity profile accepted a developer test pack.");
    }


    @Test
    public void integrityProfileRejectsLegacyTestPacks() throws Exception
    {
        CollectionSessionService session = integritySession();
        assertRejectedInDeveloperRuntime(
            () -> session.purchaseFoilTestPack(new Random(2L)),
            "Integrity profile accepted the legacy foil test pack.");
        assertRejectedInDeveloperRuntime(
            () -> session.purchasePremiumFoilTestPack(new Random(3L)),
            "Integrity profile accepted the premium foil test pack.");
    }

    @Test
    public void standardRuntimeRejectsDeveloperEconomyOnCasualProfile()
        throws Exception
    {
        CollectionSessionService session = casualSession();
        String key = com.cardrestricted.PluginBuildInfo
            .DEVELOPER_TESTING_PROPERTY;
        String previous = System.getProperty(key);
        System.clearProperty(key);
        try
        {
            try
            {
                session.applyTestingBalances(1_000_000L, 1_000_000L);
                fail("Standard runtime accepted developer balances.");
            }
            catch (IllegalStateException expected)
            {
                // Release runtime is deliberately unable to invoke this path.
            }
        }
        finally
        {
            restoreProperty(key, previous);
        }
    }

    private static CollectionSessionService integritySession() throws Exception
    {
        CollectionSessionService session = new CollectionSessionService(
            Files.createTempDirectory("card-locked-integrity-dev-"),
            MembersCatalogue.create(),
            new CharacterKeyDeriver(),
            Clock.systemUTC());
        session.open(424242L, "Integrity Test");
        session.create(new ProfileSetupOptions(
            EconomyMode.STANDARD,
            StarterRewardChoice.POINTS,
            RestrictionPreset.BALANCED,
            true,
            IntegrityMode.INTEGRITY));
        return session;
    }

    private static CollectionSessionService casualSession() throws Exception
    {
        CollectionSessionService session = new CollectionSessionService(
            Files.createTempDirectory("card-locked-casual-release-"),
            MembersCatalogue.create(),
            new CharacterKeyDeriver(),
            Clock.systemUTC());
        session.open(515151L, "Casual Release Test");
        session.create(new ProfileSetupOptions(
            EconomyMode.STANDARD,
            StarterRewardChoice.POINTS,
            RestrictionPreset.BALANCED,
            true,
            IntegrityMode.CASUAL));
        return session;
    }

    private static void assertRejectedInDeveloperRuntime(
        ThrowingAction action,
        String failureMessage)
        throws Exception
    {
        String key = com.cardrestricted.PluginBuildInfo
            .DEVELOPER_TESTING_PROPERTY;
        String previous = System.getProperty(key);
        System.setProperty(key, "true");
        try
        {
            try
            {
                action.run();
                fail(failureMessage);
            }
            catch (IllegalStateException expected)
            {
                // Integrity remains the second independent denial boundary.
            }
        }
        finally
        {
            restoreProperty(key, previous);
        }
    }

    private static void restoreProperty(String key, String value)
    {
        if (value == null)
        {
            System.clearProperty(key);
        }
        else
        {
            System.setProperty(key, value);
        }
    }

    @FunctionalInterface
    private interface ThrowingAction
    {
        void run() throws Exception;
    }
}
