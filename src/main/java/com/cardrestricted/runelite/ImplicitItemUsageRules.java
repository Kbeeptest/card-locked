package com.cardrestricted.runelite;

import java.util.Locale;

/**
 * Identifies inventory/equipment items consumed or used implicitly by object,
 * NPC and production-interface actions where RuneLite does not expose an
 * explicit selected item in the menu event.
 */
public final class ImplicitItemUsageRules
{
    private ImplicitItemUsageRules()
    {
    }

    public static boolean actionCanUseImplicitItems(String option, String target)
    {
        String action = normalise(option);
        String object = normalise(target);
        return isSpellcasting(action)
            || isWoodcutting(action)
            || isMining(action)
            || isFishing(action)
            || isCooking(action, object)
            || isSmithing(action, object)
            || isFurnace(action, object)
            || isRunecrafting(action, object)
            || isFiremaking(action)
            || isFarmingToolAction(action)
            || isDigging(action)
            || isSlashObstacle(action, object)
            || isGrappleAction(action, object)
            || isUtilityToolAction(action)
            || isContainerFillAction(action)
            || isProduction(action);
    }

    public static boolean isPotentialParticipant(
        String option,
        String target,
        String itemName)
    {
        String action = normalise(option);
        String object = normalise(target);
        String item = normalise(itemName);
        if (item.isEmpty())
        {
            return false;
        }
        if (isSpellcasting(action))
        {
            return item.contains("rune pouch")
                || item.equals("rune pouch (l)")
                || item.contains("rune satchel");
        }
        if (isWoodcutting(action))
        {
            return isAxe(item);
        }
        if (isMining(action))
        {
            return isPickaxe(item);
        }
        if (isFishing(action))
        {
            return isFishingToolOrBait(item);
        }
        if (isFiremaking(action))
        {
            return item.equals("tinderbox") || isLog(item);
        }
        if (isRunecrafting(action, object))
        {
            return item.contains("essence") || item.endsWith("talisman")
                || item.endsWith("tiara");
        }
        if (isSmithing(action, object))
        {
            return item.equals("hammer") || item.endsWith(" bar")
                || item.contains("metal bar");
        }
        if (isFurnace(action, object))
        {
            return FurnaceInteractionRules.isPotentialFurnaceIngredient(item)
                || item.equals("glassblowing pipe")
                || item.equals("ammo mould")
                || item.endsWith(" mould");
        }
        if (isCooking(action, object))
        {
            return isLikelyCookable(item);
        }
        if (isFarmingToolAction(action))
        {
            return item.equals("rake") || item.equals("spade")
                || item.contains("watering can")
                || item.equals("seed dibber") || item.equals("secateurs")
                || item.equals("magic secateurs")
                || item.contains("plant cure")
                || item.contains("compost")
                || item.contains("pollen");
        }
        if (isDigging(action))
        {
            return item.equals("spade") || item.contains("trowel");
        }
        if (isSlashObstacle(action, object))
        {
            return item.contains("machete") || item.equals("knife")
                || item.endsWith(" sword") || item.endsWith(" scimitar")
                || item.endsWith(" longsword") || item.endsWith(" dagger")
                || item.endsWith(" axe");
        }
        if (isGrappleAction(action, object))
        {
            return item.contains("mith grapple")
                || item.equals("rope")
                || item.contains("crossbow");
        }
        if (isContainerFillAction(action))
        {
            return isFillableContainer(item);
        }
        if (isUtilityToolAction(action))
        {
            if (action.startsWith("pick-lock") || action.startsWith("pick lock"))
            {
                return item.equals("lockpick") || item.contains("hair clip");
            }
            if (action.startsWith("smash") || action.startsWith("finish"))
            {
                return item.contains("rock hammer") || item.equals("hammer");
            }
            if (action.startsWith("milk"))
            {
                return item.equals("bucket") || item.equals("empty bucket");
            }
            if (action.startsWith("shear"))
            {
                return item.equals("shears");
            }
            if (action.startsWith("churn"))
            {
                return item.contains("bucket of milk")
                    || item.contains("pot of cream")
                    || item.contains("pat of butter");
            }
        }
        if (isProduction(action))
        {
            return isLikelyProductionMaterial(item);
        }
        return false;
    }

    private static boolean isSpellcasting(String action)
    {
        return SpellbookWidgetRules.isSpellCastingOption(action)
            || SpellRuneRequirementResolver.isRecognisedSpellOption(action);
    }

    private static boolean isWoodcutting(String action)
    {
        return action.equals("chop") || action.startsWith("chop down")
            || action.startsWith("cut down");
    }

    private static boolean isMining(String action)
    {
        return action.equals("mine") || action.startsWith("mine ")
            || action.equals("prospect-and-mine");
    }

    private static boolean isFishing(String action)
    {
        return startsWithAny(action, "fish", "net", "bait", "lure",
            "harpoon", "cage", "small net", "big net");
    }

    private static boolean isCooking(String action, String target)
    {
        return startsWithAny(action, "cook", "bake", "roast")
            || target.contains("range") && action.equals("use")
            || target.contains("fire") && action.equals("use");
    }

    private static boolean isSmithing(String action, String target)
    {
        return startsWithAny(action, "smith", "forge")
            || target.contains("anvil") && action.equals("use");
    }

