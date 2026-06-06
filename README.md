# CozyDisplays

CozyDisplays is a lightweight Paper plugin for creating and managing vanilla
display entities. It supports fixed text displays, item displays, block
displays, and optional admin-configured click actions without the weight of a
larger hologram suite.

## Features

- Create multi-line vanilla `TextDisplay` signage in-game.
- Create vanilla `ItemDisplay` and `BlockDisplay` entries.
- Move, nudge, clone, hide, show, scale, and delete displays with admin commands.
- Add optional clickable actions with safe `Interaction` hitboxes.
- Snap displays to vertical wall faces for clean signage.
- Find nearby displays and run read-only display audits for production maintenance.
- Use a basic admin editor GUI for safe common actions.
- Tune per-display render/view range.
- Tune per-display PlaceholderAPI refresh behavior.
- Save and apply simple display templates.
- Persist displays in `plugins/CozyDisplays/displays.yml`.
- Optional PlaceholderAPI support for global placeholders.
- Legacy `&` color code support for display text.

## Compatibility

- Server software: Paper
- Target Minecraft version: 1.21.11
- Java: 21
- Optional dependency: PlaceholderAPI

CozyDisplays uses Paper API and vanilla display entities. It is not intended for
Bukkit/Spigot-only servers.

## Installation

1. Download the CozyDisplays jar.
2. Stop your Paper server.
3. Place the jar in your server's `plugins/` folder.
4. Install PlaceholderAPI only if you want placeholder support.
5. Start the server.
6. Edit `plugins/CozyDisplays/config.yml` if needed, then run `/display reload`.

Existing config files are not overwritten on startup.

## Quick Start

Create a display at your location:

```text
/display create welcome &6Welcome to spawn
```

Create item and block displays:

```text
/display create item diamond_icon DIAMOND
/display create block beacon_block BEACON
```

Add another line:

```text
/display addline welcome &fRead the rules before building.
```

Move it to the wall you are looking at:

```text
/display snapwall welcome
```

Fine tune its position:

```text
/display up welcome 0.05
/display left welcome 0.05
```

Find and edit nearby displays:

```text
/display nearby
/display edit welcome
```

## Commands

| Command | Description |
| --- | --- |
| `/display create <id> <text...>` | Backwards-compatible text display create command. |
| `/display create text <id> <text...>` | Create a text display at your location. |
| `/display create item <id> <material>` | Create an item display at your location. |
| `/display create block <id> <material>` | Create a block display at your location. |
| `/display addline <id> <text...>` | Add a line to a display. |
| `/display setline <id> <lineNumber> <text...>` | Replace one line. |
| `/display removeline <id> <lineNumber>` | Remove one line. |
| `/display movehere <id>` | Move a display to your location. |
| `/display nearby [radius]` | List nearby displays in your current world. Entries suggest `/display edit <id>` when clicked. |
| `/display clone <sourceId> <newId>` | Copy a display's editable settings and lines to a new display at your location. |
| `/display audit` | Show a read-only maintenance report for saved, spawned, missing-world, missing-entity, and invalid displays. |
| `/display edit <id>` | Open the basic admin editor GUI. |
| `/display snapwall <id>` | Center a display on the targeted vertical block face. |
| `/display nudge <id> <up\|down\|left\|right\|forward\|back> [amount]` | Move a display by a small amount. |
| `/display up\|down\|left\|right\|forward\|back <id> [amount]` | Shortcut nudge commands. |
| `/display scale <id> <scale>` | Set text scale. |
| `/display viewrange <id> <range>` | Set render range in blocks. |
| `/display viewrangeall <range>` | Set render range for every display. |
| `/display enabled <id> <true\|false>` | Show or hide a display. |
| `/display hide <id>` | Hide a display while keeping it saved. |
| `/display show <id>` | Respawn a hidden display. |
| `/display info <id>` | Show saved display settings. |
| `/display stats` | Show display and entity counts. |
| `/display setitem <id> <material>` | Change an item display's material. |
| `/display setblock <id> <material>` | Change a block display's material. |
| `/display interaction enable <id>` | Spawn a clickable interaction hitbox for a display. |
| `/display interaction disable <id>` | Disable and remove a display's interaction hitbox. |
| `/display interaction size <id> <width> <height>` | Set interaction hitbox size. |
| `/display interaction cooldown <id> <seconds>` | Set click cooldown per player/display/click type. |
| `/display interaction add <id> <left\|right> <action>` | Add a left or right click action. |
| `/display interaction clear <id> <left\|right\|all>` | Clear click actions. |
| `/display template list` | List saved display templates. |
| `/display template save <templateId> <displayId>` | Save a display's safe type/text/material/visual settings as a template. |
| `/display template apply <templateId> <displayId>` | Apply a template without changing the target ID or location. |
| `/display list` | List saved displays. |
| `/display delete <id>` | Delete a display and remove its entities. |
| `/display reload` | Reload config and saved displays. |

### Admin editor GUI

`/display edit <id>` opens a small inventory editor for safe common actions:

- Teleport to the display.
- Move the display to your current location.
- Suggest a clone command.
- Show display type and item/block material when applicable.
- Suggest `/display setitem` or `/display setblock` for item/block displays.
- Nudge on X/Y/Z by `editor.nudge-step`.
- Scale down, reset, or scale up.
- Decrease or increase view range.
- Force a placeholder/text refresh.

The editor intentionally does not include one-click deletion.

## Display Types

Saved displays have a `type`:

- `TEXT` uses vanilla `TextDisplay` entities and supports lines, legacy `&`
  colors, and PlaceholderAPI refresh.
- `ITEM` uses vanilla `ItemDisplay` entities and stores `item: MATERIAL`.
- `BLOCK` uses vanilla `BlockDisplay` entities and stores `block: MATERIAL`.

