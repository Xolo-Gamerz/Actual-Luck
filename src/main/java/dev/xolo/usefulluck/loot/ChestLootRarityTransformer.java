package dev.xolo.usefulluck.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.xolo.usefulluck.LuckConfig;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ChestLootRarityTransformer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("UsefulLuck/Rarity");

    private static final int MAX_INLINE_DEPTH = 16;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(
                ChestLootRarityTransformer::onLootTableLoad
        );

        LOGGER.info(
                "Useful Luck chest-loot rarity transformer registered."
        );
    }

    private static void onLootTableLoad(LootTableLoadEvent event) {
        LuckConfig.Settings settings = LuckConfig.getSettings();

        if (!settings.enabled()
                || !settings.rarityEnabled()
                || settings.rarityBiasStrength() <= 0.0D
                || settings.maxQualityBonus() <= 0) {
            return;
        }

        LootTable original = event.getTable();

        if (!isChestOrVaultTable(original)) {
            return;
        }

        try {
            RegistryOps<JsonElement> ops =
                    createSerializationOps(event.getRegistries());

            DataResult<JsonElement> encoded =
                    LootTable.DIRECT_CODEC.encodeStart(ops, original);

            JsonElement encodedElement = encoded.resultOrPartial(error ->
                    LOGGER.warn(
                            "Could not encode loot table {} for rarity adjustment: {}",
                            event.getName(),
                            error
                    )
            ).orElse(null);

            if (!(encodedElement instanceof JsonObject tableJson)) {
                return;
            }

            int changedEntries =
                    transformLootTable(tableJson, settings, 0);

            if (changedEntries == 0) {
                return;
            }

            DataResult<LootTable> decoded =
                    LootTable.DIRECT_CODEC.parse(ops, tableJson);

            LootTable transformed = decoded.resultOrPartial(error ->
                    LOGGER.error(
                            "Could not decode rarity-adjusted loot table {}: {}",
                            event.getName(),
                            error
                    )
            ).orElse(null);

            if (transformed != null) {
                event.setTable(transformed);

                LOGGER.debug(
                        "Applied rarity bias to {} weighted entries in {}.",
                        changedEntries,
                        event.getName()
                );
            }

        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not apply rarity bias to {}. Leaving the table unchanged.",
                    event.getName(),
                    exception
            );
        }
    }

    private static boolean isChestOrVaultTable(LootTable table) {
        return table.getParamSet().equals(LootContextParamSets.CHEST)
                || table.getParamSet().equals(LootContextParamSets.VAULT);
    }

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

    private static int transformLootTable(
            JsonObject table,
            LuckConfig.Settings settings,
            int depth
    ) {
        if (depth > MAX_INLINE_DEPTH) {
            return 0;
        }

        JsonArray pools = getArray(table, "pools");

        if (pools == null || pools.isEmpty()) {
            return 0;
        }

        int changed = 0;

        for (JsonElement poolElement : pools) {
            if (!(poolElement instanceof JsonObject pool)) {
                continue;
            }

            JsonArray entries = getArray(pool, "entries");

            if (entries != null) {
                changed += transformSiblingEntries(
                        entries,
                        settings,
                        depth
                );
            }
        }

        return changed;
    }

    private static int transformSiblingEntries(
            JsonArray entries,
            LuckConfig.Settings settings,
            int depth
    ) {
        if (depth > MAX_INLINE_DEPTH) {
            return 0;
        }

        List<JsonObject> weightedEntries = new ArrayList<>();

        collectSupportedWeightedEntries(entries, weightedEntries);

        int changed = 0;

        if (!weightedEntries.isEmpty()) {
            int heaviestWeight = 1;

            for (JsonObject entry : weightedEntries) {
                heaviestWeight = Math.max(
                        heaviestWeight,
                        readWeight(entry)
                );
            }

            for (JsonObject entry : weightedEntries) {
                int weight = readWeight(entry);

                int bonus = calculateQualityBonus(
                        heaviestWeight,
                        weight,
                        settings
                );

                if (bonus <= 0) {
                    continue;
                }

                int oldQuality = readInt(entry, "quality", 0);

                long adjusted = (long) oldQuality + bonus;

                int newQuality = (int) Math.max(
                        Integer.MIN_VALUE,
                        Math.min(Integer.MAX_VALUE, adjusted)
                );

                if (newQuality != oldQuality) {
                    entry.addProperty("quality", newQuality);
                    changed++;
                }
            }
        }

        // Recurse into composite entries and inline nested loot tables.
        for (JsonElement element : entries) {
            if (!(element instanceof JsonObject entry)) {
                continue;
            }

            JsonArray children = getArray(entry, "children");

            if (children != null) {
                changed += transformSiblingEntries(
                        children,
                        settings,
                        depth + 1
                );
            }

            JsonElement value = entry.get("value");

            if (value instanceof JsonObject nestedTable) {
                changed += transformLootTable(
                        nestedTable,
                        settings,
                        depth + 1
                );

            } else if (value instanceof JsonArray nestedTables) {
                for (JsonElement nestedElement : nestedTables) {
                    if (nestedElement instanceof JsonObject nestedTable) {
                        changed += transformLootTable(
                                nestedTable,
                                settings,
                                depth + 1
                        );
                    }
                }
            }
        }

        return changed;
    }

    private static void collectSupportedWeightedEntries(
            JsonArray entries,
            List<JsonObject> output
    ) {
        for (JsonElement element : entries) {
            if (!(element instanceof JsonObject entry)
                    || entry.has("children")) {
                continue;
            }

            String type = readString(entry, "type", "");

            boolean standardWeightedEntry =
                    type.equals("minecraft:item")
                    || type.equals("minecraft:tag")
                    || type.equals("minecraft:loot_table")
                    || type.equals("minecraft:empty");

            // Unknown custom codecs are only modified if they explicitly
            // serialize a quality property.
            if (standardWeightedEntry || entry.has("quality")) {
                output.add(entry);
            }
        }
    }

    private static int calculateQualityBonus(
            int heaviestWeight,
            int weight,
            LuckConfig.Settings settings
    ) {
        if (weight <= 0 || heaviestWeight <= weight) {
            return 0;
        }

        double gap = (double) heaviestWeight - weight;

        double raw = gap
                * settings.rarityBiasStrength()
                / settings.rarityTargetLuck();

        if (!Double.isFinite(raw) || raw <= 0.0D) {
            return 0;
        }

        return (int) Math.min(
                settings.maxQualityBonus(),
                Math.ceil(raw)
        );
    }

    private static int readWeight(JsonObject entry) {
        return Math.max(0, readInt(entry, "weight", 1));
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
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String readString(
            JsonObject object,
            String key,
            String fallback
    ) {
        JsonElement value = object.get(key);

        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }

        try {
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static JsonArray getArray(
            JsonObject object,
            String key
    ) {
        JsonElement value = object.get(key);

        return value != null && value.isJsonArray()
                ? value.getAsJsonArray()
                : null;
    }

    private ChestLootRarityTransformer() {
    }
}