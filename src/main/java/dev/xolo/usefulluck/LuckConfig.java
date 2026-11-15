package dev.xolo.usefulluck;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration for chest and vault loot.
 */
public final class LuckConfig {

    private static final ModConfigSpec.Builder BUILDER =
            new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED;

    public static final ModConfigSpec.DoubleValue QUANTITY_PER_LUCK;
    public static final ModConfigSpec.IntValue MAX_QUANTITY_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAX_ADDED_STACKS;

    public static final ModConfigSpec.BooleanValue ENABLE_RARITY_BIAS;
    public static final ModConfigSpec.DoubleValue RARITY_TARGET_LUCK;
    public static final ModConfigSpec.DoubleValue RARITY_BIAS_STRENGTH;
    public static final ModConfigSpec.IntValue MAX_QUALITY_BONUS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("chest_loot");

        ENABLED = BUILDER
                .comment("Enable Useful Luck changes for chest and vault loot.")
                .define("enabled", true);

        BUILDER.push("quantity");

        QUANTITY_PER_LUCK = BUILDER
                .comment("Multiplier growth: 1 + floor(max(0, Luck) * perLuck).")
                .defineInRange("perLuck", 0.5D, 0.0D, 10.0D);

        MAX_QUANTITY_MULTIPLIER = BUILDER
                .comment("Hard cap on the quantity multiplier.")
                .defineInRange("maxMultiplier", 32, 1, 256);

        MAX_ADDED_STACKS = BUILDER
                .comment(
                        "Maximum extra ItemStacks Useful Luck may add to one generation. " +
                        "For chests this is additionally limited by the container's actual free slots. " +
                        "Unstackable items are never multiplied."
                )
                .defineInRange("maxAddedStacks", 27, 0, 256);

        BUILDER.pop();
        BUILDER.push("rarity");

        ENABLE_RARITY_BIAS = BUILDER
                .comment("Increase the selection weight of lower-weight entries in chest and vault loot pools.")
                .define("enabled", true);

        RARITY_TARGET_LUCK = BUILDER
                .comment(
                        "At this Luck value, the added quality approximately closes the original " +
                        "weight gap between the lightest and heaviest supported entries in a pool."
                )
                .defineInRange("targetLuck", 10.0D, 1.0D, 100.0D);

        RARITY_BIAS_STRENGTH = BUILDER
                .comment("Strength of the rarity adjustment.")
                .defineInRange("biasStrength", 1.5D, 0.0D, 5.0D);

        MAX_QUALITY_BONUS = BUILDER
                .comment("Maximum additional quality assigned to one supported loot entry.")
                .defineInRange("maxQualityBonus", 128, 0, 4096);

        BUILDER.pop();
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public record Settings(
            boolean enabled,
            double quantityPerLuck,
            int maxQuantityMultiplier,
            int maxAddedStacks,
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
            27,
            true,
            10.0D,
            1.5D,
            128
    );

    /**
     * Avoids reading config values before NeoForge has loaded the spec.
     */
    public static Settings getSettings() {
        if (!SPEC.isLoaded()) {
            return DEFAULTS;
        }

        return new Settings(
                ENABLED.get(),
                QUANTITY_PER_LUCK.get(),
                MAX_QUANTITY_MULTIPLIER.get(),
                MAX_ADDED_STACKS.get(),
                ENABLE_RARITY_BIAS.get(),
                RARITY_TARGET_LUCK.get(),
                RARITY_BIAS_STRENGTH.get(),
                MAX_QUALITY_BONUS.get()
        );
    }

    private LuckConfig() {
    }
}