
package dev.xolo.usefulluck.loot;

import dev.xolo.usefulluck.LuckConfig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.loot.LootTable;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Modifies chest loot entry quality before weighted loot selection.
 *
 * Vanilla effective weight:
 *
 *     max(floor(weight + quality * luck), 0)
 *
 * This means Luck can influence which entry gets selected,
 * rather than modifying the result after selection.
 */
public final class ChestLootRarityTransformer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("UsefulLuck/ChestRarity");

    private static final Set<String> WEIGHTED_VANILLA_TYPES = Set.of(
            "minecraft:item",
            "minecraft:empty",
            "minecraft:tag",
            "minecraft:loot_table",
            "minecraft:dynamic",
            "minecraft:slots"
    );

    private static boolean warnedAboutUnloadedConfig = false;

    /**
     * Immutable snapshot of the configuration.
     *
     * Taking one snapshot per table prevents repeated config reads
     * throughout the transformation.
     */
    private record Settings(
            boolean enabled,
            double targetLuck,
            double biasStrength,
            int maxQualityBonus
    ) {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(
                ChestLootRarityTransformer::onLootTableLoad
        );
    }

    /**
     * Reads configuration safely.
     *
     * ConfigValue.get() throws if the config has not loaded yet.
     * In that situation, use the declared defaults instead.
     */
    private static Settings getSettings() {

        if (!LuckConfig.SPEC.isLoaded()) {

            if (!warnedAboutUnloadedConfig) {
                LOGGER.warn(
                        "Useful Luck config is not loaded yet. "
                                + "Using default settings for early loot table loading."
                );

                warnedAboutUnloadedConfig = true;
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

        Settings settings = getSettings();

        if (!settings.enabled()) {
            return;
        }

        if (!(event.getRegistries() instanceof HolderLookup.Provider registries)) {

            LOGGER.warn(
                    "Cannot transform {}: loot reload did not expose HolderLookup.Provider",
                    event.getName()
            );

            return;
        }

        RegistryOps<JsonElement> ops =
                registries.createSerializationContext(JsonOps.INSTANCE);

        DataResult<JsonElement> encoded =
                LootTable.DIRECT_CODEC.encodeStart(
                        ops,
                        event.getTable()
                );

        JsonElement encodedTable = encoded.resultOrPartial(
                error -> LOGGER.warn(
                        "Could not encode loot table {} for Luck weighting: {}",
                        event.getName(),
                        error
                )
        ).orElse(null);

        if (!(encodedTable instanceof JsonObject tableJson)) {
            return;
        }

        if (!isChestTable(event, tableJson)) {
            return;
        }

        JsonArray pools = getArray(tableJson, "pools");

        if (pools == null || pools.isEmpty()) {
            return;
        }

        int changedEntries = 0;

        for (JsonElement poolElement : pools) {

            if (!(poolElement instanceof JsonObject pool)) {
                continue;
            }

            JsonArray entries = getArray(pool, "entries");

            if (entries != null) {
                changedEntries += weightSiblingEntries(entries, settings);
            }
        }

        if (changedEntries == 0) {
            return;
        }

        DataResult<LootTable> decoded =
                LootTable.DIRECT_CODEC.parse(ops, tableJson);

        LootTable transformed = decoded.resultOrPartial(
                error -> LOGGER.error(
                        "Could not decode Luck-weighted chest table {}: {}",
                        event.getName(),
                        error
                )
        ).orElse(null);

        if (transformed != null) {

            event.setTable(transformed);

            LOGGER.debug(
                    "Applied Luck rarity weighting to {} ({} weighted entries)",
                    event.getName(),
                    changedEntries
            );
        }
    }

    /**
     * Determines whether a loot table belongs to chest/vault loot.
     */
    private static boolean isChestTable(
            LootTableLoadEvent event,
            JsonObject table
    ) {

        JsonElement type = table.get("type");

        if (type != null && type.isJsonPrimitive()) {

            String typeId = type.getAsString();

            if ("minecraft:chest".equals(typeId)
                    || "minecraft:vault".equals(typeId)) {
                return true;
            }
        }

        String path = event.getName().getPath();

        return path.startsWith("chests/")
                || path.startsWith("loot_tables/chests/");
    }

    /**
     * Processes all competing entries in a pool.
     *
     * Lower-weight entries receive a larger quality bonus.
     * Composite entries are traversed recursively.
     */
    private static int weightSiblingEntries(
            JsonArray entries,
            Settings settings
    ) {

        List<JsonObject> candidates = new ArrayList<>();

        collectWeightedCandidates(entries, candidates);

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

            int newQuality = (int) Math.min(
                    (long) settings.maxQualityBonus(),
                    (long) existingQuality + delta
            );

            if (newQuality > existingQuality) {

                candidate.addProperty(
                        "quality",
                        newQuality
                );

                changed++;
            }
        }

        // Inline nested loot tables have their own independent pools.
        changed += transformNestedTables(entries, settings);

        return changed;
    }

    /**
     * Collects weighted entries, including descendants of composite entries.
     */
    private static void collectWeightedCandidates(
            JsonArray entries,
            List<JsonObject> output
    ) {

        for (JsonElement element : entries) {

            if (!(element instanceof JsonObject entry)) {
                continue;
            }

            if (isWeightedEntry(entry)) {
                output.add(entry);
            }

            JsonArray children = getArray(entry, "children");

            if (children != null) {
                collectWeightedCandidates(children, output);
            }
        }
    }

    /**
     * Processes inline nested loot tables.
     *
     * Referenced loot tables are processed independently when their
     * own LootTableLoadEvent fires.
     */
    private static int transformNestedTables(
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
                changed += transformNestedTables(children, settings);
            }

            JsonElement value = entry.get("value");

            if (value == null || !value.isJsonObject()) {
                continue;
            }

            JsonObject nestedTable = value.getAsJsonObject();

            JsonArray inlinePools = getArray(
                    nestedTable,
                    "pools"
            );

            if (inlinePools == null) {
                continue;
            }

            for (JsonElement poolElement : inlinePools) {

                if (!(poolElement instanceof JsonObject inlinePool)) {
                    continue;
                }

                JsonArray nestedEntries = getArray(
                        inlinePool,
                        "entries"
                );

                if (nestedEntries != null) {

                    changed += weightSiblingEntries(
                            nestedEntries,
                            settings
                    );
                }
            }
        }

        return changed;
    }

    /**
     * Calculates the additional quality assigned to an entry.
     */
    private static int calculateQualityDelta(
            int maxWeight,
            int weight,
            Settings settings
    ) {

        if (settings.biasStrength() <= 0.0D
                || weight <= 0
                || maxWeight <= weight) {
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

    /**
     * Checks whether an entry participates in weighted selection.
     */
    private static boolean isWeightedEntry(JsonObject entry) {

        JsonElement type = entry.get("type");

        if (type == null || !type.isJsonPrimitive()) {
            return entry.has("weight");
        }

        String id = type.getAsString();

        return entry.has("weight")
                || WEIGHTED_VANILLA_TYPES.contains(id);
    }

    /**
     * Reads an entry's base weight.
     *
     * Explicit zero-weight entries remain impossible to select.
     */
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

    /**
     * Safely retrieves a JSON array.
     */
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

    private ChestLootRarityTransformer() {}
}

