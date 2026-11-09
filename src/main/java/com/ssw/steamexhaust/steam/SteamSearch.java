package com.ssw.steamexhaust.steam;

import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.ssw.steamexhaust.compat.AeroCompat;
import com.ssw.steamexhaust.config.SEConfig;
import com.ssw.steamexhaust.content.ExhaustConnection;
import com.ssw.steamexhaust.content.ExhaustPipes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 기획서 4장: 보일러 윗면에서 출발해 펌프를 지나지 않고 닿는 배관 묶음(증기 라인)을 찾는다.
 * 결과는 구조만 담는다(출구 위치·거리, 복수 펌프 위치, 재열기 등). 펌프 RPM·재열기 열·vent 신호처럼
 * 매 틱 바뀌는 값은 SteamExhaustTicker가 이 구조를 보고 그때그때 읽는다.
 */
public final class SteamSearch {
    private SteamSearch() {
    }

    public enum Kind {
        /** 배기 파이프의 열린 끝. */
        OUTLET,
        /** 펌프 흡입구 (복수기). */
        CONDENSER,
        /** 가열 중이고 증기 소비자가 없는 탱크 (재열기). */
        REHEATER,
        /** 배기 파이프 위에 단 Aeronautics vent (설정 pipeFedVents). */
        PIPE_VENT
    }

    public static final class Endpoint {
        public final Kind kind;
        /** OUTLET: 배기 파이프 위치, CONDENSER: 펌프 위치, REHEATER: 재열기 컨트롤러, PIPE_VENT: vent 위치. */
        public final BlockPos pos;
        /** OUTLET: 출구 방향, CONDENSER: 펌프 토출 방향. */
        public final Direction dir;
        /** 진입 칸에서의 거리 d. */
        public final int distance;
        /** OUTLET: 출구 앞이 막혔는지. */
        public final boolean blocked;
        /** CONDENSER: 응축수를 받는 보일러 컨트롤러 (없으면 null). */
        @Nullable
        public final BlockPos target;
        /** REHEATER: 재열기 윗면부터의 하류 탐색 결과. */
        @Nullable
        public final Result downstream;

        Endpoint(Kind kind, BlockPos pos, Direction dir, int distance, boolean blocked,
                 @Nullable BlockPos target, @Nullable Result downstream) {
            this.kind = kind;
            this.pos = pos;
            this.dir = dir;
            this.distance = distance;
            this.blocked = blocked;
            this.target = target;
            this.downstream = downstream;
        }
    }

    /** 증기 라인 하나 (윗면에서 이어진 배관 연결 묶음). */
    public static final class Line {
        public final List<Endpoint> endpoints = new ArrayList<>();
        /** 이 라인이 닿은 다른 보일러의 컨트롤러 (공유 라인). */
        public final Set<BlockPos> sharedBoilers = new LinkedHashSet<>();
        /** 펌프 토출구가 이 라인으로 물을 밀어 넣고 있음 → 처리량 0. */
        public boolean feedMixed;
    }

    public static final class Result {
        public final List<Line> lines = new ArrayList<>();
        /** 윗면에 직접 붙은 Aeronautics vent 위치. */
        public final List<BlockPos> tankVents = new ArrayList<>();
        /** 로드되지 않은 칸에 닿았음. */
        public boolean unknown;
        public BoilerGeometry geometry;
    }

