package dev.xolo.usefulluck.mixin;

import dev.xolo.usefulluck.loot.ChestLootRarityTransformer;

import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LootTable.class)
public abstract class LootTableGenerationDebugMixin {

    @Shadow
    public abstract Identifier getLootTableId();

    @Inject(
            method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("HEAD")
    )
    private void usefulluck$beforeLootGeneration(
            LootContext context,
            CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir
    ) {

        ChestLootRarityTransformer.logGeneration(
                getLootTableId(),
                context.getLuck()
        );
    }

    @Inject(
            method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("RETURN")
    )
    private void usefulluck$afterLootGeneration(
            LootContext context,
            CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir
    ) {

        ChestLootRarityTransformer.logGeneratedItems(
                getLootTableId(),
                cir.getReturnValue()
        );
    }
}