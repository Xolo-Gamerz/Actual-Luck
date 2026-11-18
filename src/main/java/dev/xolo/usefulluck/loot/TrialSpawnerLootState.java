package dev.xolo.usefulluck.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

public final class TrialSpawnerLootState {
    public static float getHighestNearbyLuck(ServerLevel level, BlockPos pos, int range) {
        double maxDistanceSqr = (double) range * range;
        float maxLuck = 0.0F;

        for (Player player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }

            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > maxDistanceSqr) {
                continue;
            }

            maxLuck = Math.max(maxLuck, player.getLuck());
        }

        return maxLuck;
    }

    private TrialSpawnerLootState() {}
}
