package dev.xolo.usefulluck.loot;

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
 * Adds native LootPool entry quality to chest tables before those tables are
 * frozen/rolled. Vanilla then applies its normal effective-weight formula:
 *
 *     effectiveWeight = max(floor(weight + quality * contextLuck), 0)
 *
 * Unlike a GLM, this changes the weighted selection itself. It runs once per
 * table load/reload, not once per chest opening.
 */
public final class ChestLootRarityTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger("UsefulLuck/ChestRarity");

    private static final Set<String> WEIGHTED_VANILLA_TYPES = Set.of(
            "minecraft:item",
            "minecraft:empty",
            "minecraft:tag",
            "minecraft:loot_table",
            "minecraft:dynamic",
            "minecraft:slots"
    );

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ChestLootRarityTransformer::onLootTableLoad);
    }

    private static void onLootTableLoad(LootTableLoadEvent event) {
        if (!LuckConfig.ENABLE_CHEST_RARITY.get()) return;

        // LootTableLoadEvent intentionally exposes HolderGetter.Provider.
        // RegistryOps needs the richer lookup provider, which is supplied by
        // NeoForge's reload pipeline for normal datapack table deserialization.
        if (!(event.getRegistries() instanceof HolderLookup.Provider registries)) {
            LOGGER.warn("Cannot transform {}: loot reload did not expose HolderLookup.Provider", event.getName());
            return;
        }

        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        DataResult<JsonElement> encoded = LootTable.DIRECT_CODEC.encodeStart(ops, event.getTable());
        JsonElement encodedTable = encoded.resultOrPartial(
                error -> LOGGER.warn("Could not encode loot table {} for Luck weighting: {}", event.getName(), error)
        ).orElse(null);

        if (!(encodedTable instanceof JsonObject tableJson)) return;
        if (!isChestTable(event, tableJson)) return;

        JsonArray pools = tableJson.getAsJsonArray("pools");
        if (pools == null || pools.isEmpty()) return;

        int changedEntries = 0;
        for (JsonElement poolElement : pools) {
            if (!(poolElement instanceof JsonObject pool)) continue;
            JsonArray entries = pool.getAsJsonArray("entries");
            if (entries != null) changedEntries += weightSiblingEntries(entries);

        }

        if (changedEntries == 0) return;

        DataResult<LootTable> decoded = LootTable.DIRECT_CODEC.parse(ops, tableJson);
        LootTable transformed = decoded.resultOrPartial(
                error -> LOGGER.error("Could not decode Luck-weighted chest table {}: {}", event.getName(), error)
        ).orElse(null);

        if (transformed != null) {
            event.setTable(transformed);
            LOGGER.debug("Applied Luck rarity weighting to {} ({} weighted entries)",
                    event.getName(), changedEntries);
        }
    }

    private static boolean isChestTable(LootTableLoadEvent event, JsonObject table) {
        JsonElement type = table.get("type");
        if (type != null && type.isJsonPrimitive()) {
            String typeId = type.getAsString();
            if ("minecraft:chest".equals(typeId) || "minecraft:vault".equals(typeId)) return true;
        }

        // Compatibility fallback for datapacks/mods that omit the explicit
        // context type but follow the conventional chest loot-table path.
        String path = event.getName().getPath();
        return path.startsWith("chests/") || path.startsWith("loot_tables/chests/");
    }

    /**
     * For each competing sibling group, use the maximum base weight as the
     * reference and add quality inversely proportional to each entry's weight.
     * Composite entries are recursively processed through their children.
     */
    private static int weightSiblingEntries(JsonArray entries) {
        // Composite group/sequence/alternatives entries expand their children
        // into the same candidate pool. Compare all descendants against the
        // same pool-wide maximum instead of biasing each composite in isolation.
        List<JsonObject> candidates = new ArrayList<>();
        collectWeightedCandidates(entries, candidates);

        int maxWeight = 1;
        for (JsonObject candidate : candidates) {
            maxWeight = Math.max(maxWeight, readWeight(candidate));
        }

        int changed = 0;
        for (JsonObject candidate : candidates) {
            int weight = readWeight(candidate);
            int existingQuality = readInt(candidate, "quality", 0);
            int delta = calculateQualityDelta(maxWeight, weight);
            if (delta <= 0) continue;

            int newQuality = (int) Math.min(
                    (long) LuckConfig.MAX_QUALITY_BONUS.get(),
                    (long) existingQuality + delta
            );
            if (newQuality > existingQuality) {
                candidate.addProperty("quality", newQuality);
                changed++;
            }
        }

        // Inline nested loot tables own independent pools and therefore get
        // their own weight baselines. Referenced tables are transformed when
        // their own LootTableLoadEvent fires.
        changed += transformNestedTables(entries);
        return changed;
    }

    private static void collectWeightedCandidates(JsonArray entries, List<JsonObject> output) {
        for (JsonElement element : entries) {
            if (!(element instanceof JsonObject entry)) continue;

            if (isWeightedEntry(entry)) output.add(entry);

            JsonArray children = entry.getAsJsonArray("children");
            if (children != null) collectWeightedCandidates(children, output);
        }
    }

    private static int transformNestedTables(JsonArray entries) {
        int changed = 0;
        for (JsonElement element : entries) {
            if (!(element instanceof JsonObject entry)) continue;

            JsonArray children = entry.getAsJsonArray("children");
            if (children != null) changed += transformNestedTables(children);

            JsonElement value = entry.get("value");
            if (value != null && value.isJsonObject()) {
                JsonArray inlinePools = value.getAsJsonObject().getAsJsonArray("pools");
                if (inlinePools != null) {
                    for (JsonElement poolElement : inlinePools) {
                        if (poolElement instanceof JsonObject inlinePool) {
                            JsonArray nestedEntries = inlinePool.getAsJsonArray("entries");
                            if (nestedEntries != null) changed += weightSiblingEntries(nestedEntries);
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static int calculateQualityDelta(int maxWeight, int weight) {
        if (LuckConfig.BIAS_STRENGTH.get() <= 0.0D || weight <= 0 || maxWeight <= weight) return 0;

        double difference = (double) maxWeight - weight;
        double raw = difference * LuckConfig.BIAS_STRENGTH.get()
                / LuckConfig.TARGET_LUCK.get();
        int cap = LuckConfig.MAX_QUALITY_BONUS.get();
        return (int) Math.min(cap, Math.ceil(raw));
    }

    private static boolean isWeightedEntry(JsonObject entry) {
        JsonElement type = entry.get("type");
        if (type == null || !type.isJsonPrimitive()) return entry.has("weight");
        String id = type.getAsString();
        return entry.has("weight") || WEIGHTED_VANILLA_TYPES.contains(id);
    }

    private static int readWeight(JsonObject entry) {
        // A deliberately zero-weight entry must remain impossible to select.
        return Math.max(0, readInt(entry, "weight", 1));
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return fallback;
        try {
            return value.getAsInt();
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private ChestLootRarityTransformer() {}
}
