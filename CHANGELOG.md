# Changelog

## [1.8.5] - Display command action audit logging

- Added concise audit logging for clickable display `player:` and `console:`
  command actions, including display id, player name/UUID, executor type,
  rendered command, and dispatch result.
- Added warning logs for command action dispatch failures and runtime
  exceptions without changing command execution behavior, message actions,
  cooldowns, placeholders, or stored display data.
- Bumped the plugin version to `1.8.5`.

## [1.8.4] - Text panel styling controls

- Added per-text-display render modes: `LINE_ENTITIES` keeps the existing one
  TextDisplay-per-line behavior, while `SINGLE_ENTITY` renders saved lines as
  one newline-joined TextDisplay for a shared background panel look.
- Added persistent text panel fields for background color and 0-100%
  background opacity while preserving the existing background enabled flag.
- Added text styling commands for render mode, background toggle, background
  color, background opacity, alignment, line spacing, shadow, see-through, and
  billboard mode.
- Extended `/display edit <id>` for text displays with render mode,
  background, opacity, alignment, line spacing, and background color controls.
- Extended clone behavior and templates to preserve render mode, background
  color, and background opacity.
- Extended `/display audit` with text render mode, background-enabled, and
  invalid panel setting counts.
- Added `text-defaults` config keys for new text displays only. Existing saved
  displays still default to the old line-entity layout when the new keys are
  missing.
- Bumped the plugin version to `1.8.4`.

## [1.8.3] - Reload entity duplication hotfix

- Made `/display reload` stop scheduled placeholder/rotation work before
  closing editors, removing plugin-owned entities, reloading config/storage,
  and spawning saved displays once.
- Added role-aware PersistentDataContainer tags for spawned visual display
  entities and interaction hitboxes, plus compatibility scoreboard tags for
  future cleanup/auditing.
- Made every single-display spawn replace any already loaded CozyDisplays-owned
  visual or interaction entity for the same display id before spawning fresh.
- Kept cleanup scoped to CozyDisplays-owned `TextDisplay`, `ItemDisplay`,
  `BlockDisplay`, and `Interaction` entities.
- Extended `/display audit` with read-only loaded-world visual, interaction,
  duplicate, and orphan entity counts.
- Did not add `/display cleanup`; `/display reload` now performs the safe
  cleanup path for owned duplicate/orphan entities.
- Bumped the plugin version to `1.8.3`.

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
