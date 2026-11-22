package dev.xolo.usefulluck.loot;

import dev.xolo.usefulluck.LuckConfig;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class LootScaling {

    private static int getMultiplier(float luck, LuckConfig.Settings settings) {
        if (luck <= 0.0F || settings.quantityPerLuck() <= 0.0D) return 1;
        double raw = 1.0D + Math.floor(luck * settings.quantityPerLuck());
        return (int) Math.min(settings.maxQuantityMultiplier(), raw);
    }

    public static void scaleContainerOutput(
            ObjectArrayList<ItemStack> generated,
            float luck,
            int availableSlots,
            LuckConfig.Settings settings
    ) {
        if (!settings.enableChestQuantity() || generated.isEmpty() || availableSlots <= 0) return;

        int multiplier = getMultiplier(luck, settings);
        if (multiplier <= 1) return;

        int requiredSlots = countRequiredSlots(generated);
        int remainingSlots = Math.max(0, availableSlots - requiredSlots);
        int extraBudget = Math.min(settings.maxAddedStacks(), remainingSlots);

        // Existing stacks still grow even when there is no room for extra stacks.
        scaleStacks(generated, multiplier, extraBudget);
    }

    public static void scaleTrialSpawnerOutput(
            ObjectArrayList<ItemStack> generated,
            float luck,
            LuckConfig.Settings settings
    ) {
        if (generated.isEmpty()) return;
        int multiplier = getMultiplier(luck, settings);
        if (multiplier <= 1) return;
        scaleStacks(generated, multiplier, settings.maxAddedStacks());
    }

    /*
     * Ominous Item Spawners dispense one selected stack per entity. Scale a
     * copy of the one-item weighted-list entry; do not change its selection weight.
     */
    public static ItemStack scaleOminousDispensedStack(
            ItemStack original,
            float luck,
            LuckConfig.Settings settings
    ) {
        ItemStack scaled = original.copy();
        int multiplier = getMultiplier(luck, settings);
        if (multiplier <= 1 || scaled.isEmpty() || scaled.getMaxStackSize() <= 1) return scaled;

        long desiredCount = (long) scaled.getCount() * multiplier;
        scaled.setCount((int) Math.min(desiredCount, scaled.getMaxStackSize()));
        return scaled;
    }

    public static void scaleVaultOutput(
            ObjectArrayList<ItemStack> generated,
            float luck,
            LuckConfig.Settings settings
    ) {
        if (!settings.enableVaultQuantity() || generated.isEmpty()) return;
        int multiplier = getMultiplier(luck, settings);
        if (multiplier <= 1) return;
        scaleStacks(generated, multiplier, settings.maxAddedStacks());
    }

    public static void scaleItemEntities(
            Collection<ItemEntity> drops,
            Level level,
            float luck,
            LuckConfig.Settings settings
    ) {
        if (drops.isEmpty()) return;
        int multiplier = getMultiplier(luck, settings);
        if (multiplier <= 1) return;

        int remainingBudget = settings.maxAddedStacks();
        List<ItemEntity> additionalEntities = new ArrayList<>();

        for (ItemEntity entity : drops) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) continue;

            int maxStackSize = stack.getMaxStackSize();
            if (maxStackSize <= 1) continue;

            int originalCount = stack.getCount();
            if (originalCount <= 0) continue;

            long desiredCount = (long) originalCount * multiplier;
            ItemStack template = stack.copy();
            int primaryCount = (int) Math.min(desiredCount, maxStackSize);

            stack.setCount(primaryCount);
            entity.setItem(stack);

            long remainingCount = desiredCount - primaryCount;
            while (remainingCount > 0 && remainingBudget > 0) {
                int nextCount = (int) Math.min(remainingCount, maxStackSize);
                ItemStack extraStack = template.copy();
                extraStack.setCount(nextCount);

                ItemEntity extraEntity = new ItemEntity(
                        level, entity.getX(), entity.getY(), entity.getZ(), extraStack);
                additionalEntities.add(extraEntity);

                remainingCount -= nextCount;
                remainingBudget--;
            }
        }

        drops.addAll(additionalEntities);
    }

    private static void scaleStacks(
            ObjectArrayList<ItemStack> stacks,
            int multiplier,
            int extraStackBudget
    ) {
        int originalSize = stacks.size();

        for (int i = 0; i < originalSize; i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) continue;

            int maxStackSize = stack.getMaxStackSize();
            if (maxStackSize <= 1) continue;

            int originalCount = stack.getCount();
            if (originalCount <= 0) continue;

            long desiredCount = (long) originalCount * multiplier;
            ItemStack template = stack.copy();
            int primaryCount = (int) Math.min(desiredCount, maxStackSize);
            stack.setCount(primaryCount);

            long remainingCount = desiredCount - primaryCount;
            while (remainingCount > 0 && extraStackBudget > 0) {
                int nextCount = (int) Math.min(remainingCount, maxStackSize);
                ItemStack extra = template.copy();
                extra.setCount(nextCount);
                stacks.add(extra);

                remainingCount -= nextCount;
                extraStackBudget--;
            }
        }
    }

    private static int countRequiredSlots(List<ItemStack> stacks) {
        long required = 0;

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;

            int maxStackSize = stack.getMaxStackSize();
            if (maxStackSize <= 0) {
                required++;
                continue;
            }

            required += ((long) stack.getCount() + maxStackSize - 1) / maxStackSize;
            if (required >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }

        return (int) required;
    }

    private LootScaling() {}
}
