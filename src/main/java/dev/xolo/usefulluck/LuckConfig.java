package dev.xolo.usefulluck;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side settings for chest and vault loot only.
 *
 * Config values are read through {@link #getSettings()} so loot-table loading
 * before the config has finished loading never calls ConfigValue#get().
 */
public final class LuckConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED;

    public static final ModConfigSpec.DoubleValue QUANTITY_PER_LUCK;
    public static final ModConfigSpec.IntValue MAX_QUANTITY_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAX_OUTPUT_STACKS;

    public static final ModConfigSpec.BooleanValue ENABLE_RARITY_BIAS;
    public static final ModConfigSpec.DoubleValue RARITY_TARGET_LUCK;
    public static final ModConfigSpec.DoubleValue RARITY_BIAS_STRENGTH;
    public static final ModConfigSpec.IntValue MAX_QUALITY_BONUS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("chest_loot");

        ENABLED = BUILDER
                .comment("Enable all Useful Luck changes for chest and vault loot.")
                .define("enabled", true);

        BUILDER.push("quantity");

        QUANTITY_PER_LUCK = BUILDER
                .comment(
                        "Additional quantity multiplier per point of Luck. " +
                        "The multiplier is 1 + floor(max(0, Luck) * this value)."
                )
                .defineInRange("perLuck", 0.5D, 0.0D, 10.0D);

        MAX_QUANTITY_MULTIPLIER = BUILDER
                .comment("Hard upper bound for the quantity multiplier.")
                .defineInRange("maxMultiplier", 32, 1, 256);

        MAX_OUTPUT_STACKS = BUILDER
                .comment(
                        "Maximum number of ItemStacks produced by one top-level chest loot table. " +
                        "This prevents extreme Luck values from creating unbounded output lists."
                )
                .defineInRange("maxOutputStacks", 256, 27, 4096);

        BUILDER.pop();
        BUILDER.push("rarity");

        ENABLE_RARITY_BIAS = BUILDER
                .comment("Give lower-weight entries a Luck-scaled quality bonus.")
                .define("enabled", true);

        RARITY_TARGET_LUCK = BUILDER
                .comment(
                        "At this Luck value, the rarity bonus approximately closes the original " +
                        "weight gap between the lightest and heaviest entries in a pool."
                )
                .defineInRange("targetLuck", 10.0D, 1.0D, 100.0D);

        RARITY_BIAS_STRENGTH = BUILDER
                .comment("Strength of the rarity bias. 1.0 applies the full weight-gap heuristic.")
                .defineInRange("biasStrength", 1.0D, 0.0D, 5.0D);

        MAX_QUALITY_BONUS = BUILDER
                .comment("Hard cap on the additional quality assigned to one loot entry.")
                .defineInRange("maxQualityBonus", 256, 0, 4096);

        BUILDER.pop();
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public record Settings(
            boolean enabled,
            double quantityPerLuck,
            int maxQuantityMultiplier,
            int maxOutputStacks,
            boolean rarityEnabled,
            double rarityTargetLuck,
            double rarityBiasStrength,
            int maxQualityBonus
    ) {
    }

    private static final Settings DEFAULTS = new Settings(
            true,
            0.5D,
            32,
            256,
            true,
            10.0D,
            1.0D,
            256
    );

    public static Settings getSettings() {
        if (!SPEC.isLoaded()) {
            return DEFAULTS;
        }

        return new Settings(
                ENABLED.get(),
                QUANTITY_PER_LUCK.get(),
                MAX_QUANTITY_MULTIPLIER.get(),
                MAX_OUTPUT_STACKS.get(),
                ENABLE_RARITY_BIAS.get(),
                RARITY_TARGET_LUCK.get(),
                RARITY_BIAS_STRENGTH.get(),
                MAX_QUALITY_BONUS.get()
        );
    }

    private LuckConfig() {
    }
}
