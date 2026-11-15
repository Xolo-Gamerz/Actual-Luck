package dev.xolo.usefulluck.loot;

import dev.xolo.usefulluck.LuckConfig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChestLootRarityTransformer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("UsefulLuck/ChestRarity");

    private static final AtomicBoolean WARNED_UNLOADED_CONFIG =
            new AtomicBoolean(false);

    private static final AtomicBoolean WARNED_REGISTRY_PROVIDER =
            new AtomicBoolean(false);

    /*
     * Debug information retained from the transformed loot tables.
     * It is used later when the table actually generates loot.
     */
    private static final ConcurrentMap<Identifier, List<EntrySnapshot>>
            DEBUG_SNAPSHOTS = new ConcurrentHashMap<>();

    private record Settings(
            boolean enabled,
            double targetLuck,
            double biasStrength,
            int maxQualityBonus
    ) {
    }

    private record EntrySnapshot(
            String location,
            String entry,
            int weight,
            int originalQuality,
            int modifiedQuality
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
                "[CHECK] Settings | enabled={} | targetLuck={} | biasStrength={} | maxQualityBonus={}",
                settings.enabled(),
                settings.targetLuck(),
                settings.biasStrength(),
                settings.maxQualityBonus()
        );

        if (!settings.enabled()
                || settings.biasStrength() <= 0.0D
                || settings.maxQualityBonus() <= 0) {

            LOGGER.info(
                    "[CHECK] Skipping {} because rarity modification is disabled.",
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
                        "[CHECK] Cannot create registry serialization context.",
                        exception
                );
            }

            return;
        }

        DataResult<JsonElement> encoded =
                LootTable.DIRECT_CODEC.encodeStart(ops, original);

        JsonElement encodedTable = encoded.resultOrPartial(
                error -> LOGGER.warn(
                        "[CHECK] Could not encode {}: {}",
                        event.getName(),
                        error
                )
        ).orElse(null);

        if (!(encodedTable instanceof JsonObject tableJson)) {
            LOGGER.warn(
                    "[CHECK] Encoded table {} was not a JSON object.",
                    event.getName()
            );

            return;
        }

        JsonArray pools = getArray(tableJson, "pools");

        if (pools == null || pools.isEmpty()) {
            LOGGER.info(
                    "[CHECK] {} has no loot pools.",
                    event.getName()
            );

            return;
        }

        List<EntrySnapshot> snapshots = new ArrayList<>();

        int changedEntries = 0;

        for (int i = 0; i < pools.size(); i++) {

            JsonElement poolElement = pools.get(i);

            if (!(poolElement instanceof JsonObject pool)) {
                continue;
            }

            JsonArray entries = getArray(pool, "entries");

            if (entries == null) {
                continue;
            }

            int changes = weightSiblingEntries(
                    entries,
                    settings,
                    snapshots,
                    "pool[" + i + "]"
            );

            changedEntries += changes;

            LOGGER.info(
                    "[CHECK] {} | pool={} | modifiedEntries={}",
                    event.getName(),
                    i,
                    changes
            );
        }

        if (changedEntries == 0) {

            DEBUG_SNAPSHOTS.put(
                    event.getName(),
                    List.copyOf(snapshots)
            );

            LOGGER.info(
                    "[CHECK] {}: no entries required modification.",
                    event.getName()
            );

            return;
        }

        DataResult<LootTable> decoded =
                LootTable.DIRECT_CODEC.parse(ops, tableJson);

        LootTable transformed = decoded.resultOrPartial(
                error -> LOGGER.error(
                        "[CHECK] Could not decode transformed table {}: {}",
                        event.getName(),
                        error
                )
        ).orElse(null);

        if (transformed == null) {
            LOGGER.error(
                    "[CHECK] Transformation failed for {}.",
                    event.getName()
            );

            return;
        }

        event.setTable(transformed);

        DEBUG_SNAPSHOTS.put(
                event.getName(),
                List.copyOf(snapshots)
        );

        LOGGER.info(
                "[CHECK] Successfully transformed {} | changedEntries={} | trackedEntries={}",
                event.getName(),
                changedEntries,
                snapshots.size()
        );
    }

    /*
     * Adapt the HolderGetter.Provider supplied by NeoForge's registry-loading
     * task to the RegistryInfoLookup interface required by RegistryOps.
     */
    private static RegistryOps<JsonElement> createSerializationOps(
            HolderGetter.Provider provider
    ) {

        RegistryOps.RegistryInfoLookup lookup =
                new RegistryOps.RegistryInfoLookup() {

            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<HolderGetter<T>> lookup(
                    ResourceKey<? extends Registry<? extends T>> registryKey
            ) {
                return provider.lookup(registryKey)
                        .map(getter -> (HolderGetter<T>) getter);
            }
        };

        return RegistryOps.create(JsonOps.INSTANCE, lookup);
    }

    private static boolean isTargetLootTable(LootTable table) {

        return table.getParamSet().equals(LootContextParamSets.CHEST)
                || table.getParamSet().equals(LootContextParamSets.VAULT);
    }

    private static int weightSiblingEntries(
            JsonArray entries,
            Settings settings,
            List<EntrySnapshot> snapshots,
            String location
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

        for (int i = 0; i < candidates.size(); i++) {

            JsonObject candidate = candidates.get(i);

            int weight = readWeight(candidate);

            int originalQuality = readInt(
                    candidate,
                    "quality",
                    0
            );

            int delta = calculateQualityDelta(
                    maxWeight,
                    weight,
                    settings
            );

            int modifiedQuality = originalQuality + delta;

            if (delta > 0) {

                candidate.addProperty(
                        "quality",
                        modifiedQuality
                );

                LOGGER.info(
                        "[CHECK] Entry {} | weight={} | quality {} -> {} | delta={}",
                        describeEntry(candidate),
                        weight,
                        originalQuality,
                        modifiedQuality,
                        delta
                );

                changed++;
            }

            snapshots.add(
                    new EntrySnapshot(
                            location,
                            describeEntry(candidate),
                            weight,
                            originalQuality,
                            modifiedQuality
                    )
            );
        }

        changed += transformNestedEntries(
                entries,
                settings,
                snapshots,
                location
        );

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
            Settings settings,
            List<EntrySnapshot> snapshots,
            String parentLocation
    ) {

        int changed = 0;

        for (int i = 0; i < entries.size(); i++) {

            JsonElement element = entries.get(i);

            if (!(element instanceof JsonObject entry)) {
                continue;
            }

            String location = parentLocation + "/entry[" + i + "]";

            JsonArray children = getArray(entry, "children");

            if (children != null) {
                changed += weightSiblingEntries(
                        children,
                        settings,
                        snapshots,
                        location + "/children"
                );
            }

            JsonElement value = entry.get("value");

            if (value instanceof JsonObject nestedTable) {

                changed += transformInlineLootTable(
                        nestedTable,
                        settings,
                        snapshots,
                        location + "/value"
                );

            } else if (value instanceof JsonArray nestedTables) {

                for (int j = 0; j < nestedTables.size(); j++) {

                    JsonElement nestedElement = nestedTables.get(j);

                    if (nestedElement instanceof JsonObject nestedTable) {

                        changed += transformInlineLootTable(
                                nestedTable,
                                settings,
                                snapshots,
                                location + "/value[" + j + "]"
                        );
                    }
                }
            }
        }

        return changed;
    }

    private static int transformInlineLootTable(
            JsonObject nestedTable,
            Settings settings,
            List<EntrySnapshot> snapshots,
            String location
    ) {

        JsonArray pools = getArray(nestedTable, "pools");

        if (pools == null) {
            return 0;
        }

        int changed = 0;

        for (int i = 0; i < pools.size(); i++) {

            JsonElement poolElement = pools.get(i);

            if (!(poolElement instanceof JsonObject pool)) {
                continue;
            }

            JsonArray entries = getArray(pool, "entries");

            if (entries != null) {
                changed += weightSiblingEntries(
                        entries,
                        settings,
                        snapshots,
                        location + "/pool[" + i + "]"
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

    private static int effectiveWeight(
            int weight,
            int quality,
            float luck
    ) {

        double result = Math.floor(
                weight + (double) quality * luck
        );

        if (result <= 0.0D) {
            return 0;
        }

        return (int) Math.min(
                Integer.MAX_VALUE,
                result
        );
    }

    /*
     * Called by the LootTable Mixin immediately before generation.
     */
    public static void logGeneration(
            Identifier tableId,
            float luck
    ) {

        if (tableId == null) {
            return;
        }

        List<EntrySnapshot> snapshots =
                DEBUG_SNAPSHOTS.get(tableId);

        if (snapshots == null) {
            return;
        }

        LOGGER.info("==================================================");
        LOGGER.info("[CHEST-LOOT] GENERATING TABLE: {}", tableId);
        LOGGER.info("[CHEST-LOOT] ACTUAL CONTEXT LUCK: {}", luck);
        LOGGER.info("[CHEST-LOOT] Comparing original and modified weights:");

        for (EntrySnapshot snapshot : snapshots) {

            int originalAtLuck = effectiveWeight(
                    snapshot.weight(),
                    snapshot.originalQuality(),
                    luck
            );

            int modifiedAtLuck = effectiveWeight(
                    snapshot.weight(),
                    snapshot.modifiedQuality(),
                    luck
            );

            int modifiedAtZero = effectiveWeight(
                    snapshot.weight(),
                    snapshot.modifiedQuality(),
                    0.0F
            );

            LOGGER.info(
                    "[CHEST-LOOT] {} | {} | baseWeight={} | originalQuality={} | modifiedQuality={}",
                    snapshot.location(),
                    snapshot.entry(),
                    snapshot.weight(),
                    snapshot.originalQuality(),
                    snapshot.modifiedQuality()
            );

            LOGGER.info(
                    "[CHEST-LOOT]    Original effective weight @ Luck {}: {}",
                    luck,
                    originalAtLuck
            );

            LOGGER.info(
                    "[CHEST-LOOT]    Modified effective weight @ Luck {}: {}",
                    luck,
                    modifiedAtLuck
            );

            LOGGER.info(
                    "[CHEST-LOOT]    Modified effective weight @ Luck 0: {}",
                    modifiedAtZero
            );
        }

        LOGGER.info("==================================================");
    }

    /*
     * Called by the LootTable Mixin after generation.
     */
    public static void logGeneratedItems(
            Identifier tableId,
            List<ItemStack> generatedItems
    ) {

        if (tableId == null || !DEBUG_SNAPSHOTS.containsKey(tableId)) {
            return;
        }

        LOGGER.info("[CHEST-LOOT] GENERATED ITEMS FROM {}:", tableId);

        if (generatedItems == null || generatedItems.isEmpty()) {
            LOGGER.info("[CHEST-LOOT]    <no items generated>");
            return;
        }

        for (ItemStack stack : generatedItems) {

            LOGGER.info(
                    "[CHEST-LOOT]    {} x{}",
                    stack.getItem(),
                    stack.getCount()
            );
        }
    }

    private static String describeEntry(JsonObject entry) {

        for (String key : List.of("name", "value", "items", "type")) {

            JsonElement value = entry.get(key);

            if (value != null && value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }

        return "<unnamed>";
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
