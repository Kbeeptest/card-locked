Card Locked offline OSRS Wiki artwork pack v1

The active catalogue currently has near-complete packaged image coverage:
- 7,144 cards use packaged OSRS Wiki artwork mappings.
- 438 item cards use their RuneLite item sprites as the reviewed fallback.
- 5 NPC cards use deterministic built-in catalogue fallback artwork.
- 6,667 unique PNG files are stored once in offline-assets-v1.zip.

The Phase 0.77.4 acquisition pass safely imported 715 exact display-name
matches from the user-supplied OSRS TCG offline Wiki-image cache. Original
OSRS Wiki source URLs remain recorded in manifest.tsv. No fuzzy or ambiguous
name match was accepted.

The ZIP is SHA-256 verified, opened as a random-access archive and read only for
cards currently being rendered. Individual images are not extracted during
project unpacking and the in-memory rendered image cache is bounded to 512.
Gameplay-time Wiki downloads are disabled.

Every artwork row retains its Wiki page, file title, source URL, revision,
source hash, normalised runtime hash and identity-match method in manifest.tsv.

See:
- docs/catalogue/TEST_ALBUM_ARTWORK_COMPLETENESS.tsv
- docs/catalogue/WIKI_CARD_CONTENT_RESOLUTION.tsv
- docs/OSRS_WIKI_ARTWORK_ATTRIBUTION.md
