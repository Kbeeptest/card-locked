## 0.81.05 — RuneLite API compatibility

- Replaced terminally deprecated RuneLite `WidgetID` usage with the supported `InterfaceID` gameval API.
- No gameplay, balance, restriction-policy, artwork, pack, sound, or progression changes.

# Changelog

## 0.81.04

- Moved the reviewed Wiki artwork archive to the versioned `artwork-v1` GitHub Release asset to meet the RuneLite Plugin Hub source-archive size limit.
- Added one-time background artwork download with strict host allowlisting, bounded transfer size and SHA-256 verification before use.
- Optimised packaged card-frame PNG storage without changing their dimensions.
- No gameplay balance, progression, rarity, restriction or sound changes.

## 0.81.03

- Removed the remaining stationary frame between opening a booster and the five-card deal animation beginning.
- No balance, progression, sound, rarity, restriction or artwork changes.

## 0.81.02

- Fixed starter-pack redemption on fresh profiles after the catalogue rarity update.
- Bundled the reviewed offline collection artwork needed for a ready-to-use fresh installation.
- Moved first-run artwork preparation off the UI thread and refreshes the Album when preparation completes.

## 0.81.01

- Added beta compatibility handling for unresolved item and NPC identities on non-integrity profiles.
- Preserved manually refreshed Quest completion state until the next manual refresh or profile/session change.
- Prevented rapid Nexus exchanges from queuing repeated modal card presentations.
- Improved pack presentation responsiveness and placement-audio synchronization.
