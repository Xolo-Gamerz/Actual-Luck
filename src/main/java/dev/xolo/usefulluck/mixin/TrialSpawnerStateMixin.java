package dev.xolo.usefulluck.mixin;

import dev.xolo.usefulluck.LuckConfig;
import dev.xolo.usefulluck.loot.TrialSpawnerLootState;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState")
public abstract class TrialSpawnerStateMixin {
    @Redirect(
            method = "tickAndGetNext(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawner;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/random/WeightedList;getRandom(Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;",
                    ordinal = 1
            )
    )
    private Optional<ResourceKey<LootTable>> usefulluck$adjustKeyWeight(
            WeightedList<ResourceKey<LootTable>> original,
            RandomSource random,
            BlockPos pos,
            TrialSpawner spawner,
            ServerLevel level
    ) {
        LuckConfig.Settings settings = LuckConfig.getSettings();
        if (!settings.enableTrialKeyWeight() || settings.trialKeyWeightPerLuck() <= 0.0D) {
            return original.getRandom(random);
        }

        float maxLuck = TrialSpawnerLootState.getHighestNearbyLuck(
                level, pos, spawner.getRequiredPlayerRange()
        );
        if (maxLuck <= 0.0F) return original.getRandom(random);

        int multiplier = (int) Math.min(
                settings.maxTrialKeyWeightMultiplier(),
                1.0D + Math.floor(maxLuck * settings.trialKeyWeightPerLuck())
        );
        if (multiplier <= 1) return original.getRandom(random);

        WeightedList.Builder<ResourceKey<LootTable>> adjusted = WeightedList.builder();
        for (Weighted<ResourceKey<LootTable>> entry : original.unwrap()) {
            int weight = entry.weight();
            if (entry.value().identifier().toString().endsWith("/key")) {
                weight = (int) Math.min(Integer.MAX_VALUE, (long) weight * multiplier);
            }
            adjusted.add(entry.value(), weight);
        }

        return adjusted.build().getRandom(random);
    }
}
