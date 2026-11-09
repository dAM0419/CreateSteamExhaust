package com.ssw.steamexhaust.steam;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 탱크 묶음의 실제 모양. Create 세로 탱크와 Create: Connected 가로 탱크(Fluid Vessel)를 같은 방식으로 다룬다:
 * 컨트롤러를 공유하는 탱크 블록을 모두 모은 뒤, 맨 위 층 바로 위 칸을 윗면, 맨 아래 층 바로 아래 칸을 바닥면으로 본다.
 * 컨트롤러가 어느 모서리에 있든 상관없다.
 * <p>
 * 모양은 탱크를 다시 쌓기 전에는 바뀌지 않으므로 {@link #cached}로 보일러 상태에 저장해 두고 재사용한다.
 */
public final class BoilerGeometry {
    private static final int MAX_CELLS = 4096;

    public final BlockPos controller;
    public final List<BlockPos> topCells;
    public final List<BlockPos> bottomCells;
    public final Vec3 centre;
    public final int size;

    private BoilerGeometry(BlockPos controller, int size, List<BlockPos> top, List<BlockPos> bottom, Vec3 centre) {
        this.controller = controller;
        this.size = size;
        this.topCells = top;
        this.bottomCells = bottom;
        this.centre = centre;
    }

    /**
     * 저장해 둔 모양을 돌려준다. 너비·높이·컨트롤러 블록 상태가 그대로면 탱크 구성이 같다고 본다.
     * 일부 칸이 로드되지 않아 칸 수가 탱크 크기와 다르면 저장하지 않는다 (불완전한 모양이 굳지 않게).
     */
    public static BoilerGeometry cached(Level level, FluidTankBlockEntity controller) {
        SteamState st = BoilerHooks.state(controller.boiler);
        if (st == null) {
            return of(level, controller);
        }
        int width = controller.getWidth();
        int height = controller.getHeight();
        BlockState state = controller.getBlockState();
        BoilerGeometry g = st.geometry;
        if (g != null && st.geometryWidth == width && st.geometryHeight == height && st.geometryState == state
                && g.controller.equals(controller.getBlockPos())) {
            return g;
        }
        g = of(level, controller);
        if (g.size == controller.getTotalTankSize()) {
            st.geometry = g;
            st.geometryWidth = width;
            st.geometryHeight = height;
            st.geometryState = state;
        } else {
            st.geometry = null;
        }
        return g;
    }

    public static BoilerGeometry of(Level level, FluidTankBlockEntity controller) {
        BlockPos cpos = controller.getBlockPos();
        Set<BlockPos> cells = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        cells.add(cpos);
        queue.add(cpos);
        while (!queue.isEmpty() && cells.size() < MAX_CELLS) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (cells.contains(n) || !level.isLoaded(n)) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(n);
                if (be instanceof FluidTankBlockEntity tank) {
                    FluidTankBlockEntity c = tank.getControllerBE();
                    if (c != null && c.getBlockPos().equals(cpos)) {
                        cells.add(n);
                        queue.add(n);
                    }
                }
            }
        }

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        double sx = 0;
        double sy = 0;
        double sz = 0;
        for (BlockPos p : cells) {
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
            sx += p.getX() + 0.5;
            sy += p.getY() + 0.5;
            sz += p.getZ() + 0.5;
        }
        List<BlockPos> top = new ArrayList<>();
        List<BlockPos> bottom = new ArrayList<>();
        for (BlockPos p : cells) {
            if (p.getY() == maxY) {
                top.add(p.above());
            }
            if (p.getY() == minY) {
                bottom.add(p.below());
            }
        }
        int n = cells.size();
        return new BoilerGeometry(cpos, n, List.copyOf(top), List.copyOf(bottom), new Vec3(sx / n, sy / n, sz / n));
    }
}
