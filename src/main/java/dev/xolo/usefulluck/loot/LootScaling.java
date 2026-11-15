package dev.xolo.usefulluck.loot;

import dev.xolo.usefulluck.LuckConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;

/**
 * Applies quantity scaling to already-generated loot.
 *
 * Does not rerun loot tables. Only stackable items are multiplied.
 */
public final class LootScaling {

    public static void scaleContainerOutput(
            ObjectArrayList<ItemStack> generated,
            float luck,
            int availableSlots,
            LuckConfig.Settings settings
    ) {
        int freeOutputSlots = Math.max(0, availableSlots - generated.size());
        int allowedExtraStacks = Math.min(
                settings.maxAddedStacks(),
                freeOutputSlots
        );

        scale(generated, luck, allowedExtraStacks, settings);
    }

    /**
     * Vault rewards are ejected rather than inserted into a fixed-size
     * container, so they use the configured extra-stack safety cap.
     */
    public static void scaleVaultOutput(
            ObjectArrayList<ItemStack> generated,
            float luck,
            LuckConfig.Settings settings
    ) {
        scale(generated, luck, settings.maxAddedStacks(), settings);
    }

    private static void scale(
            ObjectArrayList<ItemStack> generated,
            float luck,
            int allowedExtraStacks,
            LuckConfig.Settings settings
    ) {
        if (generated == null || generated.isEmpty()
                || !settings.enabled()
                || settings.quantityPerLuck() <= 0.0D) {
            return;
        }

        int multiplier = getMultiplier(luck, settings);

        if (multiplier <= 1) {
            return;
        }

        int originalSize = generated.size();
        int remainingExtraSlots = Math.max(0, allowedExtraStacks);

        for (int index = 0; index < originalSize; index++) {
            ItemStack original = generated.get(index);

            if (original == null || original.isEmpty()) {
                continue;
            }

            int maxStackSize = original.getMaxStackSize();

            // Never duplicate unstackable items.
            if (maxStackSize <= 1) {
                continue;
            }

            int originalCount = original.getCount();
            long desiredCount = (long) originalCount * multiplier;

            if (desiredCount <= maxStackSize) {
                original.setCount((int) desiredCount);
                continue;
            }

            original.setCount(maxStackSize);

            long remaining = desiredCount - maxStackSize;

            while (remaining > 0L && remainingExtraSlots > 0) {
                int nextCount = (int) Math.min(
                        (long) maxStackSize,
                        remaining
                );

                ItemStack extra = original.copy();
                extra.setCount(nextCount);
                generated.add(extra);

                remaining -= nextCount;
                remainingExtraSlots--;
            }
        }
    }

    private static int getMultiplier(
            float luck,
            LuckConfig.Settings settings
    ) {
        if (!Float.isFinite(luck) || luck <= 0.0F) {
            return 1;
        }

        double raw = 1.0D
                + Math.floor(luck * settings.quantityPerLuck());

        return (int) Math.max(
                1.0D,
                Math.min(
                        (double) settings.maxQuantityMultiplier(),
                        raw
                )
        );
    }

    private LootScaling() {
    }
}