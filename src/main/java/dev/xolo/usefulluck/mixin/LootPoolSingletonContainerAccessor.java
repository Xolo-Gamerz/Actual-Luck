package dev.xolo.usefulluck.mixin;

import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootPoolSingletonContainer.class)
public interface LootPoolSingletonContainerAccessor {
    @Accessor("weight")
    int usefulluck$getWeight();

    @Accessor("quality")
    int usefulluck$getQuality();

    @Mutable
    @Accessor("quality")
    void usefulluck$setQuality(int quality);
}
