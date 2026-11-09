package com.ssw.steamexhaust;

import com.ssw.steamexhaust.content.ExhaustPipeBlock;
import com.ssw.steamexhaust.content.ExhaustPipeBlockEntity;
import com.ssw.steamexhaust.content.StraightExhaustPipeBlock;
import com.ssw.steamexhaust.content.SteamPlumeOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SERegistry {
    private SERegistry() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SteamExhaust.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SteamExhaust.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SteamExhaust.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, SteamExhaust.MOD_ID);

    private static BlockBehaviour.Properties pipeProperties() {
        // Create 유체 파이프와 같은 계열(구리 금속). 완전한 블록이 아니므로 noOcclusion.
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE)
                .strength(1.5F, 6.0F)
                .sound(SoundType.COPPER)
                .noOcclusion()
                .forceSolidOn();
    }

    public static final DeferredBlock<ExhaustPipeBlock> EXHAUST_PIPE =
            BLOCKS.register("exhaust_pipe", () -> new ExhaustPipeBlock(pipeProperties()));

    public static final DeferredBlock<StraightExhaustPipeBlock> STRAIGHT_EXHAUST_PIPE =
            BLOCKS.register("straight_exhaust_pipe", () -> new StraightExhaustPipeBlock(pipeProperties()));

    public static final DeferredItem<BlockItem> EXHAUST_PIPE_ITEM = ITEMS.registerSimpleBlockItem(EXHAUST_PIPE);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExhaustPipeBlockEntity>> EXHAUST_PIPE_BE =
            BLOCK_ENTITIES.register("exhaust_pipe", () -> BlockEntityType.Builder
                    .of(ExhaustPipeBlockEntity::new, EXHAUST_PIPE.get(), STRAIGHT_EXHAUST_PIPE.get())
                    .build(null));

    /** 증기 덩어리. 세기(strength)에 따라 크기·수명이 연속적으로 변한다. */
    public static final DeferredHolder<ParticleType<?>, SteamPlumeOptions.Type> STEAM_PLUME =
            PARTICLES.register("steam_plume", SteamPlumeOptions.Type::new);

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        PARTICLES.register(modBus);
    }
}
