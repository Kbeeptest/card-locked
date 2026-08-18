package com.cardrestricted;

import com.cardrestricted.domain.RestrictionMode;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(CardRestrictedAccountConfig.GROUP)
public interface CardRestrictedAccountConfig extends Config
{
    String GROUP = "cardrestrictedaccount";

    @ConfigSection(
        name = "Core restrictions",
        description = "The small set of gameplay rules used by the stable runtime.",
        position = 0)
    String restrictionSection = "restrictions";

    @ConfigSection(
        name = "Presentation",
        description = "Visual and audio presentation settings.",
        position = 1)
    String presentationSection = "presentation";

    @ConfigItem(
        keyName = "restrictionMode",
        name = "Restriction mode",
        description = "Enforce restrictions, observe without blocking, or disable them.",
        position = 0,
        section = restrictionSection)
    default RestrictionMode restrictionMode()
    {
        return RestrictionMode.ENFORCE;
    }

    @ConfigItem(
        keyName = "restrictLockedItems",
        name = "Restrict locked items",
        description = "Blocks functional use of a tracked item until its card family is owned.",
        position = 1,
        section = restrictionSection)
    default boolean restrictLockedItems()
    {
        return true;
    }

    @ConfigItem(
        keyName = "allowLockedItemBanking",
        name = "Allow banking locked items",
        description = "Allows Deposit, Withdraw, Store and Remove-from-storage actions for locked items.",
        position = 2,
        section = restrictionSection)
    default boolean allowLockedItemBanking()
    {
        return true;
    }

    @ConfigItem(
        keyName = "allowUnverifiedItemActions",
        name = "Allow unverified item actions",
        description = "Beta compatibility escape hatch for non-integrity profiles when an item's identity cannot be resolved. Known locked items remain restricted.",
        position = 3,
        section = restrictionSection)
    default boolean allowUnverifiedItemActions()
    {
        return false;
    }

    @ConfigItem(
        keyName = "allowUnverifiedNpcActions",
        name = "Allow unverified NPC actions",
        description = "Beta compatibility escape hatch for non-integrity profiles when an NPC action loses its exact identity. Known verified locked NPCs remain restricted.",
        position = 4,
        section = restrictionSection)
    default boolean allowUnverifiedNpcActions()
    {
        return false;
    }

    @ConfigItem(
        keyName = "restrictNpcCombat",
        name = "Restrict locked NPC actions",
        description = "Allows only Talk-to until the corresponding NPC card family is owned.",
        position = 5,
        section = restrictionSection)
    default boolean restrictNpcCombat()
    {
        return true;
    }

    @ConfigItem(
        keyName = "blockedChatMessages",
        name = "Blocked-action messages",
        description = "Explains blocked item and NPC actions in game chat.",
        position = 6,
        section = restrictionSection)
    default boolean blockedChatMessages()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showLockedItemOverlay",
        name = "Mark locked items",
        description = "Greys tracked locked items and displays a centred red cross.",
        position = 0,
        section = presentationSection)
    default boolean showLockedItemOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showLockedNpcOverlay",
        name = "Outline locked NPCs",
        description = "Draws a muted outline around tracked NPCs whose cards are not owned.",
        position = 1,
        section = presentationSection)
    default boolean showLockedNpcOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showLockedNpcLabels",
        name = "Label locked NPCs",
        description = "Displays LOCKED above outlined NPCs.",
        position = 2,
        section = presentationSection)
    default boolean showLockedNpcLabels()
    {
        return false;
    }

    @ConfigItem(
        keyName = "reducedPackMotion",
        name = "Reduced pack motion",
        description = "Uses shorter pack-opening transitions.",
        position = 3,
        section = presentationSection)
    default boolean reducedPackMotion()
    {
        return false;
    }

    @ConfigItem(
        keyName = "packRevealSounds",
        name = "Pack reveal sounds",
        description = "Plays deal and rarity cues during pack opening.",
        position = 4,
        section = presentationSection)
    default boolean packRevealSounds()
    {
        return true;
    }

    @ConfigItem(
        keyName = "achievementNotifications",
        name = "Collection goal messages",
        description = "Shows a game message when a collection goal is completed.",
        position = 5,
        section = presentationSection)
    default boolean achievementNotifications()
    {
        return true;
    }

    @ConfigItem(
        keyName = "achievementToasts",
        name = "Collection goal overlays",
        description = "Shows a non-blocking milestone overlay.",
        position = 6,
        section = presentationSection)
    default boolean achievementToasts()
    {
        return true;
    }

}