    /**
     * @param depth        재열기 단수 (보일러 자체는 0)
     * @param visitedTanks 이미 거친 탱크 컨트롤러 (재열기 순환 방지)
     */
    public static Result run(Level level, FluidTankBlockEntity controller, BoilerGeometry geometry, int depth,
                             Set<BlockPos> visitedTanks) {
        Result result = new Result();
        result.geometry = geometry;
        BlockPos self = controller.getBlockPos();
        Set<BlockPos> visited = new HashSet<>(visitedTanks);
        visited.add(self);

        int maxNodes = SEConfig.searchMaxNodes();
        int maxDepth = SEConfig.reheaterMaxDepth();

        Map<BlockPos, Integer> dist = new HashMap<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        // 노드별로 발견한 끝점·플래그. 탐색이 끝난 뒤 연결 묶음별로 모은다.
        Map<BlockPos, List<Endpoint>> nodeEndpoints = new LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> nodeShared = new HashMap<>();
        Set<BlockPos> nodeFeed = new HashSet<>();
        Map<BlockPos, Result> reheaterCache = new HashMap<>();

        // ---- 진입 칸 (윗면 칸 중 아래로 열린 배관)
        for (BlockPos top : geometry.topCells) {
            if (!level.isLoaded(top)) {
                result.unknown = true;
                continue;
            }
            BlockState state = level.getBlockState(top);
            if (AeroCompat.isSteamVent(state)) {
                result.tankVents.add(top);
                continue;
            }
            if (opensToward(level, top, state, Direction.DOWN)) {
                dist.put(top, 0);
                parent.put(top, top);
                queue.add(top);
            }
        }

        // ---- 너비 우선 탐색 (모든 진입 칸에서 동시에 출발 → 거리 = 가장 가까운 진입 칸부터)
        while (!queue.isEmpty()) {
            BlockPos a = queue.poll();
            int da = dist.get(a);
            BlockState sa = level.getBlockState(a);
            boolean ours = ExhaustPipes.isExhaust(sa);

            if (ours) {
                for (Direction face : Direction.values()) {
                    if (ExhaustPipes.faceOf(sa, face) != ExhaustConnection.OPEN) {
                        continue;
                    }
                    BlockPos front = a.relative(face);
                    if (!level.isLoaded(front)) {
                        result.unknown = true;
                        continue;
                    }
                    boolean blocked = level.getBlockState(front).isFaceSturdy(level, front, face.getOpposite());
                    add(nodeEndpoints, a, new Endpoint(Kind.OUTLET, a, face, da, blocked, null, null));
                }
            }

            for (Direction d : connections(level, a, sa)) {
                BlockPos b = a.relative(d);
                if (!level.isLoaded(b)) {
                    result.unknown = true;
                    continue;
                }
                BlockState sb = level.getBlockState(b);

                // 1. 배기 파이프
                if (ExhaustPipes.isExhaust(sb)) {
                    if (ExhaustPipes.faceOf(sb, d.getOpposite()).isConnected()) {
                        visit(a, b, da + 1, dist, parent, queue, maxNodes);
                    }
                    continue;
                }

                // 2. 펌프: 흡입구에 닿으면 복수기, 토출구에 닿으면 급수 혼입
                if (PumpBlock.isPump(sb)) {
                    Direction facing = sb.getValue(PumpBlock.FACING);
                    if (facing.getAxis() != d.getAxis()) {
                        continue;
                    }
                    if (d == facing) {
                        // 펌프의 뒤(흡입구)로 들어감
                        BlockPos target = findCondensateTarget(level, b, facing);
                        add(nodeEndpoints, a, new Endpoint(Kind.CONDENSER, b, facing, da + 1, false, target, null));
                    } else {
                        nodeFeed.add(a);
                    }
                    continue;
                }

                // 3. Create 배관 (파이프, 유리·케이스 파이프, 스마트 파이프, 밸브)
                FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, b);
                if (pipe != null) {
                    if (valveOpen(sb) && pipe.canHaveFlowToward(sb, d.getOpposite())) {
                        visit(a, b, da + 1, dist, parent, queue, maxNodes);
                    }
                    continue;
                }

                // 4. 탱크
                BlockEntity be = level.getBlockEntity(b);
                if (be instanceof FluidTankBlockEntity tank) {
                    FluidTankBlockEntity other = tank.getControllerBE();
                    if (other == null || other.getBlockPos().equals(self)) {
                        continue; // 자기 탱크(윗면이면 진입 칸, 옆면이면 막힘)
                    }
                    BlockPos otherPos = other.getBlockPos();
                    if (d == Direction.DOWN) {
                        // 다른 탱크의 윗면으로 들어감: 그것이 보일러면 공유 라인
                        if (other.boiler.isActive()) {
                            nodeShared.computeIfAbsent(a, k -> new LinkedHashSet<>()).add(otherPos);
                        }
                    } else if (depth < maxDepth && !visited.contains(otherPos) && isReheaterCandidate(level, other)) {
                        Result downstream = reheaterCache.get(otherPos);
                        if (downstream == null) {
                            Set<BlockPos> nextVisited = new HashSet<>(visited);
                            downstream = run(level, other, BoilerGeometry.cached(level, other), depth + 1, nextVisited);
                            reheaterCache.put(otherPos, downstream);
                            if (downstream.unknown) {
                                result.unknown = true;
                            }
                        }
                        add(nodeEndpoints, a, new Endpoint(Kind.REHEATER, otherPos, d, da + 1, false, null, downstream));
                    }
                    continue;
                }

                // 5. 배기 파이프 위의 vent (설정)
                if (ours && d == Direction.UP && SEConfig.pipeFedVents() && AeroCompat.isSteamVent(sb)) {
                    add(nodeEndpoints, a, new Endpoint(Kind.PIPE_VENT, b, Direction.UP, da + 1, false, null, null));
                }
                // 그 밖(공기, 일반 블록, Create 파이프의 열린 끝 너머 등): 배기 아님
            }
        }

