package dev.xolo.usefulluck;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class LuckConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_CHEST_QUANTITY;
    public static final ModConfigSpec.BooleanValue ENABLE_VAULT_QUANTITY;
    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_QUANTITY;
    public static final ModConfigSpec.BooleanValue ENABLE_BLOCK_QUANTITY;
    public static final ModConfigSpec.DoubleValue QUANTITY_PER_LUCK;
    public static final ModConfigSpec.IntValue MAX_QUANTITY_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAX_ADDED_STACKS;

    public static final ModConfigSpec.BooleanValue ENABLE_RARITY_BIAS;
    public static final ModConfigSpec.BooleanValue ENABLE_CHEST_RARITY;
    public static final ModConfigSpec.BooleanValue ENABLE_VAULT_RARITY;
    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_RARITY;
    public static final ModConfigSpec.BooleanValue ENABLE_BLOCK_RARITY;
    public static final ModConfigSpec.DoubleValue RARITY_TARGET_LUCK;
    public static final ModConfigSpec.DoubleValue RARITY_BIAS_STRENGTH;
    public static final ModConfigSpec.IntValue MAX_QUALITY_BONUS;

    public static final ModConfigSpec.BooleanValue ENABLE_TRIAL_SPAWNER_QUANTITY;
    public static final ModConfigSpec.BooleanValue ENABLE_OMINOUS_TRIAL_SPAWNER_QUANTITY;
    public static final ModConfigSpec.BooleanValue ENABLE_TRIAL_SPAWNER_RARITY;
    public static final ModConfigSpec.BooleanValue ENABLE_OMINOUS_TRIAL_SPAWNER_RARITY;
    public static final ModConfigSpec.BooleanValue ENABLE_TRIAL_KEY_WEIGHT;
    public static final ModConfigSpec.DoubleValue TRIAL_KEY_WEIGHT_PER_LUCK;
    public static final ModConfigSpec.IntValue MAX_TRIAL_KEY_WEIGHT_MULTIPLIER;

    public static final ModConfigSpec SPEC;

    public record Settings(
            boolean enableChestQuantity,
            boolean enableVaultQuantity,
            boolean enableEntityQuantity,
            boolean enableBlockQuantity,
            double quantityPerLuck,
            int maxQuantityMultiplier,
            int maxAddedStacks,
            boolean enableRarityBias,
            boolean enableChestRarity,
            boolean enableVaultRarity,
            boolean enableEntityRarity,
            boolean enableBlockRarity,
            double rarityTargetLuck,
            double rarityBiasStrength,
            int maxQualityBonus,
            boolean enableTrialSpawnerQuantity,
            boolean enableOminousTrialSpawnerQuantity,
            boolean enableTrialSpawnerRarity,
            boolean enableOminousTrialSpawnerRarity,
            boolean enableTrialKeyWeight,
            double trialKeyWeightPerLuck,
            int maxTrialKeyWeightMultiplier
    ) {}

    private static final Settings DEFAULTS = new Settings(
            true, true, true, true, 0.5D, 32, 27,
            true, true, true, true, true, 10.0D, 1.5D, 128,
            true, true, true, true, true, 0.25D, 8
    );

    static {
        BUILDER.push("quantity");
        ENABLE_CHEST_QUANTITY = BUILDER.comment("Scale generated chest loot quantities.").define("chestEnabled", true);
        ENABLE_VAULT_QUANTITY = BUILDER.comment("Scale generated vault loot quantities.").define("vaultEnabled", true);
        ENABLE_ENTITY_QUANTITY = BUILDER.comment("Scale drops from non-player living entities.").define("entityEnabled", true);
        ENABLE_BLOCK_QUANTITY = BUILDER.comment("Scale drops from broken blocks, including ores and crops.").define("blockEnabled", true);
        QUANTITY_PER_LUCK = BUILDER.comment("Additional quantity multiplier gained per point of Luck.").defineInRange("quantityPerLuck", 0.5D, 0.0D, 10.0D);
        MAX_QUANTITY_MULTIPLIER = BUILDER.comment("Maximum quantity multiplier.").defineInRange("maxQuantityMultiplier", 32, 1, 256);
        MAX_ADDED_STACKS = BUILDER.comment("Maximum additional stacks or dropped item entities per loot operation.").defineInRange("maxAddedStacks", 27, 0, 256);
        BUILDER.pop();

        BUILDER.push("rarity");
        ENABLE_RARITY_BIAS = BUILDER.comment("Enable Luck-based weighted loot selection adjustments.").define("enabled", true);
        ENABLE_CHEST_RARITY = BUILDER.comment("Apply rarity adjustments to chest loot.").define("chestEnabled", true);
        ENABLE_VAULT_RARITY = BUILDER.comment("Apply rarity adjustments to vault loot.").define("vaultEnabled", true);
        ENABLE_ENTITY_RARITY = BUILDER.comment("Apply rarity adjustments to entity loot.").define("entityEnabled", true);
        ENABLE_BLOCK_RARITY = BUILDER.comment("Apply rarity adjustments to block loot.").define("blockEnabled", true);
        RARITY_TARGET_LUCK = BUILDER.comment("Luck value used to calibrate rarity adjustments.").defineInRange("targetLuck", 10.0D, 1.0D, 100.0D);
        RARITY_BIAS_STRENGTH = BUILDER.comment("Strength of the rarity adjustment.").defineInRange("biasStrength", 1.5D, 0.0D, 5.0D);
        MAX_QUALITY_BONUS = BUILDER.comment("Maximum additional quality assigned to an entry.").defineInRange("maxQualityBonus", 128, 0, 4096);
        BUILDER.pop();

        BUILDER.push("trial_spawners");
        ENABLE_TRIAL_SPAWNER_QUANTITY = BUILDER.comment("Scale normal Trial Spawner reward quantities.").define("quantityEnabled", true);
        ENABLE_OMINOUS_TRIAL_SPAWNER_QUANTITY = BUILDER.comment("Scale ominous Trial Spawner reward quantities.").define("ominousQuantityEnabled", true);
        ENABLE_TRIAL_SPAWNER_RARITY = BUILDER.comment("Apply rarity weighting to normal Trial Spawner reward tables.").define("rarityEnabled", true);
        ENABLE_OMINOUS_TRIAL_SPAWNER_RARITY = BUILDER.comment("Apply rarity weighting to ominous Trial Spawner reward tables.").define("ominousRarityEnabled", true);
        ENABLE_TRIAL_KEY_WEIGHT = BUILDER.comment("Increase the chance of selecting a Trial Key reward with Luck.").define("keyWeightEnabled", true);
        TRIAL_KEY_WEIGHT_PER_LUCK = BUILDER.comment("Additional Trial Key selection weight multiplier per point of the highest nearby player's Luck.").defineInRange("keyWeightPerLuck", 0.25D, 0.0D, 2.0D);
        MAX_TRIAL_KEY_WEIGHT_MULTIPLIER = BUILDER.comment("Maximum multiplier applied to Trial Key selection weight.").defineInRange("maxKeyWeightMultiplier", 8, 1, 64);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static Settings getSettings() {
        if (!SPEC.isLoaded()) return DEFAULTS;
        return new Settings(
                ENABLE_CHEST_QUANTITY.get(), ENABLE_VAULT_QUANTITY.get(), ENABLE_ENTITY_QUANTITY.get(), ENABLE_BLOCK_QUANTITY.get(),
                QUANTITY_PER_LUCK.get(), MAX_QUANTITY_MULTIPLIER.get(), MAX_ADDED_STACKS.get(),
                ENABLE_RARITY_BIAS.get(), ENABLE_CHEST_RARITY.get(), ENABLE_VAULT_RARITY.get(), ENABLE_ENTITY_RARITY.get(), ENABLE_BLOCK_RARITY.get(),
                RARITY_TARGET_LUCK.get(), RARITY_BIAS_STRENGTH.get(), MAX_QUALITY_BONUS.get(),
                ENABLE_TRIAL_SPAWNER_QUANTITY.get(), ENABLE_OMINOUS_TRIAL_SPAWNER_QUANTITY.get(),
                ENABLE_TRIAL_SPAWNER_RARITY.get(), ENABLE_OMINOUS_TRIAL_SPAWNER_RARITY.get(),
                ENABLE_TRIAL_KEY_WEIGHT.get(), TRIAL_KEY_WEIGHT_PER_LUCK.get(), MAX_TRIAL_KEY_WEIGHT_MULTIPLIER.get()
        );
    }

    private LuckConfig() {}
}
