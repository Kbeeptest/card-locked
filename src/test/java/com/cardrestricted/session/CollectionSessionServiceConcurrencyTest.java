package com.cardrestricted.session;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.identity.CharacterKeyDeriver;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CollectionSessionServiceConcurrencyTest
{
    @Test
    public void snapshotReadNeverWaitsForMutationMonitor() throws Exception
    {
        TemporaryFolder temporaryFolder = new TemporaryFolder();
        temporaryFolder.create();
        Path root = temporaryFolder.newFolder("characters").toPath();
        CardCatalogue catalogue = MembersCatalogue.create();
        CollectionSessionService service = new CollectionSessionService(
            root,
            catalogue,
            new CharacterKeyDeriver(),
            Clock.systemUTC());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch monitorHeld = new CountDownLatch(1);
        CountDownLatch releaseMonitor = new CountDownLatch(1);
        try
        {
            Future<?> holder = workers.submit(() -> {
                synchronized (service)
                {
                    monitorHeld.countDown();
                    try
                    {
                        releaseMonitor.await(2, TimeUnit.SECONDS);
                    }
                    catch (InterruptedException exception)
                    {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            assertTrue(monitorHeld.await(1, TimeUnit.SECONDS));

            Future<SessionSnapshot> read = workers.submit(service::snapshot);
            assertEquals(
                SessionStatus.LOGGED_OUT,
                read.get(150, TimeUnit.MILLISECONDS).getStatus());

            releaseMonitor.countDown();
            holder.get(1, TimeUnit.SECONDS);
        }
        finally
        {
            releaseMonitor.countDown();
            workers.shutdownNow();
            workers.awaitTermination(1, TimeUnit.SECONDS);
            temporaryFolder.delete();
        }
    }
}
