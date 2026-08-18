package com.cardrestricted.persistence;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PluginPathsMigrationTest
{
    @Test
    public void legacyCharactersMergeWithoutOverwritingCurrentData()
        throws Exception
    {
        Path runeLite = Files.createTempDirectory(
            "card-locked-path-migration-");
        Path currentCharacter = runeLite.resolve("card-locked")
            .resolve("characters").resolve("character-a");
        Path legacyCharacter = runeLite.resolve("card-restricted-account")
            .resolve("characters").resolve("character-a");
        Files.createDirectories(currentCharacter);
        Files.createDirectories(legacyCharacter);
        Files.writeString(
            currentCharacter.resolve("current.snapshot"),
            "current-data",
            StandardCharsets.UTF_8);
        Files.writeString(
            legacyCharacter.resolve("current.snapshot"),
            "legacy-conflict",
            StandardCharsets.UTF_8);
        Files.writeString(
            legacyCharacter.resolve("previous.snapshot"),
            "legacy-unique",
            StandardCharsets.UTF_8);

        Path staleWiki = runeLite.resolve("card-locked")
            .resolve("artwork").resolve("wiki-v1");
        Path staleNpc = runeLite.resolve("card-locked")
            .resolve("npc-artwork-cache");
        Files.createDirectories(staleWiki);
        Files.createDirectories(staleNpc);
        Files.writeString(staleWiki.resolve("old.png"), "old");
        Files.writeString(staleNpc.resolve("old.png"), "old");

        PluginPaths paths = new PluginPaths(runeLite);
        paths.prepareAndMigrate();

        assertEquals("current-data", Files.readString(
            currentCharacter.resolve("current.snapshot"),
            StandardCharsets.UTF_8));
        assertEquals("legacy-unique", Files.readString(
            currentCharacter.resolve("previous.snapshot"),
            StandardCharsets.UTF_8));
        assertTrue(Files.exists(
            legacyCharacter.resolve("current.snapshot")));
        assertEquals("legacy-conflict", Files.readString(
            paths.migrationBackupsDirectory()
                .resolve("legacy-characters-v1/character-a/current.snapshot"),
            StandardCharsets.UTF_8));
        assertEquals("legacy-unique", Files.readString(
            paths.migrationBackupsDirectory()
                .resolve("legacy-characters-v1/character-a/previous.snapshot"),
            StandardCharsets.UTF_8));
        assertFalse(Files.exists(
            legacyCharacter.resolve("previous.snapshot")));
        assertFalse(Files.exists(staleWiki));
        assertFalse(Files.exists(staleNpc));
        assertTrue(Files.isDirectory(paths.wikiArtworkDirectory()));
    }

    @Test
    public void cleanLegacyTreeIsRemovedAfterSuccessfulMove()
        throws Exception
    {
        Path runeLite = Files.createTempDirectory(
            "card-locked-path-clean-migration-");
        Path legacyCharacter = runeLite.resolve("card-restricted-account")
            .resolve("characters").resolve("character-b");
        Files.createDirectories(legacyCharacter);
        Files.writeString(
            legacyCharacter.resolve("current.snapshot"),
            "legacy-only",
            StandardCharsets.UTF_8);

        PluginPaths paths = new PluginPaths(runeLite);
        paths.prepareAndMigrate();

        assertEquals("legacy-only", Files.readString(
            paths.charactersDirectory().resolve("character-b")
                .resolve("current.snapshot"),
            StandardCharsets.UTF_8));
        assertEquals("legacy-only", Files.readString(
            paths.migrationBackupsDirectory()
                .resolve("legacy-characters-v1/character-b/current.snapshot"),
            StandardCharsets.UTF_8));
        assertFalse(Files.exists(
            runeLite.resolve("card-restricted-account")));
    }
}
