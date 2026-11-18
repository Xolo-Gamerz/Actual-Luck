package dev.xolo.usefulluck.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;

public final class TrialSpawnerLootState {
    public record Context(float luck, boolean ominous) {}

    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();

    public static float getHighestNearbyLuck(ServerLevel level, BlockPos pos, int range) {
        double maxDistanceSqr = (double) range * range;
        float maxLuck = 0.0F;

        for (Player player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) continue;
            if (player.distanceToSqr(pos.getCenter()) > maxDistanceSqr) continue;
            maxLuck = Math.max(maxLuck, player.getLuck());
        }

        return maxLuck;
    }

    public static void begin(ServerLevel level, BlockPos pos, TrialSpawner spawner) {
        ACTIVE.set(new Context(
                getHighestNearbyLuck(level, pos, spawner.getRequiredPlayerRange()),
                spawner.isOminous()
        ));
    }

    public static Context get() {
        return ACTIVE.get();
    }

    public static void clear() {
        ACTIVE.remove();
    }

    private TrialSpawnerLootState() {}
}
