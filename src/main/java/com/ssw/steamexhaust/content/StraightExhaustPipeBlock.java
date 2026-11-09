package com.ssw.steamexhaust.content;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.ssw.steamexhaust.SERegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 직선 배기 파이프. Create 유리 파이프(GlassFluidPipeBlock)에 대응한다:
 * 축이 고정되어 그 축 방향으로만 연결되고, 렌치로 치면 일반 배기 파이프로 돌아간다.
 * 양 끝은 항상 열려 있으며(OPEN/EXHAUST/PIPE), 한쪽만 연결되면 반대쪽이 출구가 된다.
 */
public class StraightExhaustPipeBlock extends Block implements EntityBlock, SimpleWaterloggedBlock, IWrenchable {
    public static final MapCodec<StraightExhaustPipeBlock> CODEC = simpleCodec(StraightExhaustPipeBlock::new);

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<ExhaustConnection> POSITIVE =
            EnumProperty.create("positive", ExhaustConnection.class, ExhaustConnection.OPEN, ExhaustConnection.EXHAUST, ExhaustConnection.PIPE);
    public static final EnumProperty<ExhaustConnection> NEGATIVE =
            EnumProperty.create("negative", ExhaustConnection.class, ExhaustConnection.OPEN, ExhaustConnection.EXHAUST, ExhaustConnection.PIPE);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public StraightExhaustPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(POSITIVE, ExhaustConnection.OPEN)
                .setValue(NEGATIVE, ExhaustConnection.OPEN)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, POSITIVE, NEGATIVE, WATERLOGGED);
    }

    /** 방향 side 쪽 끝의 상태. 축이 다른 면은 NONE(막힌 옆면). */
    public static ExhaustConnection endValue(BlockState state, Direction side) {
        if (state.getValue(AXIS) != side.getAxis()) {
            return ExhaustConnection.NONE;
        }
        return side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? state.getValue(POSITIVE) : state.getValue(NEGATIVE);
    }

    private static EnumProperty<ExhaustConnection> endProperty(Direction side) {
        return side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? POSITIVE : NEGATIVE;
    }

    /** 축 방향 두 끝을 이웃 기준으로 다시 계산한다. 연결이 없으면 열린 끝. */
    public static BlockState updateState(BlockState state, @Nullable Direction ignore, LevelAccessor level, BlockPos pos) {
        Direction.Axis axis = state.getValue(AXIS);
        for (Direction.AxisDirection ad : Direction.AxisDirection.values()) {
            Direction side = Direction.fromAxisAndDirection(axis, ad);
            if (side == ignore) {
                continue;
            }
            ExhaustConnection c = ExhaustPipes.connectionToward(level, pos, side);
            state = state.setValue(endProperty(side), c.isConnected() ? c : ExhaustConnection.OPEN);
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 아이템이 없으므로 보통은 렌치 전환으로만 생기지만, 혹시 설치되면 클릭한 면의 축을 쓴다.
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState()
                .setValue(AXIS, context.getClickedFace().getAxis())
                .setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
        return updateState(state, null, level, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (direction.getAxis() != state.getValue(AXIS)) {
            return state;
        }
        ExhaustConnection c = ExhaustPipes.connectionToward(level, pos, direction);
        return state.setValue(endProperty(direction), c.isConnected() ? c : ExhaustConnection.OPEN);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ExhaustPipes.shape(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** 직선 배기 파이프는 아이템이 없다. 픽블록하면 일반 배기 파이프를 준다 (Create 유리 파이프와 같음). */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(SERegistry.EXHAUST_PIPE_ITEM.get());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExhaustPipeBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------- 렌치 (Create AxisPipeBlock.toRegularPipe 대응)

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.isClientSide) {
            Direction side = Direction.fromAxisAndDirection(state.getValue(AXIS), Direction.AxisDirection.POSITIVE);
            BlockState regular = SERegistry.EXHAUST_PIPE.get().defaultBlockState()
                    .setValue(ExhaustPipeBlock.property(side), ExhaustConnection.OPEN)
                    .setValue(ExhaustPipeBlock.property(side.getOpposite()), ExhaustConnection.OPEN)
                    .setValue(ExhaustPipeBlock.WATERLOGGED, state.getValue(WATERLOGGED));
            regular = ExhaustPipeBlock.updateState(regular, side, null, level, pos);
            level.setBlockAndUpdate(pos, regular);
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
