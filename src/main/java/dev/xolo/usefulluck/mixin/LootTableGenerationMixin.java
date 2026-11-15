package dev.xolo.usefulluck.mixin;

import dev.xolo.usefulluck.LuckConfig;
import dev.xolo.usefulluck.loot.LootGenerationState;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales the output of the outermost chest/vault loot-table generation call.
 *
 * Scaling the returned stacks is deliberately used instead of rerolling the
 * table. That keeps the operation linear in the generated output size and
 * avoids repeated execution of loot conditions, functions, and nested tables.
 */
@Mixin(LootTable.class)
public abstract class LootTableGenerationMixin {

    @Shadow
    public abstract ContextKeySet getParamSet();

    @Inject(
            method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("HEAD")
    )
    private void usefulluck$enterGeneration(
            LootContext context,
            CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir
    ) {
        LootGenerationState.enter();
    }

    @Inject(
            method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("RETURN")
    )
    private void usefulluck$scaleChestOutput(
            LootContext context,
            CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir
    ) {
        int depth = LootGenerationState.depth();

        try {
            // Nested loot tables are already part of the outer table's result.
            // Scaling only depth 1 prevents multiplying the same output twice.
            if (depth != 1 || !isChestOrVaultTable()) {
                return;
            }

            LuckConfig.Settings settings = LuckConfig.getSettings();
            if (!settings.enabled() || settings.quantityPerLuck() <= 0.0D) {
                return;
            }

            ObjectArrayList<ItemStack> generated = cir.getReturnValue();
            if (generated == null || generated.isEmpty()) {
                return;
            }

            int multiplier = getQuantityMultiplier(context.getLuck(), settings);
            if (multiplier > 1) {
                scaleStacks(generated, multiplier, settings.maxOutputStacks());
            }
        } finally {
            LootGenerationState.exit();
        }
    }

    @Unique
    private boolean isChestOrVaultTable() {
        ContextKeySet paramSet = getParamSet();
        return paramSet.equals(LootContextParamSets.CHEST)
                || paramSet.equals(LootContextParamSets.VAULT);
    }

    @Unique
    private static int getQuantityMultiplier(float luck, LuckConfig.Settings settings) {
        if (!Float.isFinite(luck) || luck <= 0.0F) {
            return 1;
        }

        double calculated = 1.0D + Math.floor(luck * settings.quantityPerLuck());
        return (int) Math.max(1.0D,
                Math.min(settings.maxQuantityMultiplier(), calculated));
    }

    @Unique
    private static void scaleStacks(
            ObjectArrayList<ItemStack> generated,
            int multiplier,
            int maxOutputStacks
    ) {
        int originalSize = generated.size();

        for (int i = 0; i < originalSize; i++) {
            ItemStack original = generated.get(i);
            if (original.isEmpty()) {
                continue;
            }

            int originalCount = original.getCount();
            int maxStackSize = Math.max(1, original.getMaxStackSize());
            long desiredCount = (long) originalCount * multiplier;

            int freeOutputSlots = Math.max(0, maxOutputStacks - generated.size());
            long representableCount = (long) maxStackSize * (1L + freeOutputSlots);
            long finalCount = Math.min(desiredCount, representableCount);

            // The original stack occupies one output slot. Any additional
            // quantity is represented by valid-size copies, never oversized stacks.
            int firstStackCount = (int) Math.min(maxStackSize, finalCount);
            original.setCount(firstStackCount);

            long remaining = finalCount - firstStackCount;
            while (remaining > 0L && generated.size() < maxOutputStacks) {
                int nextCount = (int) Math.min(maxStackSize, remaining);
                ItemStack extra = original.copy();
                extra.setCount(nextCount);
                generated.add(extra);
                remaining -= nextCount;
            }
        }
    }
}
