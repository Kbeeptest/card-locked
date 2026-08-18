package com.cardrestricted.session;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.collection.ProfileSetupOptions;
import com.cardrestricted.collection.ProfileStateMarkers;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.domain.RestrictionPreset;
import com.cardrestricted.identity.CharacterKeyDeriver;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.SnapshotCodec;
import com.cardrestricted.persistence.TransactionalStateStore;
import com.cardrestricted.starter.StarterRewardChoice;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class ProfileIsolationRecoveryTest
{
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-03T15:30:00Z"),
        ZoneOffset.UTC);

    @Test
    public void switchingAccountsRetainsIndependentCollections()
        throws Exception
    {
        Path root = Files.createTempDirectory(
            "card-locked-session-isolation-");
        CardCatalogue catalogue = MembersCatalogue.create();
        CharacterKeyDeriver keys = new CharacterKeyDeriver();
        CollectionSessionService session = new CollectionSessionService(
            root,
            catalogue,
            keys,
            CLOCK);

        long accountA = 1010101L;
        long accountB = 2020202L;
        assertEquals(SessionStatus.NEEDS_SETUP,
            session.open(accountA, "Account A").getStatus());
        SessionSnapshot createdA = session.create(options());
        assertEquals(SessionStatus.READY, createdA.getStatus());
        UUID collectionA = createdA.getCollectionState()
            .orElseThrow(AssertionError::new).getCollectionId();
        applyDeveloperBalances(session, 111_111L, 111L);

        assertEquals(SessionStatus.NEEDS_SETUP,
            session.open(accountB, "Account B").getStatus());
        SessionSnapshot createdB = session.create(options());
        assertEquals(SessionStatus.READY, createdB.getStatus());
        UUID collectionB = createdB.getCollectionState()
            .orElseThrow(AssertionError::new).getCollectionId();
        applyDeveloperBalances(session, 222_222L, 222L);
        assertNotEquals(collectionA, collectionB);

        CollectionState reloadedA = session.open(accountA, "Account A")
            .getCollectionState().orElseThrow(AssertionError::new);
        assertEquals(collectionA, reloadedA.getCollectionId());
        assertEquals(111_111L, reloadedA.getPoints());
        assertEquals(111L, reloadedA.getShards());
        assertEquals(keys.derive(accountA), reloadedA.getCharacterKey());

        CollectionState reloadedB = session.open(accountB, "Account B")
            .getCollectionState().orElseThrow(AssertionError::new);
        assertEquals(collectionB, reloadedB.getCollectionId());
        assertEquals(222_222L, reloadedB.getPoints());
        assertEquals(222L, reloadedB.getShards());
        assertEquals(keys.derive(accountB), reloadedB.getCharacterKey());
    }

    @Test
    public void mismatchedCharacterSaveIsRejectedBeforeMigrationWrites()
        throws Exception
    {
        Path root = Files.createTempDirectory(
            "card-locked-session-mismatch-");
        CardCatalogue catalogue = MembersCatalogue.create();
        CharacterKeyDeriver keys = new CharacterKeyDeriver();
        long expectedAccount = 3030303L;
        long otherAccount = 4040404L;
        String expectedKey = keys.derive(expectedAccount);
        String otherKey = keys.derive(otherAccount);
        Path expectedDirectory = root.resolve(expectedKey);

        CollectionState foreign = new CollectionState(
            UUID.randomUUID(),
            otherKey,
            "Other Account",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-08-03T15:00:00Z"),
            5,
            Math.max(1, catalogue.getCatalogueVersion() - 1),
            3,
            0L,
            50_000L,
            0L,
            Collections.emptySet(),
            Collections.emptySet(),
            Set.of("starter.points.choice"));
        TransactionalStateStore store = new TransactionalStateStore(
            expectedDirectory,
            new SnapshotCodec());
        store.save(foreign, -1L);
        long revisionBeforeOpen = foreign.getRevision();

        CollectionSessionService session = new CollectionSessionService(
            root,
            catalogue,
            keys,
            CLOCK);
        SessionSnapshot result = session.open(
            expectedAccount,
            "Expected Account");
        assertEquals(SessionStatus.ERROR, result.getStatus());
        assertEquals(
            SessionFailureCode.LOAD_FAILED,
            result.getFailureCode().orElseThrow(AssertionError::new));
        assertFalse(result.getMessage().contains(
            "belongs to another character"));

        CollectionState persisted = new TransactionalStateStore(
            expectedDirectory,
            new SnapshotCodec()).loadHighestValid()
                .orElseThrow(AssertionError::new);
        assertEquals(revisionBeforeOpen, persisted.getRevision());
        assertEquals(otherKey, persisted.getCharacterKey());
        assertEquals(
            Math.max(1, catalogue.getCatalogueVersion() - 1),
            persisted.getCatalogueVersion());
    }

    @Test
    public void recoveredProfileSurfacesAVisibleRecoveryMessage()
        throws Exception
    {
        Path root = Files.createTempDirectory(
            "card-locked-session-recovery-message-");
        CardCatalogue catalogue = MembersCatalogue.create();
        CharacterKeyDeriver keys = new CharacterKeyDeriver();
        long account = 6060606L;
        String characterKey = keys.derive(account);
        Path directory = root.resolve(characterKey);
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState initial = new CollectionState(
            UUID.randomUUID(),
            characterKey,
            "Recovery Message",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-08-03T15:00:00Z"),
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            50_000L,
            0L,
            Collections.emptySet(),
            Collections.emptySet(),
            Set.of("starter.points.choice"));
        store.save(initial, -1L);
        CollectionState updated = initial.withProgress(
            1L,
            75_000L,
            0L,
            Collections.emptySet(),
            Collections.emptySet());
        store.save(updated, 0L);
        Files.write(
            directory.resolve("current.snapshot"),
            new byte[]{1, 2, 3});

        CollectionSessionService session = new CollectionSessionService(
            root,
            catalogue,
            keys,
            CLOCK);
        SessionSnapshot result = session.open(account, "Recovery Message");
        assertEquals(SessionStatus.READY, result.getStatus());
        assertTrue(result.getMessage().contains("local backup"));
        assertTrue(result.getMessage().contains("recovery-quarantine"));
    }

    @Test
    public void unrecoverableCommittedProfileSurfacesErrorNotSetup()
        throws Exception
    {
        Path root = Files.createTempDirectory(
            "card-locked-session-unrecoverable-");
        CardCatalogue catalogue = MembersCatalogue.create();
        CharacterKeyDeriver keys = new CharacterKeyDeriver();
        long account = 7070707L;
        String characterKey = keys.derive(account);
        Path directory = root.resolve(characterKey);
        CollectionState initial = new CollectionState(
            UUID.randomUUID(),
            characterKey,
            "Unrecoverable Profile",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-08-03T15:00:00Z"),
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            50_000L,
            0L,
            Collections.emptySet(),
            Collections.emptySet(),
            Set.of("starter.points.choice"));
        new TransactionalStateStore(directory, new SnapshotCodec())
            .save(initial, -1L);
        Files.write(
            directory.resolve("current.snapshot"),
            new byte[]{1, 2, 3});

        SessionSnapshot result = new CollectionSessionService(
            root,
            catalogue,
            keys,
            CLOCK).open(account, "Unrecoverable Profile");
        assertEquals(SessionStatus.ERROR, result.getStatus());
        assertEquals(
            SessionFailureCode.LOAD_FAILED,
            result.getFailureCode().orElseThrow(AssertionError::new));
        assertFalse(result.getMessage().contains(
            "No valid collection snapshot"));
    }

    @Test
    public void loadErrorRetainsIdentityForValidatedManualImport()
        throws Exception
    {
        Path root = Files.createTempDirectory(
            "card-locked-session-manual-recovery-");
        CardCatalogue catalogue = MembersCatalogue.create();
        CharacterKeyDeriver keys = new CharacterKeyDeriver();
        long account = 8080808L;
        String characterKey = keys.derive(account);
        Path directory = root.resolve(characterKey);
        CollectionState source = new CollectionState(
            UUID.randomUUID(),
            characterKey,
            "Manual Recovery",
            EconomyMode.STANDARD,
            IntegrityMode.INTEGRITY,
            Instant.parse("2026-08-03T15:00:00Z"),
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            12_345L,
            67L,
            Collections.emptySet(),
            Collections.emptySet(),
            Set.of(ProfileStateMarkers.INTEGRITY_ELIGIBLE));
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        store.save(source, -1L);
        Path externalBackup = root.resolve("manual.cardlocked-backup");
        store.exportCurrentSnapshot(externalBackup);
        Files.write(directory.resolve("current.snapshot"), new byte[]{9, 8, 7});

        CollectionSessionService session = new CollectionSessionService(
            root,
            catalogue,
            keys,
            CLOCK);
        assertEquals(SessionStatus.ERROR,
            session.open(account, "Manual Recovery").getStatus());

        SessionSnapshot imported = session.importBackup(externalBackup);
        assertEquals(SessionStatus.READY, imported.getStatus());
        CollectionState restored = imported.getCollectionState()
            .orElseThrow(AssertionError::new);
        assertEquals(12_345L, restored.getPoints());
        assertEquals(67L, restored.getShards());
        assertEquals(IntegrityMode.CASUAL, restored.getIntegrityMode());
        assertTrue(restored.getClaimedPointSourceIds().contains(
            ProfileStateMarkers.INTEGRITY_FORFEITED));
        assertTrue(imported.getMessage().contains("imported and validated"));
    }

    @Test
    public void closeClearsReadyStateWithoutDeletingEitherProfile()
        throws Exception
    {
        Path root = Files.createTempDirectory(
            "card-locked-session-close-");
        CardCatalogue catalogue = MembersCatalogue.create();
        CharacterKeyDeriver keys = new CharacterKeyDeriver();
        CollectionSessionService session = new CollectionSessionService(
            root,
            catalogue,
            keys,
            CLOCK);
        long account = 5050505L;
        session.open(account, "Close Test");
        UUID collectionId = session.create(options())
            .getCollectionState().orElseThrow(AssertionError::new)
            .getCollectionId();

        assertEquals(SessionStatus.LOGGED_OUT, session.close().getStatus());
        assertEquals(SessionStatus.LOGGED_OUT, session.snapshot().getStatus());
        CollectionState reloaded = session.open(account, "Close Test")
            .getCollectionState().orElseThrow(AssertionError::new);
        assertEquals(collectionId, reloaded.getCollectionId());
    }

    private ProfileSetupOptions options()
    {
        return new ProfileSetupOptions(
            EconomyMode.STANDARD,
            StarterRewardChoice.POINTS,
            RestrictionPreset.BALANCED,
            true,
            IntegrityMode.CASUAL);
    }

    private static void applyDeveloperBalances(
        CollectionSessionService session,
        long points,
        long shards)
        throws Exception
    {
        String key = com.cardrestricted.PluginBuildInfo
            .DEVELOPER_TESTING_PROPERTY;
        String previous = System.getProperty(key);
        System.setProperty(key, "true");
        try
        {
            session.applyTestingBalances(points, shards);
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
