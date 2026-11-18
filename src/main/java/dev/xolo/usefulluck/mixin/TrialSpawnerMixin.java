package dev.xolo.usefulluck.mixin;

import dev.xolo.usefulluck.LuckConfig;
import dev.xolo.usefulluck.loot.LootScaling;
import dev.xolo.usefulluck.loot.TrialSpawnerLootState;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerMixin {
    @Redirect(
            method = "ejectReward(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/resources/ResourceKey;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"
            )
    )
    private ObjectArrayList<ItemStack> usefulluck$scaleTrialSpawnerReward(
            LootTable lootTable,
            LootParams params,
            ServerLevel level,
            BlockPos pos,
            ResourceKey<LootTable> rewardTable
    ) {
        ObjectArrayList<ItemStack> generated = lootTable.getRandomItems(params);
        TrialSpawner spawner = (TrialSpawner) (Object) this;
        LuckConfig.Settings settings = LuckConfig.getSettings();

        boolean enabled = spawner.isOminous()
                ? settings.enableOminousTrialSpawnerQuantity()
                : settings.enableTrialSpawnerQuantity();

        if (enabled) {
            float luck = TrialSpawnerLootState.getHighestNearbyLuck(
                    level, pos, spawner.getRequiredPlayerRange()
            );
            LootScaling.scaleTrialSpawnerOutput(generated, luck, settings);
        }

        return generated;
    }
}
