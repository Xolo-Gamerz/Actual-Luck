package dev.xolo.usefulluck.loot;

/** Tracks nested LootTable#getRandomItems calls on the current thread. */
public final class LootGenerationState {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static int depth() {
        return DEPTH.get();
    }

    public static void exit() {
        int depth = DEPTH.get();
        if (depth <= 1) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth - 1);
        }
    }

    private LootGenerationState() {
    }
}
