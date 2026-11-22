package dev.xolo.usefulluck.mixin;

import dev.xolo.usefulluck.LuckConfig;
import dev.xolo.usefulluck.loot.LootScaling;
import dev.xolo.usefulluck.loot.TrialSpawnerLootState;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TrialSpawnerData.class)
public abstract class TrialSpawnerDataMixin {
    @Redirect(
            method = "getDispensingItems(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawnerConfig;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/util/random/SimpleWeightedRandomList;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;J)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;")
    )
    private ObjectArrayList<ItemStack> usefulluck$applyLuckToOminousDispensingTable(
            LootTable lootTable, LootParams params, long seed, ServerLevel level, TrialSpawnerConfig config, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        int range = blockEntity instanceof TrialSpawnerBlockEntity trialSpawnerBlockEntity
                ? trialSpawnerBlockEntity.getTrialSpawner().getRequiredPlayerRange()
                : 14;
        float luck = TrialSpawnerLootState.getHighestNearbyLuck(level, pos, range);
        LuckConfig.Settings settings = LuckConfig.getSettings();

        LootParams luckParams = new LootParams.Builder(level)
                .withLuck(luck)
                .create(LootContextParamSets.EMPTY);
        ObjectArrayList<ItemStack> generated = lootTable.getRandomItems(luckParams, seed);

        if (settings.enableOminousTrialSpawnerQuantity()) {
            for (int i = 0; i < generated.size(); i++) {
                generated.set(i, LootScaling.scaleOminousDispensedStack(generated.get(i), luck, settings));
            }
        }

        return generated;
    }
}
