package dev.xolo.usefulluck.mixin;

import dev.xolo.usefulluck.loot.TrialSpawnerLootState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerMixin {
    @Inject(
            method = "ejectReward(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/resources/ResourceKey;)V",
            at = @At("HEAD")
    )
    private void usefulluck$beginReward(ServerLevel level, BlockPos pos, ResourceKey<LootTable> lootTable, CallbackInfo ci) {
        TrialSpawnerLootState.begin(level, pos, (TrialSpawner) (Object) this);
    }

    @Inject(
            method = "ejectReward(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/resources/ResourceKey;)V",
            at = @At("RETURN")
    )
    private void usefulluck$clearReward(ServerLevel level, BlockPos pos, ResourceKey<LootTable> lootTable, CallbackInfo ci) {
        TrialSpawnerLootState.clear();
    }
}
