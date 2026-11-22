package dev.xolo.usefulluck.mixin;

import dev.xolo.usefulluck.LuckConfig;
import dev.xolo.usefulluck.loot.TrialSpawnerLootState;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TrialSpawnerState.class)
public abstract class TrialSpawnerStateMixin {
    @Redirect(
            method = "tickAndGetNext(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawner;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/random/SimpleWeightedRandomList;getRandomValue(Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;")
    )
    private Optional<ResourceKey<LootTable>> usefulluck$adjustKeyWeight(
            SimpleWeightedRandomList<ResourceKey<LootTable>> original, RandomSource random,
            BlockPos pos, TrialSpawner spawner, ServerLevel level) {
        LuckConfig.Settings settings = LuckConfig.getSettings();
        if (!settings.enableTrialKeyWeight() || settings.trialKeyWeightPerLuck() <= 0.0D) {
            return original.getRandomValue(random);
        }

        float maxLuck = TrialSpawnerLootState.getHighestNearbyLuck(level, pos, spawner.getRequiredPlayerRange());
        if (maxLuck <= 0.0F) return original.getRandomValue(random);

        int multiplier = (int) Math.min(settings.maxTrialKeyWeightMultiplier(),
                1.0D + Math.floor(maxLuck * settings.trialKeyWeightPerLuck()));
        if (multiplier <= 1) return original.getRandomValue(random);

        SimpleWeightedRandomList.Builder<ResourceKey<LootTable>> adjusted = SimpleWeightedRandomList.builder();
        for (WeightedEntry.Wrapper<ResourceKey<LootTable>> entry : original.unwrap()) {
            ResourceKey<LootTable> table = entry.data();
            String path = table.location().getPath();
            int weight = entry.weight().asInt();
            if (path.endsWith("/key") || path.endsWith("/ominous_key")) {
                weight = (int) Math.min(Integer.MAX_VALUE, (long) weight * multiplier);
            }
            adjusted.add(table, weight);
        }
        return adjusted.build().getRandomValue(random);
    }
}