    private static boolean isFurnace(String action, String target)
    {
        return startsWithAny(action, "smelt")
            || FurnaceInteractionRules.isFurnaceInteraction(action, target);
    }

    private static boolean isRunecrafting(String action, String target)
    {
        return startsWithAny(action, "craft-rune", "craft rune", "bind")
            || (target.contains("altar") && action.equals("craft-rune"));
    }

    private static boolean isFiremaking(String action)
    {
        return startsWithAny(action, "light", "burn");
    }

    private static boolean isFarmingToolAction(String action)
    {
        return startsWithAny(action, "rake", "dig-up", "dig up", "clear",
            "water", "prune", "cure", "compost", "fertilise",
            "fertilize", "treat", "pollinate");
    }

    private static boolean isDigging(String action)
    {
        return startsWithAny(action, "dig", "excavate");
    }

    private static boolean isSlashObstacle(String action, String target)
    {
        return startsWithAny(action, "slash", "cut-through", "cut through")
            || (startsWithAny(action, "chop", "cut")
                && (target.contains("vine") || target.contains("web")
                    || target.contains("jungle")
                    || target.contains("thicket")));
    }

    private static boolean isGrappleAction(String action, String target)
    {
        return startsWithAny(action, "grapple", "fire grapple")
            || (startsWithAny(action, "cross", "climb")
                && (target.contains("grapple")
                    || target.contains("shortcut")
                    || target.contains("wall")));
    }

    private static boolean isUtilityToolAction(String action)
    {
        return startsWithAny(action, "pick-lock", "pick lock", "smash",
            "finish", "milk", "shear", "churn");
    }

    private static boolean isContainerFillAction(String action)
    {
        return startsWithAny(action, "fill", "fill-all", "fill all");
    }

    private static boolean isProduction(String action)
    {
        return InteractionIntegrityRules.isProductionOption(action);
    }

    private static boolean isAxe(String item)
    {
        return !item.contains("pickaxe")
            && (item.equals("axe") || item.endsWith(" axe")
                || item.contains("felling axe"));
    }

    private static boolean isPickaxe(String item)
    {
        return item.equals("pickaxe") || item.endsWith(" pickaxe")
            || item.contains("pickaxe");
    }

    private static boolean isFishingToolOrBait(String item)
    {
        return item.contains("fishing rod") || item.contains("fly fishing rod")
            || item.contains("harpoon") || item.contains("lobster pot")
            || item.contains("karambwan vessel") || item.endsWith(" net")
            || item.equals("small fishing net") || item.equals("big fishing net")
            || item.equals("fishing bait") || item.equals("dark fishing bait")
            || item.equals("feather") || item.equals("stripy feather")
            || item.equals("fish chunks") || item.equals("sandworms")
            || item.equals("rope") || item.equals("bailing bucket");
    }

    private static boolean isLikelyCookable(String item)
    {
        return item.startsWith("raw ") || item.startsWith("uncooked ")
            || item.contains(" dough") || item.endsWith(" dough")
            || item.equals("potato") || item.equals("seaweed")
            || item.equals("soda ash") || item.equals("swamp weed")
            || item.contains("meat") || item.contains("fish")
            || item.contains("pie shell") || item.contains("cake tin")
            || item.contains("bowl of") || item.contains("bucket of milk");
    }

    private static boolean isLog(String item)
    {
        return item.equals("logs") || item.endsWith(" logs")
            || item.contains("kindling");
    }

    private static boolean isFillableContainer(String item)
    {
        return item.equals("bucket") || item.equals("jug")
            || item.equals("bowl") || item.equals("vial")
            || item.equals("pot") || item.equals("cup")
            || item.contains("waterskin") || item.contains("water container")
            || item.startsWith("empty ")
            || item.endsWith(" bucket") || item.endsWith(" jug")
            || item.endsWith(" bowl") || item.endsWith(" vial");
    }

    private static boolean isLikelyProductionMaterial(String item)
    {
        return item.startsWith("raw ") || item.startsWith("uncut ")
            || item.endsWith(" ore") || item.endsWith(" bar")
            || isLog(item) || item.endsWith(" plank")
            || item.contains("hide") || item.contains("leather")
            || item.contains("wool") || item.equals("flax")
            || item.contains("essence") || item.contains("herb")
            || item.contains("unfinished") || item.contains("dough")
            || item.equals("soft clay") || item.equals("clay")
            || item.equals("molten glass") || item.contains("battlestaff")
            || item.equals("bow string") || item.equals("thread")
            || item.equals("needle") || item.equals("hammer")
            || item.equals("saw") || item.contains("nails")
            || item.equals("chisel") || item.equals("knife")
            || item.equals("pestle and mortar") || item.endsWith(" mould")
            || item.contains("limbs") || item.contains("stock")
            || item.contains("feather") || item.contains("dart tip")
            || item.contains("arrowtips") || item.contains("unfinished potion")
            || item.contains("vial of water") || item.contains("coconut milk")
            || item.contains("secondary ingredient") || item.contains("fabric");
    }

    private static boolean startsWithAny(String value, String... prefixes)
    {
        for (String prefix : prefixes)
        {
            if (value.equals(prefix) || value.startsWith(prefix + " ")
                || value.startsWith(prefix + "-"))
            {
                return true;
            }
        }
        return false;
    }

    private static String normalise(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replaceAll("<[^>]*>", "")
            .replace('\u00a0', ' ')
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ");
    }
}
