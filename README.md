# Useful Luck — Chest Rarity (NeoForge 26.3)

This iteration focuses exclusively on the central feature: **Luck changes which entries are selected from structure/chest loot pools**.

## Target

- Minecraft Java Edition 26.3
- NeoForge 26.3.0.101 (the pinned MDK coordinate)
- Java 25
- ModDevGradle 2.0.148

## How the chest rarity engine works

The mod listens to NeoForge's `LootTableLoadEvent`. That event fires on the logical server when loot tables from vanilla, mods and datapacks are loaded or reloaded.

For each table, it:

1. Checks whether the serialized loot-table context type is `minecraft:chest` or `minecraft:vault`. It also accepts the conventional `chests/` path as a fallback for older/custom tables that omit their context type.
2. Encodes the loaded `LootTable` using `LootTable.DIRECT_CODEC` and the reload's registry-aware serialization context.
3. Visits each pool's competing `entries` list and nested composite `children`.
4. Finds the largest base weight in that sibling group.
5. Adds a quality bonus to lower-weight entries using:

   `qualityBonus = ceil((maxWeight - weight) * biasStrength / targetLuck)`

6. Decodes the modified table and replaces the event table.

Minecraft then uses its native selection formula:

`effectiveWeight = max(floor(weight + quality * lootContextLuck), 0)`

Therefore:
- At Luck 0, selection is unchanged.
- As Luck rises, low-weight entries become increasingly competitive.
- Around `targetLuck`, the lowest- and highest-weight entries in a pool are approximately equalized.
- Above `targetLuck`, the rarer entries can become more likely than common entries.
- Existing quality values are preserved and the heuristic is added on top.
- Explicit zero-weight entries stay impossible to select.

The transformation happens during resource loading/reloading, not during each chest opening. There are no loot-table reroll loops and no per-open pool scans.

## Compatibility coverage

- Vanilla structure chests: selected by the `minecraft:chest` context type.
- Trial Chamber vault loot: selected by `minecraft:vault`.
- Modded chest tables: selected when they declare `minecraft:chest`, `minecraft:vault`, or use the conventional `chests/` path.
- User datapacks: included because NeoForge fires the load event for datapack tables and again on reload.
- Composite entries (`group`, `sequence`, `alternatives`): recursively traversed through their `children`.
- Custom modded weighted entry types: adjusted if their serialized entry exposes a `weight` property.

Known boundaries:
- A custom modded loot table with a non-chest custom context type and a nonstandard path cannot be identified as a chest table generically.
- Expanded item-tag entries remain a single weighted entry; their expanded items share that entry's weight.
- Referenced loot tables are transformed independently when their own table is loaded and identified as chest/vault loot.
- The codec round-trip is registry-aware. If a custom entry codec cannot encode/decode in the reload registry context, the transformer logs a warning and leaves that table untouched rather than breaking the reload.

## Configuration

The server config is generated under the world's `serverconfig` directory.

```toml
[chest_rarity]
enabled = true
targetLuck = 10.0
biasStrength = 1.0
maxQualityBonus = 256
```

- `enabled`: master switch.
- `targetLuck`: Luck level at which rare/common pool weights approximately equalize.
- `biasStrength`: strength of the heuristic.
- `maxQualityBonus`: per-entry quality cap.

## Build

Use JDK 25 and a compatible Gradle installation:

```sh
gradle wrapper
./gradlew build
```

On Windows:

```bat
gradle wrapper
gradlew.bat build
```

The JAR is produced in `build/libs/`.

The GitHub Actions workflow builds on every push and pull request with Java 25 and uploads the JAR as an Actions artifact.

## Verification status

The source archive is generated, but this environment has Java 21 and no installed Gradle, so I could not execute a Java 25 NeoForge compilation here. The first GitHub Actions build is the compilation gate before in-game testing.
