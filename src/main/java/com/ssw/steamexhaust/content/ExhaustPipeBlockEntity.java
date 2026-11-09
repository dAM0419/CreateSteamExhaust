package com.ssw.steamexhaust.content;

import com.ssw.steamexhaust.SERegistry;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 데이터가 없는 빈 블록 엔티티.
 * Create 6.0.10의 FluidPropagator.hasFluidCapability는 블록 엔티티가 없으면 capability를 묻지 않고 false를 돌려주므로,
 * Create 파이프가 배기 파이프에 연결되려면 블록 엔티티가 꼭 있어야 한다.
 */
public class ExhaustPipeBlockEntity extends BlockEntity {
    public ExhaustPipeBlockEntity(BlockPos pos, BlockState state) {
        super(SERegistry.EXHAUST_PIPE_BE.get(), pos, state);
    }

    /** 이 면으로 유체 capability를 노출할지. 일반 배기 파이프는 모든 면, 직선 배기 파이프는 축 방향 두 면만. */
    public boolean exposesCapability(@Nullable Direction side) {
        BlockState state = getBlockState();
        if (side == null) {
            return true;
        }
        if (state.getBlock() instanceof StraightExhaustPipeBlock) {
            return state.getValue(StraightExhaustPipeBlock.AXIS) == side.getAxis();
        }
        return true;
    }
}
