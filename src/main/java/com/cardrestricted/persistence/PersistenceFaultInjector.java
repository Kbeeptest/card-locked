package com.cardrestricted.persistence;

import java.io.IOException;

@FunctionalInterface
public interface PersistenceFaultInjector
{
    PersistenceFaultInjector NONE = stage -> { };

    void checkpoint(PersistenceCommitStage stage) throws IOException;
}
