package dev.xolo.usefulluck;

import dev.xolo.usefulluck.loot.ChestLootRarityTransformer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(UsefulLuck.MOD_ID)
public final class UsefulLuck {
    public static final String MOD_ID = "usefulluck";

    public UsefulLuck(ModContainer container) {
        ChestLootRarityTransformer.register();
        container.registerConfig(ModConfig.Type.SERVER, LuckConfig.SPEC);
    }
}
