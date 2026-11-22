# Useful Luck

Luck improves loot generation and drop quantities in Minecraft Java Edition.

## Target platform

- Minecraft Java Edition **1.21.1**
- NeoForge **21.1.267**
- Java **25** build toolchain; Java 21 bytecode target for 1.21.1 compatibility
- ModDevGradle **2.0.148**
- Gradle wrapper **9.2.1**

## Features

### Quantity scaling

Luck scales stackable drops from:

- **Structure/chest loot** — scales generated chest loot while respecting available inventory slots and the configured extra-stack budget.
- **Vault loot** — scales generated vault stacks.
- **Living entity drops** — scales non-player living-entity drops when a player is responsible for the kill.
- **Ore block drops** — scales drops from blocks in NeoForge's Tags.Blocks.ORES tag. It does not scale arbitrary block drops such as crops or logs.

The quantity multiplier is:

**multiplier = min(maxQuantityMultiplier, 1 + floor(luck × quantityPerLuck))**

Unstackable items are not duplicated. Additional stacks or item entities are limited by maxAddedStacks.

### Loot rarity weighting

The mod adjusts weighted loot-entry quality when loot tables are loaded or datapacks are reloaded. It does not reroll loot tables.

For each sibling entry group, lower-weight entries receive an additional quality bonus:

**qualityBonus = ceil((maxWeight - weight) × biasStrength / targetLuck)**

Minecraft then applies its normal Luck-based weighted-selection formula. Existing quality values are preserved, and zero-weight entries remain impossible to select.

Rarity weighting currently covers:

- Chest loot tables using the minecraft:chest parameter set
- Vault loot tables using the minecraft:vault parameter set
- Entity loot tables using the minecraft:entity parameter set
- Normal and ominous Trial Spawner loot tables identified by their vanilla table paths

**Block rarity is not currently applied.** The blockEnabled config value exists but block and block-interaction parameter sets are deliberately excluded by the current rarity transformer. Ore quantity scaling is separate.

### Trial Spawners

Trial Spawner rewards use the highest Luck among alive, non-creative, non-spectator players within the spawner's configured required-player range.

- Normal and ominous reward tables receive that Luck for rarity selection.
- Normal and ominous reward quantities can be scaled independently.
- Ominous Item Spawner dispensing-table entries are generated with the same nearby-player Luck and can have their stack quantities scaled.
- Luck increases the weight of Trial Key reward entries in the spawner's existing weighted selection list. It changes selection odds; it does not reroll rewards or add keys to the consumables table.

At the defaults, Luck 10 gives the key entry a 3x weight multiplier. For an original 1:1 key/consumables weight split, that changes the key selection chance from 50% to 75%.

## Configuration

The server config is generated in the world's serverconfig directory.

### [quantity]

| Option | Default | Meaning |
|---|---:|---|
| chestEnabled | true | Scale chest loot quantities |
| vaultEnabled | true | Scale vault loot quantities |
| entityEnabled | true | Scale player-attributed non-player living-entity drops |
| blockEnabled | true | Scale ore-tagged block drops |
| quantityPerLuck | 0.5 | Additional multiplier growth per Luck |
| maxQuantityMultiplier | 32 | Maximum quantity multiplier |
| maxAddedStacks | 27 | Maximum additional stacks/item entities per loot operation |

### [rarity]

| Option | Default | Meaning |
|---|---:|---|
| enabled | true | Master rarity-weighting toggle |
| chestEnabled | true | Apply to chest loot tables |
| vaultEnabled | true | Apply to vault loot tables |
| entityEnabled | true | Apply to entity loot tables |
| blockEnabled | true | Reserved; block rarity is not currently applied |
| targetLuck | 10.0 | Luck used to calibrate the quality bonus |
| biasStrength | 1.5 | Strength of the rarity adjustment |
| maxQualityBonus | 128 | Maximum additional quality per entry |

### [trial_spawners]

| Option | Default | Meaning |
|---|---:|---|
| quantityEnabled | true | Scale normal Trial Spawner reward quantities |
| ominousQuantityEnabled | true | Scale ominous reward/dispensing quantities |
| rarityEnabled | true | Apply rarity weighting to normal reward tables |
| ominousRarityEnabled | true | Apply rarity weighting to ominous reward tables |
| keyWeightEnabled | true | Enable Luck-based Trial Key weighting |
| keyWeightPerLuck | 0.25 | Key-weight multiplier growth per nearby Luck |
| maxKeyWeightMultiplier | 8 | Maximum Trial Key weight multiplier |

## Build

Use JDK 25. The repository contains the Gradle wrapper scripts and properties; if gradle-wrapper.jar is absent, generate it first:

~~~sh
gradle wrapper --gradle-version 9.2.1 --distribution-type bin
./gradlew clean build
~~~

On Windows:

~~~bat
gradle wrapper --gradle-version 9.2.1 --distribution-type bin
gradlew.bat clean build
~~~

The mod JAR is written to build/libs/. GitHub Actions runs the same wrapper-based build on pushes and pull requests and uploads the JAR as an artifact.

## Verification

A successful compile does not prove that runtime Mixin injections apply. The packaged refmap and the Trial Spawner, chest, and vault injection points must also be validated in a 1.21.1 NeoForge game environment.