        // ---- 연결 묶음별로 정리
        Map<BlockPos, Line> lineByRoot = new LinkedHashMap<>();
        for (BlockPos node : dist.keySet()) {
            lineByRoot.computeIfAbsent(find(parent, node), k -> new Line());
        }
        Set<BlockPos> reheatersTaken = new HashSet<>();
        for (Map.Entry<BlockPos, List<Endpoint>> e : nodeEndpoints.entrySet()) {
            Line line = lineByRoot.get(find(parent, e.getKey()));
            for (Endpoint ep : e.getValue()) {
                // 같은 재열기는 보일러 전체에서 한 번만 센다 (여러 입구로 들어가도 재열 능력은 하나)
                if (ep.kind == Kind.REHEATER && !reheatersTaken.add(ep.pos)) {
                    continue;
                }
                line.endpoints.add(ep);
            }
        }
        for (Map.Entry<BlockPos, Set<BlockPos>> e : nodeShared.entrySet()) {
            lineByRoot.get(find(parent, e.getKey())).sharedBoilers.addAll(e.getValue());
        }
        for (BlockPos node : nodeFeed) {
            lineByRoot.get(find(parent, node)).feedMixed = true;
        }
        result.lines.addAll(lineByRoot.values());
        return result;
    }

    // ------------------------------------------------------------------ 배관 판정

    /** 이 칸의 배관이 side 방향으로 열려(연결되어) 있는지. */
    static boolean opensToward(Level level, BlockPos pos, BlockState state, Direction side) {
        if (ExhaustPipes.isExhaust(state)) {
            return ExhaustPipes.faceOf(state, side).isConnected();
        }
        if (PumpBlock.isPump(state)) {
            return false;
        }
        FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pos);
        return pipe != null && valveOpen(state) && pipe.canHaveFlowToward(state, side);
    }

    /** 증기가 지나갈 수 있는 방향들. 배기 파이프는 연결된 면, Create 배관은 Create의 연결 목록. */
    static List<Direction> connections(Level level, BlockPos pos, BlockState state) {
        if (ExhaustPipes.isExhaust(state)) {
            List<Direction> list = new ArrayList<>(6);
            for (Direction d : Direction.values()) {
                if (ExhaustPipes.faceOf(state, d).isConnected()) {
                    list.add(d);
                }
            }
            return list;
        }
        FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pos);
        if (pipe == null || !valveOpen(state) || PumpBlock.isPump(state)) {
            return Collections.emptyList();
        }
        return FluidPropagator.getPipeConnections(state, pipe);
    }

    /** 닫힌 밸브는 증기를 막는다. Create 밸브는 닫혀도 연결 목록은 그대로라 따로 본다. */
    static boolean valveOpen(BlockState state) {
        if (state.getBlock() instanceof FluidValveBlock && state.hasProperty(FluidValveBlock.ENABLED)) {
            return state.getValue(FluidValveBlock.ENABLED);
        }
        return true;
    }

    /** 증기 소비자가 없고 바닥에 열원이 있는 탱크. 열의 크기는 틱마다 따로 읽는다. */
    static boolean isReheaterCandidate(Level level, FluidTankBlockEntity controller) {
        if (controller.boiler.isActive()) {
            return false;
        }
        BoilerGeometry geometry = BoilerGeometry.cached(level, controller);
        for (BlockPos below : geometry.bottomCells) {
            if (!level.isLoaded(below)) {
                continue;
            }
            if (BoilerHeater.findHeat(level, below, level.getBlockState(below)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 복수 펌프 토출구에서 Create 배관을 따라(펌프 사거리 이내) 처음 닿는 보일러.
     * 윗면으로 들어가면 급수 혼입이 되므로 대상이 아니다 (기획서 6.2).
     */
    @Nullable
    static BlockPos findCondensateTarget(Level level, BlockPos pumpPos, Direction facing) {
        int range = FluidPropagator.getPumpRange();
        record Step(BlockPos pos, Direction arrivedMoving, int steps) {
        }
        ArrayDeque<Step> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(new Step(pumpPos.relative(facing), facing, 1));
        while (!queue.isEmpty()) {
            Step step = queue.poll();
            BlockPos p = step.pos();
            if (!seen.add(p) || !level.isLoaded(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof FluidTankBlockEntity tank) {
                if (step.arrivedMoving() == Direction.DOWN) {
                    return null;
                }
                FluidTankBlockEntity controller = tank.getControllerBE();
                return controller != null && controller.boiler.isActive() ? controller.getBlockPos() : null;
            }
            if (PumpBlock.isPump(state) || ExhaustPipes.isExhaust(state)) {
                continue;
            }
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, p);
            if (pipe == null || !valveOpen(state) || !pipe.canHaveFlowToward(state, step.arrivedMoving().getOpposite())) {
                continue;
            }
            if (step.steps() >= range) {
                continue;
            }
            for (Direction d : FluidPropagator.getPipeConnections(state, pipe)) {
                if (d != step.arrivedMoving().getOpposite()) {
                    queue.add(new Step(p.relative(d), d, step.steps() + 1));
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 도우미

    private static void visit(BlockPos from, BlockPos to, int distance, Map<BlockPos, Integer> dist,
                              Map<BlockPos, BlockPos> parent, ArrayDeque<BlockPos> queue, int maxNodes) {
        if (!dist.containsKey(to)) {
            if (dist.size() >= maxNodes) {
                return;
            }
            dist.put(to, distance);
            parent.put(to, to);
            queue.add(to);
        }
        union(parent, from, to);
    }

    private static void add(Map<BlockPos, List<Endpoint>> map, BlockPos node, Endpoint endpoint) {
        map.computeIfAbsent(node, k -> new ArrayList<>()).add(endpoint);
    }

    private static BlockPos find(Map<BlockPos, BlockPos> parent, BlockPos x) {
        BlockPos root = x;
        while (true) {
            BlockPos p = parent.get(root);
            if (p == null || p.equals(root)) {
                break;
            }
            root = p;
        }
        // 경로 압축
        BlockPos cur = x;
        while (!cur.equals(root)) {
            BlockPos next = parent.get(cur);
            parent.put(cur, root);
            cur = next;
        }
        return root;
    }

    private static void union(Map<BlockPos, BlockPos> parent, BlockPos a, BlockPos b) {
        BlockPos ra = find(parent, a);
        BlockPos rb = find(parent, b);
        if (!ra.equals(rb)) {
            parent.put(rb, ra);
        }
    }
}
