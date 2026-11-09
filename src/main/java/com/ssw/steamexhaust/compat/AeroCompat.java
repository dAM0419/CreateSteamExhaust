package com.ssw.steamexhaust.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/** Create Aeronautics steam vent 감지. Aeronautics 클래스에 직접 의존하지 않는다. */
public final class AeroCompat {
    private AeroCompat() {
    }

    public static final ResourceLocation STEAM_VENT_ID = ResourceLocation.fromNamespaceAndPath("aeronautics", "steam_vent");

    private static volatile Block ventBlock;
    private static volatile boolean resolved;

    private static Block vent() {
        if (!resolved) {
            Block block = null;
            if (ModList.get() != null && ModList.get().isLoaded("aeronautics")) {
                Block found = BuiltInRegistries.BLOCK.get(STEAM_VENT_ID);
                if (found != Blocks.AIR) {
                    block = found;
                }
            }
            ventBlock = block;
            resolved = true;
        }
        return ventBlock;
    }

    public static boolean isSteamVent(BlockState state) {
        Block vent = vent();
        return vent != null && state.is(vent);
    }
}
