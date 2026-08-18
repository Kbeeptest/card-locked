package com.cardrestricted.persistence;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.collection.ProfileStateMarkers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class ProfileBackupRecoveryTest
{
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void exportIsAtomicVerifiedAndNonOverwriting() throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path profile = temporaryFolder.newFolder("profile").toPath();
        TransactionalStateStore store = store(profile);
        CollectionState state = PersistenceTestFixtures.state(
            catalogue,
            "character-export",
            4_200L);
        store.save(state, -1L);

        Path destination = temporaryFolder.getRoot().toPath()
            .resolve("export.cardlocked-backup");
        assertEquals(destination.toAbsolutePath(),
            store.exportCurrentSnapshot(destination));
        assertArrayEquals(
            new SnapshotCodec().encode(state),
            Files.readAllBytes(destination));

        try
        {
            store.exportCurrentSnapshot(destination);
            fail("Existing backups must not be overwritten implicitly.");
        }
        catch (IOException expected)
        {
            assertTrue(expected.getMessage().contains("already exists"));
        }
    }

    @Test
    public void importRejectsCorruptAndCrossCharacterSnapshots()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path profile = temporaryFolder.newFolder("profile-import-reject")
            .toPath();
        TransactionalStateStore store = store(profile);
        CollectionState current = PersistenceTestFixtures.state(
            catalogue,
            "character-a",
            1_000L);
        store.save(current, -1L);

        Path corrupt = temporaryFolder.getRoot().toPath().resolve("bad.backup");
        Files.write(corrupt, "not a snapshot".getBytes(StandardCharsets.UTF_8));
        assertImportRejected(store, corrupt, "character-a",
            catalogue.getCatalogueVersion());

        CollectionState otherCharacter = PersistenceTestFixtures.state(
            catalogue,
            "character-b",
            9_999L);
        Path wrongIdentity = temporaryFolder.getRoot().toPath()
            .resolve("wrong-character.backup");
        Files.write(wrongIdentity, new SnapshotCodec().encode(otherCharacter));
        assertImportRejected(store, wrongIdentity, "character-a",
            catalogue.getCatalogueVersion());

        assertEquals(current.getRevision(),
            store.loadHighestValid().orElseThrow().getRevision());
        assertEquals(current.getPoints(),
            store.loadHighestValid().orElseThrow().getPoints());
    }

    @Test
    public void importRejectsDifferentCollectionLineage() throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path profile = temporaryFolder.newFolder("profile-lineage").toPath();
        TransactionalStateStore store = store(profile);
        CollectionState current = PersistenceTestFixtures.state(
            catalogue,
            "same-character",
            1_000L);
        store.save(current, -1L);
        CollectionState otherLineage = PersistenceTestFixtures.state(
            catalogue,
            "same-character",
            2_000L);
        Path source = temporaryFolder.getRoot().toPath()
            .resolve("other-lineage.backup");
        Files.write(source, new SnapshotCodec().encode(otherLineage));

        assertImportRejected(store, source, "same-character",
            catalogue.getCatalogueVersion());
        assertEquals(current.getCollectionId(),
            store.loadHighestValid().orElseThrow().getCollectionId());
    }

    @Test
    public void importRejectsSnapshotFromNewerCatalogue() throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path profile = temporaryFolder.newFolder("profile-future-catalogue")
            .toPath();
        TransactionalStateStore store = store(profile);
        CollectionState current = PersistenceTestFixtures.state(
            catalogue,
            "character-future",
            1_000L);
        store.save(current, -1L);

        CollectionState future = withCatalogueVersion(
            current,
            catalogue.getCatalogueVersion() + 1);
        Path source = temporaryFolder.getRoot().toPath()
            .resolve("future-catalogue.backup");
        Files.write(source, new SnapshotCodec().encode(future));

        assertImportRejected(store, source, current.getCharacterKey(),
            catalogue.getCatalogueVersion());
        assertEquals(current.getRevision(),
            store.loadHighestValid().orElseThrow().getRevision());
    }

    @Test
    public void manualImportForfeitsIntegrityAndPreservesClaimHistory()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path profile = temporaryFolder.newFolder("profile-manual-import")
            .toPath();
        TransactionalStateStore store = store(profile);
        CollectionState original = PersistenceTestFixtures.state(
            catalogue,
            "character-manual",
            1_000L);
        store.save(original, -1L);
        Path backup = temporaryFolder.getRoot().toPath()
            .resolve("manual.backup");
        store.exportCurrentSnapshot(backup);

        CollectionState withWatermark = original.withNoncombatXpProcessed(
            "WOODCUTTING",
            20_000L,
            0L,
            0L);
        store.save(withWatermark, original.getRevision());
        CollectionState newer = withWatermark.withPointsAwarded(
            "reward.after.backup",
            500L);
        store.save(newer, withWatermark.getRevision());
        CollectionState restored = store.importSnapshot(
            backup,
            original.getCharacterKey(),
            catalogue.getCatalogueVersion(),
            ProfileStateMarkers.INTEGRITY_FORFEITED);

        assertEquals(newer.getRevision() + 1L, restored.getRevision());
        assertEquals(original.getPoints(), restored.getPoints());
        assertEquals(com.cardrestricted.domain.IntegrityMode.CASUAL,
            restored.getIntegrityMode());
        assertTrue(restored.getClaimedPointSourceIds().contains(
            "reward.after.backup"));
        assertTrue(restored.getClaimedPointSourceIds().contains(
            ProfileStateMarkers.INTEGRITY_FORFEITED));
        assertEquals(Long.valueOf(20_000L),
            restored.getNoncombatXpWatermarks().get("WOODCUTTING"));
        assertFalse(ProfileStateMarkers.isIntegrityProfile(restored));
    }

    @Test
    public void previousAutomaticBackupRestoresAsNewRevision()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path profile = temporaryFolder.newFolder("profile-auto-restore")
            .toPath();
        TransactionalStateStore store = store(profile);
        CollectionState original = PersistenceTestFixtures.state(
            catalogue,
            "character-automatic",
            2_000L);
        store.save(original, -1L);
        CollectionState newer = original.withPointsAwarded(
            "newer.claim",
            750L);
        store.save(newer, original.getRevision());

        CollectionState restored = store.restorePreviousSnapshot(
            catalogue.getCatalogueVersion(),
            ProfileStateMarkers.INTEGRITY_FORFEITED);

        assertEquals(2L, restored.getRevision());
        assertEquals(original.getPoints(), restored.getPoints());
        assertTrue(restored.getClaimedPointSourceIds().contains(
            "newer.claim"));
        assertTrue(restored.getClaimedPointSourceIds().contains(
            ProfileStateMarkers.INTEGRITY_FORFEITED));
        assertEquals(restored.getRevision(),
            store.loadHighestValid().orElseThrow().getRevision());
    }

    @Test
    public void emptyProfileCanInstallValidatedBackupBaseline()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        CollectionState sourceState = PersistenceTestFixtures.state(
            catalogue,
            "character-empty",
            8_000L);
        Path source = temporaryFolder.getRoot().toPath()
            .resolve("empty-import.backup");
        Files.write(source, new SnapshotCodec().encode(sourceState));
        TransactionalStateStore emptyStore = store(
            temporaryFolder.newFolder("empty-profile").toPath());

        CollectionState imported = emptyStore.importSnapshot(
            source,
            sourceState.getCharacterKey(),
            catalogue.getCatalogueVersion(),
            ProfileStateMarkers.INTEGRITY_FORFEITED);

        assertEquals(0L, imported.getRevision());
        assertEquals(8_000L, imported.getPoints());
        assertEquals(com.cardrestricted.domain.IntegrityMode.CASUAL,
            imported.getIntegrityMode());
        assertTrue(imported.getClaimedPointSourceIds().contains(
            ProfileStateMarkers.INTEGRITY_FORFEITED));
        assertEquals(imported.getCollectionId(),
            emptyStore.loadHighestValid().orElseThrow().getCollectionId());
    }

    private static TransactionalStateStore store(Path directory)
    {
        return new TransactionalStateStore(directory, new SnapshotCodec());
    }

    private static CollectionState withCatalogueVersion(
        CollectionState state,
        int catalogueVersion)
    {
        return new CollectionState(
            state.getCollectionId(),
            state.getCharacterKey(),
            state.getDisplayName(),
            state.getEconomyMode(),
            state.getIntegrityMode(),
            state.getCreatedAt(),
            state.getSchemaVersion(),
            catalogueVersion,
            state.getRuleSetVersion(),
            state.getRevision(),
            state.getPoints(),
            state.getShards(),
            state.getOwnedCardIds(),
            state.getFoilCardIds(),
            state.getClaimedPointSourceIds(),
            state.getNoncombatRewardRemainderUnits(),
            state.getNoncombatXpWatermarks(),
            state.getPendingPackReveal().orElse(null));
    }

    private static void assertImportRejected(
        TransactionalStateStore store,
        Path source,
        String characterKey,
        int maximumCatalogueVersion)
        throws Exception
    {
        try
        {
            store.importSnapshot(
                source,
                characterKey,
                maximumCatalogueVersion,
                ProfileStateMarkers.INTEGRITY_FORFEITED);
            fail("Invalid backup import must fail.");
        }
        catch (IOException expected)
        {
            // Expected: the user-facing layer supplies a stable recovery code.
        }
    }
}
