package com.cardrestricted.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CharacterKeyDeriver
{
    private static final byte[] NAMESPACE =
        "card-restricted-account:v1".getBytes(StandardCharsets.UTF_8);

    public String derive(long accountHash)
    {
        if (accountHash == 0L || accountHash == -1L)
        {
            throw new IllegalArgumentException(
                "A stable account hash is required before creating a collection.");
        }

        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(NAMESPACE);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(accountHash).array());
            return toHex(digest.digest());
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String toHex(byte[] bytes)
    {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
        {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }
}
