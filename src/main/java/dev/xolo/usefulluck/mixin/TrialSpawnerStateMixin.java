package dev.xolo.usefulluck.mixin;

import dev.xolo.usefulluck.LuckConfig;
import dev.xolo.usefulluck.loot.TrialSpawnerLootState;
import dev.xolo.usefulluck.loot.LootScaling;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState")
public abstract class TrialSpawnerStateMixin {
    @Redirect(
            method = "tickAndGetNext(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawner;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/random/WeightedList;getRandom(Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"
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
        if (maxLuck <= 0.0F) {
            return original.getRandom(random);
        }

        int multiplier = (int) Math.min(
                settings.maxTrialKeyWeightMultiplier(),
                1.0D + Math.floor(maxLuck * settings.trialKeyWeightPerLuck())
        );
        if (multiplier <= 1) {
            return original.getRandom(random);
        }

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

    @Inject(
            method = "spawnOminousOminousItemSpawner(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawner;)V",
            at = @At("HEAD")
    )
    private void usefulluck$beginOminousDispensing(
            ServerLevel level,
            BlockPos pos,
            TrialSpawner spawner,
            CallbackInfo ci
    ) {
        TrialSpawnerLootState.beginDispensing(
                level, pos, spawner.getRequiredPlayerRange()
        );
    }

    @Inject(
            method = "spawnOminousOminousItemSpawner(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawner;)V",
            at = @At("RETURN")
    )
    private void usefulluck$clearOminousDispensing(
            ServerLevel level,
            BlockPos pos,
            TrialSpawner spawner,
            CallbackInfo ci
    ) {
        TrialSpawnerLootState.clearDispensing();
    }

    @Redirect(
            method = "spawnOminousOminousItemSpawner(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawner;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/random/WeightedList;getRandom(Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"
            )
    )
    private Optional<ItemStack> usefulluck$scaleOminousDispensedItem(
            WeightedList<ItemStack> items,
            RandomSource random,
            ServerLevel level,
            BlockPos pos,
            TrialSpawner spawner
    ) {
        Optional<ItemStack> selected = items.getRandom(random);
        LuckConfig.Settings settings = LuckConfig.getSettings();
        if (selected.isEmpty() || !settings.enableOminousTrialSpawnerQuantity()) {
            return selected;
        }

        float luck = TrialSpawnerLootState.getHighestNearbyLuck(
                level, pos, spawner.getRequiredPlayerRange()
        );
        return Optional.of(
                LootScaling.scaleOminousDispensedStack(selected.get(), luck, settings)
        );
    }
}