Existing saved displays without a `type` load as `TEXT` for backwards
compatibility. Placeholder refresh only applies to text displays.

## Click Actions

Click actions are disabled by default. Enabling interaction spawns a vanilla
`Interaction` entity tagged to the display and cleaned up with the display.

Actions are admin-configured and support these prefixes:

- `player:<command without leading slash>`
- `console:<command without leading slash>`
- `message:<message text>`

Examples:

```text
/display interaction enable rewards_sign
/display interaction size rewards_sign 1.0 1.0
/display interaction cooldown rewards_sign 2
/display interaction add rewards_sign right player:rewards
/display interaction add rewards_sign left message:&aYou clicked &f%id%&a.
```

Supported action tokens: `%player%`, `%uuid%`, `%id%`, `%world%`, `%x%`,
`%y%`, and `%z%`. If PlaceholderAPI is installed, action strings are also
parsed for the clicking player. Console actions should be treated as trusted
admin configuration.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `cozydisplays.admin` | `op` | Allows all CozyDisplays admin commands. |

## PlaceholderAPI

PlaceholderAPI is optional. If it is installed and enabled, CozyDisplays resolves placeholders in display lines on spawn and on the configured refresh interval. If PlaceholderAPI is missing, the plugin still starts normally and leaves placeholder text unchanged.

Displays are world-bound and not rendered per player by CozyDisplays, so player-specific placeholders may resolve to PlaceholderAPI's global, default, or empty value.

Each display can now store refresh controls in `displays.yml`:

```yaml
refresh:
  enabled: true
  interval-seconds: 10
  only-when-viewed: true
  viewer-range: 32
```

When `only-when-viewed` is true, CozyDisplays skips automatic refresh work unless
at least one player is in the same world and within the display's viewer range.
Lower intervals can make placeholders feel more responsive, but may increase
PlaceholderAPI and entity respawn work on busy servers.

## Configuration

`config.yml` includes:

| Key | Description |
| --- | --- |
| `placeholder-refresh-seconds` | Refresh interval for PlaceholderAPI text. Set to `0` to disable automatic refresh. |
| `refresh.minimum-interval-seconds` | Safe lower bound for per-display refresh intervals. |
| `refresh.default-interval-seconds` | Default refresh interval for new or older displays. |
| `refresh.default-only-when-viewed` | Whether new or older displays refresh only near viewers. |
| `refresh.default-viewer-range` | Default viewer range for refresh visibility checks. |
| `nearby-default-radius` | Default radius for `/display nearby`. |
| `nearby-max-radius` | Maximum accepted radius for `/display nearby`. |
| `interaction.default-width` | Default interaction hitbox width. |
| `interaction.default-height` | Default interaction hitbox height. |
| `interaction.default-cooldown-seconds` | Default per-player click cooldown. |
| `interaction.max-width` | Maximum interaction hitbox width. |
| `interaction.max-height` | Maximum interaction hitbox height. |
| `interaction.max-cooldown-seconds` | Maximum interaction cooldown. |
| `wall-offset` | Distance pushed out from a wall by `/display snapwall`. |
| `default-view-range` | Default render range in blocks for new or older saved displays. |
| `display-entity-warning-threshold` | Logs a warning when spawned TextDisplay entities exceed this count. |
| `default-nudge-amount` | Default movement amount for nudge commands. |
| `debug-view-range` | Logs view-range details while spawning displays. |
| `debug-placeholder-refresh` | Logs placeholder refresh checks. |
| `editor.*` | Basic editor GUI title, step sizes, item names, and lore. |

Unsafe numeric values are clamped to safe ranges and logged.

## Examples

Spawn rules sign:

```text
/display create rules &cRules
/display addline rules &f1. Be kind
/display addline rules &f2. No griefing
```

Create a lightweight status display with PlaceholderAPI:

```text
/display create online &aOnline: &f%server_online%
```

Limit render range for dense signage:

```text
/display viewrange rules 8
```

Update item and block materials:

```text
/display setitem diamond_icon EMERALD
/display setblock beacon_block DIAMOND_BLOCK
```

Clone a display and move the clone:

```text
/display clone rules rules_copy
/display movehere rules_copy
```

Save and apply a template:

```text
/display template save spawn_sign rules
/display template apply spawn_sign welcome
```

## Troubleshooting

| Problem | Check |
| --- | --- |
| Commands do not work | Confirm you have `cozydisplays.admin` or operator status. |
| Placeholders show literally | Install PlaceholderAPI and the needed expansions, then run `/display reload`. |
| Display is missing after restart | Confirm the saved world is loaded and check the server log for skipped display warnings. |
| You are not sure which display is nearby | Use `/display nearby [radius]`, then click an entry to prepare `/display edit <id>`. |
| Spawned displays seem out of sync | Run `/display audit`, then `/display reload` if it reports missing display or interaction entities. |
| Item or block display does not spawn as expected | Confirm the saved material is valid and, for block displays, is a block material. |
| Text is hard to position | Use `/display snapwall`, then the nudge shortcuts with small amounts such as `0.03` or `0.05`. |
| Dense areas cause client lag | Lower `viewrange`, remove unneeded lines, enable refresh only when viewed, or split signage across fewer visible displays. |

## Support / Issues

Please report bugs and documentation problems on the GitHub issue tracker:

https://github.com/Cheddah01/CozyDisplays/issues

When reporting bugs, include your server software/version, CozyDisplays version, Java version, relevant config snippets, and any console errors.

## Building From Source

Requirements:

- Java 21
- Maven 3.9 or newer

Build:

```sh
mvn clean package
```

The jar is written to `target/CozyDisplays-<version>.jar`.

## License

CozyDisplays is released under the MIT License. See [LICENSE](LICENSE).
