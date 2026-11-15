
package dev.xolo.usefulluck.mixin;

import dev.xolo.usefulluck.LuckConfig;
import dev.xolo.usefulluck.loot.LootGenerationState;
import dev.xolo.usefulluck.loot.LootScaling;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LootTable.class)
public abstract class LootTableGenerationMixin {

    @Shadow
    public abstract ContextKeySet getParamSet();

    @Inject(
            method = "fill(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/storage/loot/LootParams;J)V",
            at = @At("HEAD")
    )
    private void usefulluck$beginFill(
            Container container,
            LootParams params,
            long seed,
            CallbackInfo ci
    ) {
        LootGenerationState.beginFill(
                getParamSet().equals(LootContextParamSets.CHEST),
                params.getLuck()
        );
    }

    @Inject(
            method = "shuffleAndSplitItems(Lit/unimi/dsi/fastutil/objects/ObjectArrayList;ILnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD")
    )
    private void usefulluck$scaleChestOutput(
            ObjectArrayList<ItemStack> generated,
            int availableSlots,
            RandomSource random,
            CallbackInfo ci
    ) {
        Float luck = LootGenerationState.takeChestLuck();

        if (luck == null) {
            return;
        }

        LuckConfig.Settings settings = LuckConfig.getSettings();

        LootScaling.scaleContainerOutput(
                generated,
                luck,
                availableSlots,
                settings
        );
    }

    @Inject(
            method = "fill(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/storage/loot/LootParams;J)V",
            at = @At("RETURN")
    )
    private void usefulluck$clearFill(
            Container container,
            LootParams params,
            long seed,
            CallbackInfo ci
    ) {
        LootGenerationState.clear();
    }

    @Inject(
            method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;Lnet/minecraft/util/RandomSource;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("RETURN")
    )
    private void usefulluck$scaleVaultOutput(
            LootParams params,
            RandomSource random,
            CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir
    ) {
        if (!getParamSet().equals(LootContextParamSets.VAULT)) {
            return;
        }

        LootScaling.scaleVaultOutput(
                cir.getReturnValue(),
                params.getLuck(),
                LuckConfig.getSettings()
        );
    }
}
