
package dev.xolo.usefulluck.loot;

public final class LootGenerationState {

    private static final ThreadLocal<Float> PENDING_CHEST_LUCK =
            new ThreadLocal<>();

    public static void beginFill(
            boolean isChestTable,
            float luck
    ) {
        if (isChestTable) {
            PENDING_CHEST_LUCK.set(luck);
        } else {
            PENDING_CHEST_LUCK.remove();
        }
    }

    public static Float takeChestLuck() {
        Float luck = PENDING_CHEST_LUCK.get();
        PENDING_CHEST_LUCK.remove();
        return luck;
    }

    public static void clear() {
        PENDING_CHEST_LUCK.remove();
    }

    private LootGenerationState() {}
}
