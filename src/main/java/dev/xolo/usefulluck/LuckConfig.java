package dev.xolo.usefulluck;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class LuckConfig {

    private static final ModConfigSpec.Builder BUILDER =
            new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_CHEST_RARITY;
    public static final ModConfigSpec.DoubleValue TARGET_LUCK;
    public static final ModConfigSpec.DoubleValue BIAS_STRENGTH;
    public static final ModConfigSpec.IntValue MAX_QUALITY_BONUS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("chest_rarity");

        ENABLE_CHEST_RARITY = BUILDER
                .comment("Modify weighted entries in chest-context loot tables before loot selection.")
                .define("enabled", true);

        TARGET_LUCK = BUILDER
                .comment("At this Luck value, the lightest and heaviest entries in a pool are approximately equal weight.")
                .defineInRange("targetLuck", 10.0D, 1.0D, 100.0D);

        BIAS_STRENGTH = BUILDER
                .comment("Strength of the inverse-weight rarity heuristic. 1.0 uses the full weight difference.")
                .defineInRange("biasStrength", 1.0D, 0.0D, 5.0D);

        MAX_QUALITY_BONUS = BUILDER
                .comment("Hard cap on additional quality assigned to one loot entry.")
                .defineInRange("maxQualityBonus", 256, 0, 4096);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private LuckConfig() {
    }
}
