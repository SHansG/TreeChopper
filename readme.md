# TreeChopper

TreeChopper is a lightweight Paper plugin that lets players chop an entire tree by sneaking and breaking one log with an axe. It includes optional leaf removal, direct-to-inventory drops, tool durability damage, Unbreaking support, and basic configuration for SkyBlock or survival servers.

## Features

- Chop connected logs by sneaking and breaking a log
- Optional requirement to use an axe
- Optional leaf removal around chopped logs
- Optional leaf drops such as saplings, apples, and sticks
- Tool durability damage per chopped log
- Optional Unbreaking enchantment support
- Configurable maximum logs and leaves per chop
- World whitelist support
- Admin reload command

## Requirements

- Paper server
- Java 25 or newer
- Minecraft/Paper version compatible with the Paper API used during compilation

The plugin is designed for modern Paper servers and uses only standard Bukkit/Paper API features.

## Commands

```text
/treechopper status
```

Shows the current plugin configuration summary.

```text
/treechopper toggle
```

Toggles on/off the tree chopping.

```text
/treechopper reload
```

Reloads the plugin configuration.

Alias:

```text
/tc
```

## Permissions

```text
treechopper.use
```

Allows players to use tree chopping.

```text
treechopper.toggle
```

Allows players to toggle on/off tree chopping.

Default: `true`

```text
treechopper.reload
```

Allows administrators to reload the plugin configuration.

Default: `op`

LuckPerms examples:

```text
/lp group default permission set treechopper.use true
/lp group default permission set treechopper.toggle true
/lp group admin permission set treechopper.reload true
```

## Basic Usage

By default, players must:

1. Hold an axe.
2. Sneak.
3. Break a log.

The plugin will find connected logs, remove them, optionally remove nearby leaves, handle drops, and damage the tool.

Normal log breaking without sneaking remains vanilla.

## Configuration

Default configuration example:

```yaml
tree-chopper:
  enabled: true
  default-enabled-for-players: false
  require-sneaking: true
  require-axe: true
  max-logs: 96
  same-log-type-only: true
  drop-leftovers-on-ground: true

  tool:
    damage-enabled: true
    damage-per-log: 1
    respect-unbreaking: true
    play-break-sound: true

  leaves:
    enabled: true
    drop-items: true
    radius-around-logs: 3
    max-leaves: 128

worlds:
  enabled: []
```

## Important Config Options

### `require-sneaking`

If enabled, players must sneak to chop the whole tree.

```yaml
require-sneaking: true
```

### `require-axe`

If enabled, players must use an axe.

```yaml
require-axe: true
```

### `max-logs`

Limits how many logs can be chopped from one tree.

```yaml
max-logs: 96
```

This protects the server from very large custom trees or accidental log structures.

### `same-log-type-only`

If enabled, the plugin only connects logs of the same type.

```yaml
same-log-type-only: true
```

For example, oak logs will only connect to oak logs.

### `tool.damage-enabled`

Controls whether the axe loses durability.

```yaml
damage-enabled: true
```

### `tool.respect-unbreaking`

If enabled, the Unbreaking enchantment can prevent durability loss.

```yaml
respect-unbreaking: true
```

### `leaves.enabled`

Controls whether leaves are removed after chopping logs.

```yaml
enabled: true
```

### `leaves.radius-around-logs`

Controls how far around chopped logs the plugin searches for leaves.

```yaml
radius-around-logs: 3
```

For SkyBlock servers, keep this value low to avoid removing leaves from nearby trees.

### `worlds.enabled`

Controls which worlds the plugin works in.

```yaml
worlds:
  enabled: []
```

An empty list means all worlds are enabled.

Example whitelist:

```yaml
worlds:
  enabled:
    - bskyblock_world
    - bskyblock_world_nether
```

## Version

Current version: `1.0.0`
