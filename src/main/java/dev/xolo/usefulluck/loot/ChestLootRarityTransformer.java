package dev.xolo.usefulluck.loot;

import dev.xolo.usefulluck.LuckConfig;
import dev.xolo.usefulluck.mixin.CompositeEntryBaseAccessor;
import dev.xolo.usefulluck.mixin.LootPoolAccessor;
import dev.xolo.usefulluck.mixin.LootPoolSingletonContainerAccessor;
import dev.xolo.usefulluck.mixin.LootTableAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChestLootRarityTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger("UsefulLuck/Rarity");

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ChestLootRarityTransformer::onLootTableLoad);
    }

    private static void onLootTableLoad(LootTableLoadEvent event) {
        LootTable table = event.getTable();
        LuckConfig.Settings settings = LuckConfig.getSettings();

        if (!isRarityEnabled(table, settings, event.getName().getPath())) return;
        if (!settings.enableRarityBias()
                || settings.rarityTargetLuck() <= 0.0D
                || settings.rarityBiasStrength() <= 0.0D
                || settings.maxQualityBonus() <= 0) return;

        List<LootPool> pools = ((LootTableAccessor) (Object) table).usefulluck$getPools();
        int changed = 0;
        for (LootPool pool : pools) {
            List<LootPoolEntryContainer> entries =
                    ((LootPoolAccessor) (Object) pool).usefulluck$getEntries();
            changed += transformEntries(entries, settings.rarityTargetLuck(),
                    settings.rarityBiasStrength(), settings.maxQualityBonus());
        }

        if (changed > 0) {
            LOGGER.debug("Applied Luck rarity weighting to {} ({} entries)", event.getName(), changed);
        }
    }

    private static boolean isRarityEnabled(LootTable table, LuckConfig.Settings settings, String path) {
        if (path.startsWith("spawners/ominous/trial_chamber/")
                || path.endsWith("/items_to_drop_when_ominous")) {
            return settings.enableOminousTrialSpawnerRarity();
        }
        if (path.startsWith("spawners/trial_chamber/")) {
            return settings.enableTrialSpawnerRarity();
        }
        if (table.getParamSet().equals(LootContextParamSets.CHEST)) return settings.enableChestRarity();
        if (table.getParamSet().equals(LootContextParamSets.VAULT)) return settings.enableVaultRarity();
        if (table.getParamSet().equals(LootContextParamSets.ENTITY)) return settings.enableEntityRarity();
        return false;
    }

    private static int transformEntries(
            List<LootPoolEntryContainer> entries,
            double targetLuck,
            double biasStrength,
            int maxQualityBonus
    ) {
        List<LootPoolSingletonContainer> candidates = new ArrayList<>();
        for (LootPoolEntryContainer entry : entries) {
            if (entry instanceof LootPoolSingletonContainer weightedEntry) {
                candidates.add(weightedEntry);
            }
        }

        int maxWeight = 1;
        for (LootPoolSingletonContainer candidate : candidates) {
            int weight = ((LootPoolSingletonContainerAccessor) (Object) candidate).usefulluck$getWeight();
            maxWeight = Math.max(maxWeight, weight);
        }

        int changed = 0;
        for (LootPoolSingletonContainer candidate : candidates) {
            LootPoolSingletonContainerAccessor accessor =
                    (LootPoolSingletonContainerAccessor) (Object) candidate;
            int weight = accessor.usefulluck$getWeight();
            if (weight <= 0 || weight >= maxWeight) continue;

            int quality = accessor.usefulluck$getQuality();
            double rawDelta = (maxWeight - (double) weight) * biasStrength / targetLuck;
            int delta = (int) Math.min(maxQualityBonus, Math.ceil(rawDelta));
            if (delta <= 0) continue;

            long adjustedQuality = (long) quality + delta;
            accessor.usefulluck$setQuality((int) Math.min(Integer.MAX_VALUE, adjustedQuality));
            changed++;
        }

        for (LootPoolEntryContainer entry : entries) {
            if (entry instanceof CompositeEntryBase composite) {
                List<LootPoolEntryContainer> children =
                        ((CompositeEntryBaseAccessor) (Object) composite).usefulluck$getChildren();
                changed += transformEntries(children, targetLuck, biasStrength, maxQualityBonus);
            }
        }

        return changed;
    }

    private ChestLootRarityTransformer() {}
}
