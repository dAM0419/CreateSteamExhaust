package com.ssw.steamexhaust.content;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.ssw.steamexhaust.compat.AeroCompat;
import com.ssw.steamexhaust.config.SEConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 일반·직선 배기 파이프가 함께 쓰는 연결 판정과 모양. */
public final class ExhaustPipes {
    private ExhaustPipes() {
    }

    // 배기 파이프는 12px(2~14), Create 유체 파이프는 8px(4~12).
    private static final VoxelShape CORE = Block.box(2, 2, 2, 14, 14, 14);
    private static final VoxelShape[] ARMS = new VoxelShape[6];
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (Direction d : Direction.values()) {
            ARMS[d.get3DDataValue()] = switch (d) {
                case DOWN -> Block.box(2, 0, 2, 14, 2, 14);
                case UP -> Block.box(2, 14, 2, 14, 16, 14);
                case NORTH -> Block.box(2, 2, 0, 14, 14, 2);
                case SOUTH -> Block.box(2, 2, 14, 14, 14, 16);
                case WEST -> Block.box(0, 2, 2, 2, 14, 14);
                case EAST -> Block.box(14, 2, 2, 16, 14, 14);
            };
        }
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = CORE;
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.get3DDataValue())) != 0) {
                    shape = Shapes.or(shape, ARMS[d.get3DDataValue()]);
                }
            }
            SHAPES[mask] = shape.optimize();
        }
    }

    public static VoxelShape shape(int extendMask) {
        return SHAPES[extendMask & 63];
    }

    public static boolean isExhaust(BlockState state) {
        return state.getBlock() instanceof ExhaustPipeBlock || state.getBlock() instanceof StraightExhaustPipeBlock;
    }

    /** 어떤 배기 파이프 블록이든, 그 면의 상태. 배기 파이프가 아니면 NONE. */
    public static ExhaustConnection faceOf(BlockState state, Direction side) {
        if (state.getBlock() instanceof ExhaustPipeBlock) {
            return state.getValue(ExhaustPipeBlock.property(side));
        }
        if (state.getBlock() instanceof StraightExhaustPipeBlock) {
            return StraightExhaustPipeBlock.endValue(state, side);
        }
        return ExhaustConnection.NONE;
    }

    /**
     * pos에 있는 배기 파이프가 방향 d의 이웃과 어떻게 연결되는지.
     * Create 파이프의 연결 판정(FluidPipeBlock.canConnectTo)을 그대로 쓰고, 배기 파이프끼리는 EXHAUST로 구분한다.
     */
    public static ExhaustConnection connectionToward(LevelAccessor level, BlockPos pos, Direction d) {
        BlockPos neighbourPos = pos.relative(d);
        BlockState neighbour = level.getBlockState(neighbourPos);
        Block block = neighbour.getBlock();
        if (block instanceof ExhaustPipeBlock) {
            return ExhaustConnection.EXHAUST;
        }
        if (block instanceof StraightExhaustPipeBlock) {
            return neighbour.getValue(StraightExhaustPipeBlock.AXIS) == d.getAxis()
                    ? ExhaustConnection.EXHAUST
                    : ExhaustConnection.NONE;
        }
        if (d == Direction.UP && SEConfig.pipeFedVents() && AeroCompat.isSteamVent(neighbour)) {
            return ExhaustConnection.PIPE;
        }
        if (FluidPipeBlock.canConnectTo(level, neighbourPos, neighbour, d)) {
            return ExhaustConnection.PIPE;
        }
        return ExhaustConnection.NONE;
    }
}
