# Privacy

Card Locked is a local RuneLite plugin. It has no telemetry, remote analytics, cloud-save or account-linking service.

## Data stored locally

The plugin stores collection progress, points, shards, pack state, milestone and reward claims, rule markers, transaction journals, recovery snapshots, migration backups and a bounded technical interaction trace under the local RuneLite profile area.

Save exports are created only after an explicit user action at a path selected by the user. A save backup contains the selected character's local collection snapshot, including its display name and collection state. Card Locked does not transmit that backup.

Persistence coordination may create a small sibling transaction-lock file. It contains no account, collection or gameplay data; it only coordinates local writers targeting the same profile.

## Diagnostics

The bounded integrity trace records technical metadata such as interaction surface, policy result, reason code and interface provenance. It excludes free-form chat, account identifiers, collection contents and arbitrary filesystem paths. Diagnostic exports are created only through an explicit local action and are not transmitted.

## Network boundary

The runtime does not transmit collection state, saves, traces, diagnostics, RuneScape account identifiers or telemetry. Optional Wiki navigation opens the user's browser only after a deliberate button action.

On first use, if the verified artwork archive is not already cached locally, Card Locked makes an HTTPS request to the project's GitHub Release to download `card-locked-artwork-v1.zip`. As with any web request, GitHub receives the connecting IP address and standard HTTP request metadata. Card Locked sends no RuneScape account name, collection state, gameplay data, save data or diagnostic data with this request.

## Artwork

The reviewed artwork archive is distributed as the `artwork-v1` GitHub Release asset so the Plugin Hub source package remains within RuneLite's size limit. Card Locked downloads it once on a background worker, verifies its packaged SHA-256 before use, and caches it under RuneLite's local data directory. Subsequent launches reuse the verified local archive. Some item cards intentionally use RuneLite-provided item sprites, and a small number of NPC cards use deterministic built-in fallbacks.

## Recovery and deletion

The local state uses a current snapshot, bounded automatic recovery generations, a journal and quarantined originals retained for diagnosis. Legacy-path migration creates a verified local backup before moving data. Manual import or restoration is explicit, validated and permanently disables integrity for the recovered profile.

Resetting a profile removes its collection-state directory. User-selected exported backups and the empty sibling transaction-lock file are outside that deletion target and may remain.

Any future remote request, telemetry, cloud storage or external-account integration requires an updated disclosure before distribution.
