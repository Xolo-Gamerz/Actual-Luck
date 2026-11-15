
package dev.xolo.usefulluck.loot;

import dev.xolo.usefulluck.LuckConfig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
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

    public static void register() {
        NeoForge.EVENT_BUS.addListener(
                ChestLootRarityTransformer::onLootTableLoad
        );
    }

    private static void onLootTableLoad(LootTableLoadEvent event) {
        LootTable original = event.getTable();

        LuckConfig.Settings settings = LuckConfig.getSettings();

        if (!isRarityEnabled(original, settings)) {
            return;
        }

        if (!settings.enableRarityBias()
                || settings.rarityTargetLuck() <= 0.0D
                || settings.rarityBiasStrength() <= 0.0D
                || settings.maxQualityBonus() <= 0) {
            return;
        }

        RegistryOps<JsonElement> ops;

        try {
            ops = createSerializationOps(event.getRegistries());
        } catch (IllegalStateException exception) {
            LOGGER.warn(
                    "Could not create registry serialization context for {}",
                    event.getName(),
                    exception
            );
            return;
        }

        DataResult<JsonElement> encoded =
                LootTable.DIRECT_CODEC.encodeStart(ops, original);

        JsonElement encodedTable = encoded.resultOrPartial(
                error -> LOGGER.warn(
                        "Could not encode loot table {}: {}",
                        event.getName(),
                        error
                )
        ).orElse(null);

        if (!(encodedTable instanceof JsonObject tableJson)) {
            return;
        }

        JsonArray pools = getArray(tableJson, "pools");

        if (pools == null || pools.isEmpty()) {
            return;
        }

        int changed = transformPools(
                pools,
                settings.rarityTargetLuck(),
                settings.rarityBiasStrength(),
                settings.maxQualityBonus()
        );

        if (changed <= 0) {
            return;
        }

        DataResult<LootTable> decoded =
                LootTable.DIRECT_CODEC.parse(ops, tableJson);

        LootTable transformed = decoded.resultOrPartial(
                error -> LOGGER.error(
                        "Could not decode transformed loot table {}: {}",
                        event.getName(),
                        error
                )
        ).orElse(null);

        if (transformed != null) {
            event.setTable(transformed);

            LOGGER.debug(
                    "Applied Luck rarity weighting to {} ({} entries)",
                    event.getName(),
                    changed
            );
        }
    }

    private static boolean isRarityEnabled(
            LootTable table,
            LuckConfig.Settings settings
    ) {
        var paramSet = table.getParamSet();

        if (paramSet.equals(LootContextParamSets.CHEST)) {
            return settings.enableChestRarity();
        }

        if (paramSet.equals(LootContextParamSets.VAULT)) {
            return settings.enableVaultRarity();
        }

        if (paramSet.equals(LootContextParamSets.ENTITY)) {
            return settings.enableEntityRarity();
        }

        // BLOCK and BLOCK_INTERACT are intentionally excluded.
        // Ore quantity scaling is handled separately.

        return false;
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

    private static int transformPools(
            JsonArray pools,
            double targetLuck,
            double biasStrength,
            int maxQualityBonus
    ) {
        int changed = 0;

        for (JsonElement poolElement : pools) {
            if (!(poolElement instanceof JsonObject pool)) {
                continue;
            }

            JsonArray entries = getArray(pool, "entries");

            if (entries != null) {
                changed += transformEntries(
                        entries,
                        targetLuck,
                        biasStrength,
                        maxQualityBonus
                );
            }
        }

        return changed;
    }

    private static int transformEntries(
            JsonArray entries,
            double targetLuck,
            double biasStrength,
            int maxQualityBonus
    ) {
        List<JsonObject> candidates = new ArrayList<>();

        for (JsonElement element : entries) {
            if (element instanceof JsonObject entry
                    && isWeightedEntry(entry)) {
                candidates.add(entry);
            }
        }

        int maxWeight = 1;

        for (JsonObject candidate : candidates) {
            maxWeight = Math.max(
                    maxWeight,
                    readInt(candidate, "weight", 1)
            );
        }

        int changed = 0;

        for (JsonObject candidate : candidates) {
            int weight = Math.max(
                    0,
                    readInt(candidate, "weight", 1)
            );

            int quality = readInt(candidate, "quality", 0);

            if (weight > 0 && weight < maxWeight) {
                double rawDelta =
                        (maxWeight - (double) weight)
                                * biasStrength
                                / targetLuck;

                int delta = (int) Math.min(
                        maxQualityBonus,
                        Math.ceil(rawDelta)
                );

                if (delta > 0) {
                    candidate.addProperty(
                            "quality",
                            quality + delta
                    );

                    changed++;
                }
            }
        }

        for (JsonElement element : entries) {
            if (!(element instanceof JsonObject entry)) {
                continue;
            }

            JsonArray children = getArray(entry, "children");

            if (children != null) {
                changed += transformEntries(
                        children,
                        targetLuck,
                        biasStrength,
                        maxQualityBonus
                );
            }

            JsonElement value = entry.get("value");

            if (value instanceof JsonObject nestedTable) {
                changed += transformInlineTable(
                        nestedTable,
                        targetLuck,
                        biasStrength,
                        maxQualityBonus
                );
            } else if (value instanceof JsonArray nestedTables) {
                for (JsonElement nested : nestedTables) {
                    if (nested instanceof JsonObject nestedTable) {
                        changed += transformInlineTable(
                                nestedTable,
                                targetLuck,
                                biasStrength,
                                maxQualityBonus
                        );
                    }
                }
            }
        }

        return changed;
    }

    private static boolean isWeightedEntry(JsonObject entry) {
        JsonElement typeElement = entry.get("type");

        if (typeElement == null || !typeElement.isJsonPrimitive()) {
            return entry.has("quality") && entry.has("weight");
        }

        String type = typeElement.getAsString();

        return switch (type) {
            case "minecraft:item",
                 "minecraft:empty",
                 "minecraft:tag",
                 "minecraft:loot_table",
                 "minecraft:dynamic",
                 "minecraft:slots" -> true;

            default -> entry.has("quality") && entry.has("weight");
        };
    }

    private static int transformInlineTable(
            JsonObject table,
            double targetLuck,
            double biasStrength,
            int maxQualityBonus
    ) {
        JsonArray pools = getArray(table, "pools");

        if (pools == null) {
            return 0;
        }

        return transformPools(
                pools,
                targetLuck,
                biasStrength,
                maxQualityBonus
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

    private ChestLootRarityTransformer() {}
}
