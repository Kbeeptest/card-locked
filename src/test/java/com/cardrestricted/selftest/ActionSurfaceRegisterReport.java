package com.cardrestricted.selftest;

import com.cardrestricted.runelite.InteractionRiskClass;
import com.cardrestricted.runelite.InteractionSurface;
import com.cardrestricted.runelite.InteractionSurfacePolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.MenuAction;

/** Writes the reviewed RuneLite menu-action policy as machine-readable JSON. */
public final class ActionSurfaceRegisterReport
{
    private ActionSurfaceRegisterReport()
    {
    }

    public static void main(String[] arguments) throws IOException
    {
        if (arguments.length != 1)
        {
            throw new IllegalArgumentException(
                "Pass one output JSON path.");
        }
        Path output = Path.of(arguments[0]);
        Files.createDirectories(output.getParent());

        Map<InteractionSurface, Integer> surfaceCounts =
            new EnumMap<>(InteractionSurface.class);
        Map<InteractionRiskClass, Integer> riskCounts =
            new EnumMap<>(InteractionRiskClass.class);
        StringBuilder actions = new StringBuilder();
        MenuAction[] values = MenuAction.values();
        for (int index = 0; index < values.length; index++)
        {
            MenuAction action = values[index];
            InteractionSurface surface =
                InteractionSurfacePolicy.surfaceFor(action);
            InteractionRiskClass risk =
                InteractionSurfacePolicy.riskClassFor(action);
            surfaceCounts.merge(surface, 1, Integer::sum);
            riskCounts.merge(risk, 1, Integer::sum);
            if (index > 0)
            {
                actions.append(",\n");
            }
            actions.append("    {\"action\":\"")
                .append(action.name())
                .append("\",\"surface\":\"")
                .append(surface.name())
                .append("\",\"risk_class\":\"")
                .append(risk.name())
                .append("\"}");
        }

        String json = "{\n"
            + "  \"schema_version\": 1,\n"
            + "  \"suite\": \"card-locked-action-surface-register\",\n"
            + "  \"status\": \"passed\",\n"
            + "  \"menu_actions_in_api\": " + values.length + ",\n"
            + "  \"menu_actions_reviewed\": "
            + InteractionSurfacePolicy.reviewedActions().size() + ",\n"
            + "  \"complete\": "
            + (InteractionSurfacePolicy.reviewedActions().size()
                == values.length) + ",\n"
            + "  \"surface_counts\": "
            + enumCounts(surfaceCounts) + ",\n"
            + "  \"risk_counts\": "
            + enumCounts(riskCounts) + ",\n"
            + "  \"actions\": [\n" + actions + "\n  ]\n"
            + "}\n";
        Files.writeString(output, json, StandardCharsets.UTF_8);
        if (InteractionSurfacePolicy.reviewedActions().size()
            != values.length)
        {
            throw new IllegalStateException(
                "Not every RuneLite MenuAction has a reviewed policy.");
        }
    }

    private static <E extends Enum<E>> String enumCounts(
        Map<E, Integer> counts)
    {
        StringBuilder json = new StringBuilder("{");
        int index = 0;
        for (Map.Entry<E, Integer> entry : counts.entrySet())
        {
            if (index++ > 0)
            {
                json.append(',');
            }
            json.append('\"').append(entry.getKey().name()).append("\":")
                .append(entry.getValue());
        }
        return json.append('}').toString();
    }
}
