# Changelog

## [1.8.2] - Refresh defaults performance patch

- Disabled automatic PlaceholderAPI refresh by default for new and missing-key
  displays.
- Changed refresh configuration and saved display/template interval fields from
  seconds to minutes.
- Kept backwards-compatible reads for old seconds-based refresh keys and rounds
  them up to whole minutes at load time.
- Updated the central refresh scheduler to check at a low fixed cadence and
  skip displays with automatic refresh disabled.
- Added `/display refresh <id>` for manual one-time refresh plus
  `/display refresh enable|disable|interval|status` for per-display refresh
  management.
- Extended `/display audit` with automatic refresh, aggressive interval,
  deprecated key, and no-viewer-check counts.
- Updated bundled config defaults, templates, README, and plugin version for
  `1.8.2`.

## [1.8.1] - Display rotation controls

- Added `/display rotate <id> <yaw> [pitch]` for absolute yaw/pitch rotation.
- Added `/display rotateby <id> <yawDelta> [pitchDelta]` for relative
  yaw/pitch rotation.
- Added `/display face <id>` to match the executing player's facing direction.
- Added optional yaw/pitch auto-rotation with `/display spin <id>
  <yawPerSecond> [pitchPerSecond]` and `/display spin stop <id>`.
- Added one central auto-rotation scheduler with configurable update interval
  and max degrees-per-second clamp.
- Extended the editor GUI with rotate left/right, rotate up/down, reset
  rotation, and face-me controls.
- Extended storage, templates, clone behavior, and audit reporting for
  rotation/auto-rotation fields.
- Added config defaults for editor rotation steps and auto-rotation limits.
- Bumped the plugin version to `1.8.1`.
- Roll support was intentionally skipped for this patch to avoid a broad
  display transformation rewrite.

## [1.8.0] - Interactive display types

- Added display types: `TEXT`, `ITEM`, and `BLOCK`; existing saved displays
  without a type load as `TEXT`.
- Added `/display create text <id> <text...>`, `/display create item <id>
  <material>`, and `/display create block <id> <material>`.
- Kept `/display create <id> <text...>` as a backwards-compatible text display
  alias.
- Added `/display setitem <id> <material>` and `/display setblock <id>
  <material>`.
- Updated spawn/despawn/reload lifecycle to handle `TextDisplay`,
  `ItemDisplay`, `BlockDisplay`, and optional `Interaction` hitboxes.
- Extended clone, nearby, audit, info, templates, and the editor GUI for
  type-aware display data.
- Added optional clickable actions with `/display interaction ...` commands.
  Actions are disabled by default and support `player:`, `console:`, and
  `message:` formats with per-player cooldowns.
- Added config defaults for interaction hitbox size, cooldowns, and editor
  type/material buttons.
- Bumped the plugin version to `1.8.0`.

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
