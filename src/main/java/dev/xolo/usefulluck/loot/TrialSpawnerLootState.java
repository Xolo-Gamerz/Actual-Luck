package dev.xolo.usefulluck.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

public final class TrialSpawnerLootState {
    private static final ThreadLocal<Float> DISPENSING_LUCK = new ThreadLocal<>();

    public static float getHighestNearbyLuck(ServerLevel level, BlockPos pos, int range) {
        double maxDistanceSqr = (double) range * range;
        float maxLuck = 0.0F;

        for (Player player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }

            if (player.distanceToSqr(pos.getCenter()) > maxDistanceSqr) {
                continue;
            }

            maxLuck = Math.max(maxLuck, player.getLuck());
        }

        return maxLuck;
    }

    public static void beginDispensing(ServerLevel level, BlockPos pos, int range) {
        DISPENSING_LUCK.set(getHighestNearbyLuck(level, pos, range));
    }

    public static Float getDispensingLuck() {
        return DISPENSING_LUCK.get();
    }

    public static void clearDispensing() {
        DISPENSING_LUCK.remove();
    }

    private TrialSpawnerLootState() {}
}
