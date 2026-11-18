
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

    private static int getMultiplier(
            float luck,
            LuckConfig.Settings settings
    ) {
        if (luck <= 0.0F || settings.quantityPerLuck() <= 0.0D) {
            return 1;
        }

        double raw = 1.0D
                + Math.floor(luck * settings.quantityPerLuck());

        return (int) Math.min(
                settings.maxQuantityMultiplier(),
                raw
        );
    }

    /*
     * Chest output.
     *
     * availableSlots is the number of free slots passed by vanilla.
     * Existing oversized stacks are accounted for before extra stacks
     * are permitted.
     */
    public static void scaleContainerOutput(
            ObjectArrayList<ItemStack> generated,
            float luck,
            int availableSlots,
            LuckConfig.Settings settings
    ) {
        if (!settings.enableChestQuantity()
                || generated.isEmpty()
                || availableSlots <= 0) {
            return;
        }

        int multiplier = getMultiplier(luck, settings);

        if (multiplier <= 1) {
            return;
        }

        int requiredSlots = countRequiredSlots(generated);

        int remainingSlots = Math.max(
                0,
                availableSlots - requiredSlots
        );

        int extraBudget = Math.min(
                settings.maxAddedStacks(),
                remainingSlots
        );

        scaleStacks(generated, multiplier, extraBudget);
    }

    /*
     * Trial Spawner rewards are ejected directly, not filled into a container.
     * They still use the same stack-size and extra-stack limits as other loot.
     */
    public static void scaleTrialSpawnerOutput(
            ObjectArrayList<ItemStack> generated,
            float luck,
            LuckConfig.Settings settings
    ) {
        if (generated.isEmpty()) {
            return;
        }

        int multiplier = getMultiplier(luck, settings);
        if (multiplier <= 1) {
            return;
        }

        scaleStacks(generated, multiplier, settings.maxAddedStacks());
    }

    /*
     * Vault output does not use the chest container filling path.
     */
    public static void scaleVaultOutput(
            ObjectArrayList<ItemStack> generated,
            float luck,
            LuckConfig.Settings settings
    ) {
        if (!settings.enableVaultQuantity() || generated.isEmpty()) {
            return;
        }

        int multiplier = getMultiplier(luck, settings);

        if (multiplier <= 1) {
            return;
        }

        scaleStacks(
                generated,
                multiplier,
                settings.maxAddedStacks()
        );
    }

    /*
     * Entity and block drops.
     *
     * This runs after vanilla has generated the drops.
     * Extra stacks become separate ItemEntity instances.
     */
    public static void scaleItemEntities(
            Collection<ItemEntity> drops,
            Level level,
            float luck,
            LuckConfig.Settings settings
    ) {
        if (drops.isEmpty()) {
            return;
        }

        int multiplier = getMultiplier(luck, settings);

        if (multiplier <= 1) {
            return;
        }

        int remainingBudget = settings.maxAddedStacks();

        if (remainingBudget <= 0) {
            return;
        }

        List<ItemEntity> additionalEntities = new ArrayList<>();

        for (ItemEntity entity : drops) {
            if (remainingBudget <= 0) {
                break;
            }

            ItemStack stack = entity.getItem();

            if (stack.isEmpty()) {
                continue;
            }

            int maxStackSize = stack.getMaxStackSize();

            // Never duplicate unstackable items.
            if (maxStackSize <= 1) {
                continue;
            }

            int originalCount = stack.getCount();

            if (originalCount <= 0) {
                continue;
            }

            long desiredCount = (long) originalCount * multiplier;

            ItemStack template = stack.copy();

            int primaryCount = (int) Math.min(
                    desiredCount,
                    maxStackSize
            );

            stack.setCount(primaryCount);
            entity.setItem(stack);

            long remainingCount = desiredCount - primaryCount;

            while (remainingCount > 0 && remainingBudget > 0) {
                int nextCount = (int) Math.min(
                        remainingCount,
                        maxStackSize
                );

                ItemStack extraStack = template.copy();
                extraStack.setCount(nextCount);

                ItemEntity extraEntity = new ItemEntity(
                        level,
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        extraStack
                );

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
            if (extraStackBudget <= 0) {
                break;
            }

            ItemStack stack = stacks.get(i);

            if (stack.isEmpty()) {
                continue;
            }

            int maxStackSize = stack.getMaxStackSize();

            if (maxStackSize <= 1) {
                continue;
            }

            int originalCount = stack.getCount();

            if (originalCount <= 0) {
                continue;
            }

            long desiredCount = (long) originalCount * multiplier;

            ItemStack template = stack.copy();

            int primaryCount = (int) Math.min(
                    desiredCount,
                    maxStackSize
            );

            stack.setCount(primaryCount);

            long remainingCount = desiredCount - primaryCount;

            while (remainingCount > 0 && extraStackBudget > 0) {
                int nextCount = (int) Math.min(
                        remainingCount,
                        maxStackSize
                );

                ItemStack extra = template.copy();
                extra.setCount(nextCount);

                stacks.add(extra);

                remainingCount -= nextCount;
                extraStackBudget--;
            }
        }
    }

    private static int countRequiredSlots(
            List<ItemStack> stacks
    ) {
        long required = 0;

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            int maxStackSize = stack.getMaxStackSize();

            if (maxStackSize <= 0) {
                required++;
                continue;
            }

            required += (
                    (long) stack.getCount() + maxStackSize - 1
            ) / maxStackSize;

            if (required >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }

        return (int) required;
    }

    private LootScaling() {}
}
