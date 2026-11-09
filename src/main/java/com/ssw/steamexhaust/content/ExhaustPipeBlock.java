package com.ssw.steamexhaust.content;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.ssw.steamexhaust.SERegistry;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 배기 파이프. 설치·연결·렌치 동작은 Create 유체 파이프(FluidPipeBlock)를 그대로 따른다.
 * 연결이 1개뿐이면 반대편이 열린 끝(OPEN)이 되고, 그것이 증기 라인 위에 있으면 출구다.
 */
public class ExhaustPipeBlock extends Block implements EntityBlock, SimpleWaterloggedBlock, IWrenchable {
    public static final MapCodec<ExhaustPipeBlock> CODEC = simpleCodec(ExhaustPipeBlock::new);

    public static final EnumProperty<ExhaustConnection> NORTH = EnumProperty.create("north", ExhaustConnection.class);
    public static final EnumProperty<ExhaustConnection> EAST = EnumProperty.create("east", ExhaustConnection.class);
    public static final EnumProperty<ExhaustConnection> SOUTH = EnumProperty.create("south", ExhaustConnection.class);
    public static final EnumProperty<ExhaustConnection> WEST = EnumProperty.create("west", ExhaustConnection.class);
    public static final EnumProperty<ExhaustConnection> UP = EnumProperty.create("up", ExhaustConnection.class);
    public static final EnumProperty<ExhaustConnection> DOWN = EnumProperty.create("down", ExhaustConnection.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final Map<Direction, EnumProperty<ExhaustConnection>> PROPERTY_BY_DIRECTION = new EnumMap<>(Direction.class);

    static {
        PROPERTY_BY_DIRECTION.put(Direction.NORTH, NORTH);
        PROPERTY_BY_DIRECTION.put(Direction.EAST, EAST);
        PROPERTY_BY_DIRECTION.put(Direction.SOUTH, SOUTH);
        PROPERTY_BY_DIRECTION.put(Direction.WEST, WEST);
        PROPERTY_BY_DIRECTION.put(Direction.UP, UP);
        PROPERTY_BY_DIRECTION.put(Direction.DOWN, DOWN);
    }

    public ExhaustPipeBlock(Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any().setValue(WATERLOGGED, false);
        for (Direction d : Direction.values()) {
            state = state.setValue(property(d), ExhaustConnection.NONE);
        }
        registerDefaultState(state);
    }

    public static EnumProperty<ExhaustConnection> property(Direction direction) {
        return PROPERTY_BY_DIRECTION.get(direction);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, WATERLOGGED);
    }

    // ---------------------------------------------------------------- 연결 (Create FluidPipeBlock.updateBlockState 대응)

    /**
     * Create 6.0.10 FluidPipeBlock.updateBlockState와 같은 규칙.
     * <ol>
     *   <li>ignore를 뺀 모든 면을 이웃 기준으로 다시 계산한다.</li>
     *   <li>관이 뻗은 면이 1개면 반대편을 열린 끝으로 둔다.</li>
     *   <li>0개면 직전에 직선(2개)이었으면 그 모양을 유지하고, 아니면 preferred 축으로 양쪽을 연다.</li>
     * </ol>
     */
    public static BlockState updateState(BlockState state, Direction preferred, @Nullable Direction ignore,
                                         LevelAccessor level, BlockPos pos) {
        BlockState prev = state;
        int prevExtending = countExtending(prev);

        for (Direction d : Direction.values()) {
            if (d != ignore) {
                state = state.setValue(property(d), ExhaustPipes.connectionToward(level, pos, d));
            }
        }

        int extending = 0;
        Direction only = null;
        for (Direction d : Direction.values()) {
            if (state.getValue(property(d)).extends_()) {
                extending++;
                only = d;
            }
        }

        if (extending == 1) {
            Direction opposite = only.getOpposite();
            if (state.getValue(property(opposite)) == ExhaustConnection.NONE) {
                state = state.setValue(property(opposite), ExhaustConnection.OPEN);
            }
            return state;
        }
        if (extending >= 2) {
            return state;
        }
        if (prevExtending == 2) {
            // 아무것도 연결되지 않았다: 직전의 직선 모양을 유지하되, 남아 있던 연결 표시는 열린 끝으로 바꾼다.
            BlockState kept = prev;
            for (Direction d : Direction.values()) {
                if (kept.getValue(property(d)).isConnected()) {
                    kept = kept.setValue(property(d), ExhaustConnection.OPEN);
                }
            }
            return kept;
        }
        return state.setValue(property(preferred), ExhaustConnection.OPEN)
                .setValue(property(preferred.getOpposite()), ExhaustConnection.OPEN);
    }

    private static int countExtending(BlockState state) {
        int n = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(property(d)).extends_()) {
                n++;
            }
        }
        return n;
    }

    /** 정확히 마주보는 두 면만 뻗어 있으면 그 축 (Create FluidPropagator.getStraightPipeAxis 대응). */
    @Nullable
    public static Direction.Axis straightAxis(BlockState state) {
        Direction.Axis found = null;
        for (Direction.Axis axis : Direction.Axis.values()) {
            Direction pos = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            Direction neg = pos.getOpposite();
            boolean p = state.getValue(property(pos)).extends_();
            boolean n = state.getValue(property(neg)).extends_();
            if (p && n) {
                if (found != null) {
                    return null;
                }
                found = axis;
            } else if (p || n) {
                return null;
            }
        }
        return found;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        FluidState fluid = level.getFluidState(pos);
        return updateState(defaultBlockState(), context.getNearestLookingDirection(), null, level, pos)
                .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return updateState(state, direction, direction.getOpposite(), level, pos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    // ---------------------------------------------------------------- 모양

    public static int extendMask(BlockState state) {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(property(d)).extends_()) {
                mask |= 1 << d.get3DDataValue();
            }
        }
        return mask;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ExhaustPipes.shape(extendMask(state));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ---------------------------------------------------------------- 블록 엔티티

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExhaustPipeBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------- 렌치 (Create FluidPipeBlock.onWrenched 대응)

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        Direction.Axis axis = straightAxis(state);

        if (axis == null) {
            Vec3 click = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            double closest = Double.MAX_VALUE;
            Direction best = Direction.UP;
            for (Direction direction : Direction.values()) {
                if (clickedFace.getAxis() == direction.getAxis()) {
                    continue;
                }
                Vec3 centre = Vec3.atCenterOf(direction.getNormal());
                double distance = centre.distanceToSqr(click);
                if (distance < closest) {
                    closest = distance;
                    best = direction;
                }
            }
            axis = best.getAxis();
        }

        if (clickedFace.getAxis() == axis) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            BlockState straight = SERegistry.STRAIGHT_EXHAUST_PIPE.get().defaultBlockState()
                    .setValue(StraightExhaustPipeBlock.AXIS, axis)
                    .setValue(StraightExhaustPipeBlock.WATERLOGGED, state.getValue(WATERLOGGED));
            straight = StraightExhaustPipeBlock.updateState(straight, null, level, pos);
            level.setBlockAndUpdate(pos, straight);
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
