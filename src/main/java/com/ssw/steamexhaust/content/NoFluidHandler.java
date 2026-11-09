package com.ssw.steamexhaust.content;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * 유체를 받지도 내주지도 않는 핸들러. Create 파이프가 배기 파이프에 "연결"되게 하는 용도로만 쓴다.
 * Create 펌프가 밀어도 fill이 0이므로 유체가 들어가지 않는다.
 */
public final class NoFluidHandler implements IFluidHandler {
    public static final NoFluidHandler INSTANCE = new NoFluidHandler();

    private NoFluidHandler() {
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
