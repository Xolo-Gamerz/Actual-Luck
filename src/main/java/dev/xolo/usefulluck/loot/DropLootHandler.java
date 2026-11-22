package dev.xolo.usefulluck.loot;

import dev.xolo.usefulluck.LuckConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

public final class DropLootHandler {
    public static void register() {
        NeoForge.EVENT_BUS.addListener(DropLootHandler::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(DropLootHandler::onBlockDrops);
    }

    private static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player) return;
        LuckConfig.Settings settings = LuckConfig.getSettings();
        if (!settings.enableEntityQuantity()) return;
        Player killer = getResponsiblePlayer(event);
        if (killer == null) return;

        LootScaling.scaleItemEntities(event.getDrops(), event.getEntity().level(), killer.getLuck(), settings);
    }

    private static void onBlockDrops(BlockDropsEvent event) {
        LuckConfig.Settings settings = LuckConfig.getSettings();
        if (!settings.enableBlockQuantity()) return;
        if (!event.getState().is(Tags.Blocks.ORES)) return;
        if (!(event.getBreaker() instanceof Player player)) return;

        LootScaling.scaleItemEntities(event.getDrops(), event.getLevel(), player.getLuck(), settings);
    }

    private static Player getResponsiblePlayer(LivingDropsEvent event) {
        Entity source = event.getSource().getEntity();
        if (source instanceof Player player) return player;

        // getKillCredit preserves vanilla's recent-player-hit attribution.
        return event.getEntity().getKillCredit() instanceof Player player ? player : null;
    }

    private DropLootHandler() {}
}
