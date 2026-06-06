# Changelog

## [1.7.0] - Admin editing and display polish

- Added `/display nearby [radius]` to list nearby displays in the player's
  current world with distance, location, hover preview, and click-to-edit
  command suggestions.
- Added `/display clone <sourceId> <newId>` to duplicate a display's editable
  settings and lines at the executing player's current location.
- Kept `/display movehere <id>` as the safe move command for relocating saved
  displays with full location/orientation persistence.
- Added `/display audit` for read-only production maintenance reporting.
- Added `/display edit <id>`, a basic admin editor GUI with teleport,
  move-here, clone suggestion, X/Y/Z nudge, scale controls, view-range
  controls, forced refresh, and no one-click delete.
- Added per-display PlaceholderAPI refresh settings with interval, enabled,
  only-when-viewed, and viewer-range controls.
- Added `templates.yml` plus `/display template list`, `/display template save`,
  and `/display template apply` for simple reusable display templates.
- Added config defaults for nearby radius, refresh controls, and editor GUI
  names/lore/step sizes.
- Bumped the plugin version to `1.7.0`.
- Intentionally skipped optional visual commands such as billboard/shadow/
  background commands for this pass to keep the update focused and buildable.

## [1.6.2] - First public Modrinth release

- Prepared public README, license, changelog, and Modrinth listing metadata.
- Documented Paper 1.21.11, Java 21, and optional PlaceholderAPI support.
- Added safer validation for public-facing display IDs and numeric configuration values.
- Kept CozyDisplays focused on lightweight vanilla `TextDisplay` signage, floating text, wall displays, and simple hologram-style server displays.
