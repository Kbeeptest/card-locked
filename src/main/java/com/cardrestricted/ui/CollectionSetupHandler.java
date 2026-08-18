package com.cardrestricted.ui;

import com.cardrestricted.collection.ProfileSetupOptions;
import java.nio.file.Path;

public interface CollectionSetupHandler
{
    void createCollection(ProfileSetupOptions options);

    void disableIntegrity();

    void resetProfile();

    default void exportBackup(Path destination)
    {
    }

    default void importBackup(Path source)
    {
    }

    default void restorePreviousBackup()
    {
    }

    void exportDiagnostics();
}
