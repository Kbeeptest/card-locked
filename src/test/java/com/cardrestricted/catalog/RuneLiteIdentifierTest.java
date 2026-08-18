package com.cardrestricted.catalog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class RuneLiteIdentifierTest
{
    private static final String MANIFEST =
        "com/cardrestricted/catalog/f2p/runelite-identifiers.tsv";

    @Test
    public void pinnedIdentifiersMatchRuneLiteAndCatalogue()
        throws Exception
    {
        InputStream input = getClass().getClassLoader()
            .getResourceAsStream(MANIFEST);
        assertNotNull("Missing RuneLite identifier manifest.", input);
        CardCatalogue catalogue = F2pPrototypeCatalogue.create();
        int verified = 0;

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(input, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.startsWith("api_class\t")
                    || line.trim().isEmpty())
                {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                assertEquals(
                    "Malformed identifier manifest row: " + line,
                    4,
                    columns.length);
                Class<?> apiClass = Class.forName(columns[0]);
                Field field = apiClass.getField(columns[1]);
                int expectedId = Integer.parseInt(columns[2]);
                int actualId = field.getInt(null);
                assertEquals(
                    columns[0] + "." + columns[1]
                        + " changed RuneLite ID.",
                    expectedId,
                    actualId);
                assertTrue(
                    columns[3] + " does not contain " + expectedId + ".",
                    catalogue.requireFamily(columns[3])
                        .allEntityIds().contains(expectedId));
                verified++;
            }
        }

        assertEquals("Unexpected pinned identifier count.", 230, verified);
    }
}
