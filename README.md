# Card Locked

Card Locked is a RuneLite plugin for a card-driven restricted-account challenge. Gameplay progression unlocks collectible cards representing OSRS items and NPCs; owned cards determine what tracked content can be used when restrictions are enabled.

> **Public beta:** Card Locked is still being validated against the full range of RuneLite/OSRS interaction metadata. Back up important profiles before testing and report any action that is incorrectly blocked or allowed.

## Core features

- A persistent collection of item and NPC cards with rarity tiers and foil variants.
- Point-based booster packs, targeted Explorer and Adventure packs, Rare+ progression and premium milestone packs.
- Nexus Shards for targeted missing-card unlocks.
- Optional gameplay restrictions for locked items and NPC interactions.
- A searchable Collection Album with card details, filters and foil-access information.
- Quest readiness tracking with **manual quest-status refresh**; background full-quest scanning is disabled.
- Character-bound local saves with transactional persistence, recovery generations and explicit backup/export tools.

## Profile setup

New profiles choose an account mode, starter reward, restriction preset, visual markers and integrity mode.

For non-integrity profiles, the setup wizard also offers **beta compatibility**. This is recommended during public beta: if RuneLite cannot provide enough metadata to verify an item or NPC action, the action may proceed instead of being blocked. Known, verified locked content is still restricted normally.

Integrity profiles always fail closed and cannot use the unverified-action compatibility escape hatch.

## Restrictions

The restriction system is intended to prevent functional use of tracked locked content while keeping safe actions such as Examine and appropriate storage/removal actions available according to the selected preset. Foil-access rewards may satisfy additional card requirements where explicitly defined.

Because OSRS and RuneLite expose many interaction paths, beta testers should use the compatibility option if they do not want an unresolved metadata edge case to temporarily block gameplay.

## Quest tab

Quest completion display is deliberately user-driven. Press **Refresh Quest Status** to take a new completion snapshot. That snapshot remains in place until the next manual refresh or until the character session changes. The default list hides completed quests; the filter can still show them when needed.

Quest reward detection is separate from the Quest-tab display refresh and continues to operate normally.

## Data and privacy

Card Locked stores progression locally under the RuneLite profile area. It does not send collection data, telemetry, diagnostics or account information to a remote service. See [PRIVACY.md](PRIVACY.md) for details.

## Building

The project targets Java 11 and RuneLite. The Plugin Hub metadata is in `runelite-plugin.properties` and uses the standard Plugin Hub build type.

## Licence

Card Locked is distributed under the BSD 2-Clause licence. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Artwork download

To stay within RuneLite Plugin Hub package limits, the reviewed Card Locked artwork bundle is hosted as the project's `artwork-v1` GitHub Release asset rather than inside the plugin source archive. On a clean installation the plugin downloads the archive once in the background, verifies its SHA-256, and caches it locally. This HTTPS request exposes the connecting IP address to GitHub but sends no RuneScape account, collection, gameplay, save, or diagnostic data.

