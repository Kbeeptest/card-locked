Card Locked OSRS Wiki artwork pack v1

The active catalogue currently has near-complete reviewed artwork coverage:
- 7,144 cards use OSRS Wiki artwork mappings.
- 439 item cards use RuneLite item sprites as the reviewed fallback.
- 5 NPC cards use deterministic built-in catalogue fallback artwork.
- 6,667 unique PNG files are stored in the versioned artwork-v1 release asset.

On a clean installation Card Locked downloads the artwork-v1 archive once from
the project's GitHub Release on a background worker. The archive is SHA-256
verified before use and cached locally under RuneLite's data directory.
Subsequent launches reuse the verified cache. Individual images are read lazily
from the archive and the in-memory rendered image cache remains bounded.

The manifest retains Wiki page/file provenance, source URL, source hash,
normalised runtime hash and identity-match method for mapped artwork.
