# Card Locked third-party notices

This notice accompanies Card Locked 0.81.03 and records third-party attribution and content provenance.

## Card Locked source

Card Locked source is distributed under the BSD 2-Clause licence in `LICENSE`.

## RuneLite

Card Locked compiles against the RuneLite client API. RuneLite is a separate project with its own copyright and licence terms. The standard Card Locked JAR does not bundle RuneLite client dependencies. Development dependency bundles are not public plugin artifacts.

Project reference: https://github.com/runelite/runelite

## Old School RuneScape, Jagex and OSRS Wiki media

Old School RuneScape, RuneScape, associated names and underlying game media are owned by or licensed to Jagex Ltd. Card Locked is an unofficial community project and is not endorsed by or affiliated with Jagex.

This content is not endorsed by or affiliated with Jagex.

Card Locked 0.81.03 includes a reviewed offline artwork archive used by the card collection. The archive contains 6,667 unique image assets referenced by 7,144 current provenance mappings. Card Locked performs no gameplay-time network download for these packaged mappings. A further 439 item cards use RuneLite item sprites as their intended fallback and five NPC cards use deterministic built-in fallback artwork.

The included manifest records the associated OSRS Wiki reference, acquisition provenance and cryptographic digests for mapped assets. Underlying OSRS/Jagex media rights remain with their respective rights holders. Inclusion in Card Locked does not transfer ownership or imply endorsement.

## Acquisition provenance

A prior acquisition pass used a user-supplied OSRS TCG offline cache and metadata as an exact-name lookup reference for a subset of OSRS Wiki image files. No fuzzy identity match was accepted. The referenced OSRS TCG source project was recorded as BSD 2-Clause licensed; referenced image files remain underlying OSRS Wiki/Jagex media.

## Test-only dependencies

JUnit 4.13.2 and Hamcrest are used by automated tests and are not included in the standard plugin JAR. Their own licence terms apply.

