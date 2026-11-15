package dev.xolo.usefulluck.loot;

import dev.xolo.usefulluck.LuckConfig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.PlaceholderLookupProvider;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChestLootRarityTransformer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("UsefulLuck/ChestRarity");

    private static final AtomicBoolean WARNED_UNLOADED_CONFIG =
            new AtomicBoolean(false);

    private static final AtomicBoolean WARNED_REGISTRY_PROVIDER =
            new AtomicBoolean(false);

    private record Settings(
            boolean enabled,
            double targetLuck,
            double biasStrength,
            int maxQualityBonus
    ) {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(
                ChestLootRarityTransformer::onLootTableLoad
        );

        LOGGER.info("[CHECK] Chest loot rarity listener registered.");
    }

    private static Settings getSettings() {

        if (!LuckConfig.SPEC.isLoaded()) {

            if (WARNED_UNLOADED_CONFIG.compareAndSet(false, true)) {
                LOGGER.warn(
                        "[CHECK] Useful Luck config is not loaded yet. "
                                + "Using default settings for early loot table loading."
                );
            }

            return new Settings(
                    LuckConfig.ENABLE_CHEST_RARITY.getDefault(),
                    LuckConfig.TARGET_LUCK.getDefault(),
                    LuckConfig.BIAS_STRENGTH.getDefault(),
                    LuckConfig.MAX_QUALITY_BONUS.getDefault()
            );
        }

        return new Settings(
                LuckConfig.ENABLE_CHEST_RARITY.get(),
                LuckConfig.TARGET_LUCK.get(),
                LuckConfig.BIAS_STRENGTH.get(),
                LuckConfig.MAX_QUALITY_BONUS.get()
        );
    }

    private static void onLootTableLoad(LootTableLoadEvent event) {

        LootTable original = event.getTable();

        // Ignore non-chest loot tables.
        if (!isTargetLootTable(original)) {
            return;
        }

        LOGGER.info(
                "[CHECK] Chest loot table loaded: {} | paramSet={}",
                event.getName(),
                original.getParamSet()
        );

        Settings settings = getSettings();

        LOGGER.info(
                "[CHECK] Settings for {} | enabled={} | targetLuck={} | biasStrength={} | maxQualityBonus={}",
                event.getName(),
                settings.enabled(),
                settings.targetLuck(),
                settings.biasStrength(),
                settings.maxQualityBonus()
        );

        if (!settings.enabled()
                || settings.biasStrength() <= 0.0D
                || settings.maxQualityBonus() <= 0) {

            LOGGER.info(
                    "[CHECK] Skipping {} because rarity modification is disabled or has zero strength.",
                    event.getName()
            );

            return;
        }

        RegistryOps<JsonElement> ops;

        try {
            ops = createSerializationOps(event.getRegistries());

        } catch (IllegalStateException exception) {

            if (WARNED_REGISTRY_PROVIDER.compareAndSet(false, true)) {
                LOGGER.error(
                        "[CHECK] Cannot create registry serialization context for loot tables.",
                        exception
                );
            }

            return;
        }

        DataResult<JsonElement> encoded =
                LootTable.DIRECT_CODEC.encodeStart(ops, original);

        JsonElement encodedTable = encoded.resultOrPartial(
                error -> LOGGER.warn(
                        "[CHECK] Could not encode loot table {}: {}",
                        event.getName(),
                        error
                )
        ).orElse(null);

        if (!(encodedTable instanceof JsonObject tableJson)) {
            LOGGER.warn(
                    "[CHECK] Encoded loot table {} was not a JSON object.",
                    event.getName()
            );

            return;
        }

        JsonArray pools = getArray(tableJson, "pools");

        if (pools == null || pools.isEmpty()) {
            LOGGER.info(
                    "[CHECK] {} contains no loot pools.",
                    event.getName()
            );

            return;
        }

        int changedEntries = 0;

        for (int poolIndex = 0; poolIndex < pools.size(); poolIndex++) {

            JsonElement poolElement = pools.get(poolIndex);

            if (!(poolElement instanceof JsonObject pool)) {
                continue;
            }

            JsonArray entries = getArray(pool, "entries");

            if (entries == null) {
                continue;
            }

            int poolChanges = weightSiblingEntries(entries, settings);

            changedEntries += poolChanges;

            LOGGER.info(
                    "[CHECK] {} | pool={} | modifiedEntries={}",
                    event.getName(),
                    poolIndex,
                    poolChanges
            );
        }

        if (changedEntries == 0) {

            LOGGER.info(
                    "[CHECK] {}: no entries modified.",
                    event.getName()
            );

            return;
        }

        LOGGER.info(
                "[CHECK] {}: modified {} entries.",
                event.getName(),
                changedEntries
        );

        DataResult<LootTable> decoded =
                LootTable.DIRECT_CODEC.parse(ops, tableJson);

        LootTable transformed = decoded.resultOrPartial(
                error -> LOGGER.error(
                        "[CHECK] Could not decode transformed loot table {}: {}",
                        event.getName(),
                        error
                )
        ).orElse(null);

        if (transformed == null) {
            LOGGER.error(
                    "[CHECK] Transformation failed for {}. Keeping original table.",
                    event.getName()
            );

            return;
        }

        event.setTable(transformed);

        LOGGER.info(
                "[CHECK] Successfully applied rarity transformation to {}.",
                event.getName()
        );
    }

    private static RegistryOps<JsonElement> createSerializationOps(
            HolderGetter.Provider provider
    ) {

        if (provider instanceof PlaceholderLookupProvider placeholder) {
            return placeholder.createSerializationContext(JsonOps.INSTANCE);
        }

        if (provider instanceof HolderLookup.Provider lookup) {
            return lookup.createSerializationContext(JsonOps.INSTANCE);
        }

        throw new IllegalStateException(
                "Unsupported registry provider: "
                        + provider.getClass().getName()
        );
    }

    private static boolean isTargetLootTable(LootTable table) {

        return table.getParamSet().equals(LootContextParamSets.CHEST)
                || table.getParamSet().equals(LootContextParamSets.VAULT);
    }

    private static int weightSiblingEntries(
            JsonArray entries,
            Settings settings
    ) {

        List<JsonObject> candidates = new ArrayList<>();

        collectWeightedCandidates(entries, candidates);

        if (candidates.isEmpty()) {
            return 0;
        }

        int maxWeight = 1;

        for (JsonObject candidate : candidates) {
            maxWeight = Math.max(
                    maxWeight,
                    readWeight(candidate)
            );
        }

        int changed = 0;

        for (JsonObject candidate : candidates) {

            int weight = readWeight(candidate);

            int existingQuality = readInt(
                    candidate,
                    "quality",
                    0
            );

            int delta = calculateQualityDelta(
                    maxWeight,
                    weight,
                    settings
            );

            if (delta <= 0) {
                continue;
            }

            long updatedQuality = (long) existingQuality + delta;

            int newQuality = (int) Math.min(
                    Integer.MAX_VALUE,
                    updatedQuality
            );

            if (newQuality > existingQuality) {

                candidate.addProperty(
                        "quality",
                        newQuality
                );

                LOGGER.info(
                        "[CHECK] Entry {} | weight={} | quality {} -> {} | delta={}",
                        candidate.has("name")
                                ? candidate.get("name")
                                : "<unnamed>",
                        weight,
                        existingQuality,
                        newQuality,
                        delta
                );

                changed++;
            }
        }

        // Process composite children and inline nested loot tables separately.
        changed += transformNestedEntries(entries, settings);

        return changed;
    }

    private static void collectWeightedCandidates(
            JsonArray entries,
            List<JsonObject> output
    ) {

        for (JsonElement element : entries) {

            if (!(element instanceof JsonObject entry)) {
                continue;
            }

            if (entry.has("type") || entry.has("weight")) {
                output.add(entry);
            }
        }
    }

    private static int transformNestedEntries(
            JsonArray entries,
            Settings settings
    ) {

        int changed = 0;

        for (JsonElement element : entries) {

            if (!(element instanceof JsonObject entry)) {
                continue;
            }

            JsonArray children = getArray(entry, "children");

            if (children != null) {
                changed += weightSiblingEntries(
                        children,
                        settings
                );
            }

            JsonElement value = entry.get("value");

            if (value == null) {
                continue;
            }

            if (value instanceof JsonObject nestedTable) {

                changed += transformInlineLootTable(
                        nestedTable,
                        settings
                );

            } else if (value instanceof JsonArray nestedTables) {

                for (JsonElement nestedElement : nestedTables) {

                    if (nestedElement instanceof JsonObject nestedTable) {

                        changed += transformInlineLootTable(
                                nestedTable,
                                settings
                        );
                    }
                }
            }
        }

        return changed;
    }

    private static int transformInlineLootTable(
            JsonObject nestedTable,
            Settings settings
    ) {

        JsonArray pools = getArray(nestedTable, "pools");

        if (pools == null) {
            return 0;
        }

        int changed = 0;

        for (JsonElement poolElement : pools) {

            if (!(poolElement instanceof JsonObject pool)) {
                continue;
            }

            JsonArray entries = getArray(pool, "entries");

            if (entries != null) {
                changed += weightSiblingEntries(
                        entries,
                        settings
                );
            }
        }

        return changed;
    }

    private static int calculateQualityDelta(
            int maxWeight,
            int weight,
            Settings settings
    ) {

        if (weight <= 0 || maxWeight <= weight) {
            return 0;
        }

        double difference = (double) maxWeight - weight;

        double raw = difference
                * settings.biasStrength()
                / settings.targetLuck();

        return (int) Math.min(
                settings.maxQualityBonus(),
                Math.ceil(raw)
        );
    }

    private static int readWeight(JsonObject entry) {

        return Math.max(
                0,
                readInt(entry, "weight", 1)
        );
    }

    private static int readInt(
            JsonObject object,
            String key,
            int fallback
    ) {

        JsonElement value = object.get(key);

        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }

        try {
            return value.getAsInt();

        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static JsonArray getArray(
            JsonObject object,
            String key
    ) {

        JsonElement element = object.get(key);

        if (element == null || !element.isJsonArray()) {
            return null;
        }

        return element.getAsJsonArray();
    }

    private ChestLootRarityTransformer() {
    }
}
