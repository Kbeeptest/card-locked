package com.cardrestricted.session;

import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SessionSnapshotPrivacyTest
{
    @Test
    public void errorSnapshotNeverExposesExceptionMessage()
    {
        SessionSnapshot snapshot = SessionSnapshot.error(
            "Visible Character",
            SessionFailureCode.LOAD_FAILED,
            new IOException(
                "account=987654 C:\\Users\\Private\\current.snapshot"));

        assertEquals(SessionStatus.ERROR, snapshot.getStatus());
        assertEquals(
            SessionFailureCode.LOAD_FAILED,
            snapshot.getFailureCode().orElseThrow(AssertionError::new));
        assertEquals("IOException", snapshot.getFailureType());
        assertTrue(snapshot.getMessage().contains("CL-SESSION-001"));
        assertFalse(snapshot.getMessage().contains("987654"));
        assertFalse(snapshot.getMessage().contains("Private"));
        assertFalse(snapshot.getMessage().contains("current.snapshot"));
    }

    @Test
    public void ordinarySnapshotsDoNotCarryFailureMetadata()
    {
        SessionSnapshot loggedOut = SessionSnapshot.loggedOut();
        assertFalse(loggedOut.getFailureCode().isPresent());
        assertEquals("", loggedOut.getFailureType());
    }

    @Test
    public void everyFailureHasAStableReferenceCode()
    {
        for (SessionFailureCode code : SessionFailureCode.values())
        {
            assertTrue(code.getReferenceCode().matches("CL-[A-Z]+-[0-9]{3}"));
            assertTrue(code.getUserMessage().contains(code.getReferenceCode()));
        }
    }
}
