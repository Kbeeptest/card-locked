# Privacy

Card Locked is a local RuneLite plugin. It has no telemetry, remote analytics, cloud-save or account-linking service.

## Data stored locally

The plugin stores collection progress, points, shards, pack state, milestone and reward claims, rule markers, transaction journals, recovery snapshots, migration backups and a bounded technical interaction trace under the local RuneLite profile area.

Save exports are created only after an explicit user action at a path selected by the user. A save backup contains the selected character's local collection snapshot, including its display name and collection state. Card Locked does not transmit that backup.

Persistence coordination may create a small sibling transaction-lock file. It contains no account, collection or gameplay data; it only coordinates local writers targeting the same profile.

## Diagnostics

The bounded integrity trace records technical metadata such as interaction surface, policy result, reason code and interface provenance. It excludes free-form chat, account identifiers, collection contents and arbitrary filesystem paths. Diagnostic exports are created only through an explicit local action and are not transmitted.

## Network boundary

The runtime does not transmit collection state, saves, traces, diagnostics, account identifiers or telemetry. Optional Wiki navigation opens the user's browser only after a deliberate button action.

## Artwork

The standard distribution includes a reviewed offline artwork archive so the collection is usable on a fresh installation without a gameplay-time image download. Card Locked verifies and prepares that archive locally in the RuneLite profile area. Some item cards intentionally use RuneLite-provided item sprites, and a small number of NPC cards use deterministic built-in fallbacks.

## Recovery and deletion

The local state uses a current snapshot, bounded automatic recovery generations, a journal and quarantined originals retained for diagnosis. Legacy-path migration creates a verified local backup before moving data. Manual import or restoration is explicit, validated and permanently disables integrity for the recovered profile.

Resetting a profile removes its collection-state directory. User-selected exported backups and the empty sibling transaction-lock file are outside that deletion target and may remain.

Any future remote request, telemetry, cloud storage or external-account integration requires an updated disclosure before distribution.
