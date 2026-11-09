package com.ssw.steamexhaust.steam;

import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.ssw.steamexhaust.config.SEConfig;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 보일러 컨트롤러마다 매 틱 실행된다 (FluidTankBlockEntity.tick 끝에서 Mixin으로 호출).
 * <ul>
 *   <li>서버: 배관 탐색(주기적) → 처리량 C 계산 → 증기 배분(출구·복수기·재열기·vent) → 과열·폭발 → 동기화</li>
 *   <li>클라이언트: 동기화된 출구 목록으로 증기 연출</li>
 * </ul>
 */
public final class SteamExhaustTicker {
    private SteamExhaustTicker() {
    }

    public static void tick(FluidTankBlockEntity be) {
        if (!be.isController()) {
            return;
        }
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        SteamState st = BoilerHooks.state(be.boiler);
        if (st == null) {
            return;
        }
        if (level.isClientSide) {
            SteamClientEffects.tick(level, be, st);
            return;
        }
        serverTick(level, be, be.boiler, st);
    }

    // ================================================================== 서버

    private static void serverTick(Level level, FluidTankBlockEntity be, BoilerData boiler, SteamState st) {
        long now = level.getGameTime();
        int decay = SEConfig.overheatDecayPerTick();
        int threshold = SEConfig.overheatThreshold();

        // 복수기로 돌려받은 물 표시 (지난 1틱 동안 다른 보일러/자신이 넣어 준 양)
        st.recovered = (float) st.recoveredCounter;
        st.recoveredCounter = 0;

        if (!boiler.isActive()) {
            if (st.applies || st.capacity != 0 || !st.outlets.isEmpty() || st.search != null) {
                st.resetInactive();
            }
            st.overheat = Math.max(0, st.overheat - decay);
            st.overheatRatio = Mth.clamp(st.overheat / (float) threshold, 0, 1);
            sync(be, st, now);
            return;
        }

        // ---- 배관 탐색 (주기적, 보일러마다 시점을 흩뜨림)
        int interval = SEConfig.searchIntervalTicks();
        long phase = Math.floorMod(be.getBlockPos().hashCode(), interval);
        if (st.search == null || (now + phase) % interval == 0 || now - st.lastSearchTick > interval * 2L) {
            SteamSearch.Result found = SteamSearch.run(level, be, BoilerGeometry.cached(level, be), 0, Set.of());
            st.lastSearchTick = now;
            if (found.unknown && st.search != null) {
                st.unknown = true; // 직전 구조와 처리량을 유지한다 (기획서 8.4)
            } else {
                st.search = found;
                st.unknown = found.unknown;
            }
        }
        SteamSearch.Result result = st.search;

        boolean require = SEConfig.requireExhaust();
        int consumers = boiler.attachedEngines + result.tankVents.size();
        st.applies = require && consumers > 0;
        int size = result.geometry != null ? result.geometry.size : be.getTotalTankSize();
        int nRaw = rawLevel(boiler, size);
        st.nRaw = nRaw;

        // ---- 처리량
        double a = SEConfig.baseCapacity();
        double b = SEConfig.coolingPerBlock();
        NetEval net = evaluate(level, result, a, b, true);
        double[] lineShare = new double[result.lines.size()];
        double total = 0;
        for (int i = 0; i < result.lines.size(); i++) {
            SteamSearch.Line line = result.lines.get(i);
            double cap = net.lines[i].cap;
            if (!line.sharedBoilers.isEmpty() && cap > 0) {
                // 공유 라인: 라인 처리량을 닿은 보일러들의 수요(10·n) 비율로 나눈다 (기획서 5.4)
                double mine = nRaw;
                double sum = mine;
                int count = 1;
                for (BlockPos other : line.sharedBoilers) {
                    sum += demandOf(level, other);
                    count++;
                }
                cap = sum > 0 ? cap * mine / sum : cap / count;
            }
            lineShare[i] = cap;
            total += cap;
        }
        for (double v : net.ventCaps) {
            total += v;
        }

        double capacity = st.unknown ? st.capacity : total;
        st.capacity = (float) capacity;
        st.exhaustLevel = (int) Math.floor(capacity / 10.0 + 1e-6);

        // ---- 증기 배분
        int nStar = st.applies ? Math.min(nRaw, st.exhaustLevel) : nRaw;
        double flow = 10.0 * nStar;
        Distribution dist = new Distribution();
        st.outlets.clear();
        if (total > 0 && flow > 0) {
            distribute(level, result, net, lineShare, flow, total, dist, true);
        }
        applyDistribution(level, st, dist, result, net, now, require);

        // ---- 과열 (기획서 8장)
        boolean graceActive = st.grace && SEConfig.graceForExistingBoilers();
        if (!st.unknown && capacity >= 10) {
            st.grace = false;
        }
        boolean unvented = st.applies && !st.unknown && capacity <= 0 && nRaw >= 1;
        if (unvented && !graceActive) {
            st.overheat += nRaw;
        } else {
            st.overheat = Math.max(0, st.overheat - decay);
        }
        st.overheatRatio = Mth.clamp(st.overheat / (float) threshold, 0, 1);
        st.status = status(level, st, result, net, capacity, nRaw, consumers, graceActive, a, b);

        if (st.overheat >= threshold) {
            explode(level, be, st, nRaw, result);
            return;
        }
        sync(be, st, now);
    }

    /** 배기를 뺀 보일러 레벨 = min(열, 물, 크기/4). 패시브 열은 1로 본다. */
    static int rawLevel(BoilerData boiler, int size) {
        int forSize = boiler.getMaxHeatLevelForBoilerSize(size);
        int forWater = boiler.getMaxHeatLevelForWaterSupply();
        if (boiler.activeHeat <= 0) {
            return boiler.passiveHeat && forSize > 0 && forWater > 0 ? 1 : 0;
        }
        return Math.min(boiler.activeHeat, Math.min(forWater, forSize));
    }

    private static double demandOf(Level level, BlockPos controllerPos) {
        if (!level.isLoaded(controllerPos)) {
            return 0;
        }
        if (level.getBlockEntity(controllerPos) instanceof FluidTankBlockEntity tank) {
            SteamState other = BoilerHooks.state(tank.boiler);
            return other == null ? 0 : other.nRaw;
        }
        return 0;
    }

    // ------------------------------------------------------------------ 처리량 계산

    static final class LineEval {
        double[] epCap;
        double sum;
        double cap;
    }

    static final class NetEval {
        LineEval[] lines;
        double[] ventCaps = new double[0];
        double total;
        final IdentityHashMap<SteamSearch.Endpoint, NetEval> downstream = new IdentityHashMap<>();

        double[] lineCaps() {
            double[] caps = new double[lines.length];
            for (int i = 0; i < lines.length; i++) {
                caps[i] = lines[i].cap;
            }
            return caps;
        }
    }

    static NetEval evaluate(Level level, SteamSearch.Result result, double a, double b, boolean includeVents) {
        NetEval net = new NetEval();
        net.lines = new LineEval[result.lines.size()];
        for (int i = 0; i < result.lines.size(); i++) {
            SteamSearch.Line line = result.lines.get(i);
            LineEval le = new LineEval();
            le.epCap = new double[line.endpoints.size()];
            for (int j = 0; j < line.endpoints.size(); j++) {
                SteamSearch.Endpoint ep = line.endpoints.get(j);
                double cap = switch (ep.kind) {
                    // 출구: A − B·d (기획서 5.2)
                    case OUTLET -> ep.blocked ? 0 : Math.max(0, a - b * ep.distance);
                    // 복수기: min(A, B·d, RPM/2) (기획서 6.1)
                    case CONDENSER -> {
                        double speed = pumpSpeed(level, ep.pos);
                        yield speed <= 0 ? 0 : Math.min(a, Math.min(b * ep.distance, speed / 2.0));
                    }
                    // 재열기: min(10·h, 하류 처리량) (기획서 7.2)
                    case REHEATER -> {
                        if (ep.downstream == null) {
                            yield 0;
                        }
                        NetEval down = evaluate(level, ep.downstream, a, b, false);
                        net.downstream.put(ep, down);
                        int heat = reheaterHeat(level, ep.downstream.geometry, ep.pos);
                        yield Math.min(10.0 * heat, down.total);
                    }
                    // 배관 끝 vent: min(A − B·d, 10 × 개도) (기획서 10.1 옵션)
                    case PIPE_VENT -> Math.min(Math.max(0, a - b * ep.distance), 10.0 * ventOpening(level, ep.pos));
                };
                le.epCap[j] = cap;
                le.sum += cap;
            }
            le.cap = line.feedMixed ? 0 : Math.min(a, le.sum);
            net.lines[i] = le;
            net.total += le.cap;
        }
        if (includeVents) {
            net.ventCaps = new double[result.tankVents.size()];
            for (int k = 0; k < result.tankVents.size(); k++) {
                // 윗면 직결 vent: 레벨 1(10 mB/틱) × 개도 (기획서 10.1)
                double cap = 10.0 * ventOpening(level, result.tankVents.get(k));
                net.ventCaps[k] = cap;
                net.total += cap;
            }
        }
        return net;
    }

    private static double pumpSpeed(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return 0;
        }
        return level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic ? Math.abs(kinetic.getSpeed()) : 0;
    }

    /** vent 개도 0~1. 닫혀 있고 closedVentRelief면 1 (양력 없이 처리량만 인정). */
    private static double ventOpening(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return 0;
        }
        if (level.getBlockEntity(pos) instanceof SteamVentAccess vent) {
            int signal = vent.steamexhaust$signal();
            if (signal > 0) {
                return Math.min(15, signal) / 15.0;
            }
            return SEConfig.closedVentRelief() ? 1.0 : 0.0;
        }
        return 0;
    }

    /** 재열기 열 레벨: 바닥 열원의 합, 크기/4로 제한. 그사이 보일러가 되었으면 0. */
    private static int reheaterHeat(Level level, @Nullable BoilerGeometry geometry, BlockPos controllerPos) {
        if (geometry == null || !level.isLoaded(controllerPos)) {
            return 0;
        }
        if (!(level.getBlockEntity(controllerPos) instanceof FluidTankBlockEntity tank) || tank.boiler.isActive()) {
            return 0;
        }
        float heat = 0;
        for (BlockPos below : geometry.bottomCells) {
            if (!level.isLoaded(below)) {
                continue;
            }
            float h = BoilerHeater.findHeat(level, below, level.getBlockState(below));
            if (h > 0) {
                heat += h;
            }
        }
        return Math.min((int) heat, tank.boiler.getMaxHeatLevelForBoilerSize(geometry.size));
    }

    // ------------------------------------------------------------------ 증기 배분

    static final class Distribution {
        final Map<BlockPos, Double> credits = new LinkedHashMap<>();
        final Map<BlockPos, VentFlow> vents = new HashMap<>();
        final java.util.List<SteamState.Outlet> outlets = new java.util.ArrayList<>();
    }

    record VentFlow(double flow, boolean pipeFed) {
    }

    /**
     * 증기 flow(mB/틱)를 처리량 비율대로 끝점에 나눈다.
     * 끝점 i의 몫 = 라인 몫 × (끝점 처리량 / 라인 합), 실제 증기 = flow × 몫 / 총 처리량.
     */
    private static void distribute(Level level, SteamSearch.Result result, NetEval net, double[] lineShares,
                                   double flow, double capTotal, Distribution out, boolean top) {
        if (capTotal <= 0 || flow <= 0) {
            return;
        }
        for (int i = 0; i < result.lines.size(); i++) {
            SteamSearch.Line line = result.lines.get(i);
            LineEval le = net.lines[i];
            if (le.sum <= 0 || lineShares[i] <= 0) {
                continue;
            }
            for (int j = 0; j < line.endpoints.size(); j++) {
                double cap = le.epCap[j];
                if (cap <= 0) {
                    continue;
                }
                double eff = cap * lineShares[i] / le.sum;
                double f = flow * eff / capTotal;
                SteamSearch.Endpoint ep = line.endpoints.get(j);
                switch (ep.kind) {
                    case OUTLET -> out.outlets.add(new SteamState.Outlet(ep.pos, ep.dir, (float) f,
                            (float) Mth.clamp(f / cap, 0, 1)));
                    case CONDENSER -> {
                        if (ep.target != null) {
                            out.credits.merge(ep.target, SEConfig.condenserEfficiency() * f, Double::sum);
                        }
                    }
                    case REHEATER -> {
                        NetEval down = net.downstream.get(ep);
                        if (down != null && ep.downstream != null && down.total > 0) {
                            distribute(level, ep.downstream, down, down.lineCaps(), f, down.total, out, false);
                        }
                    }
                    case PIPE_VENT -> out.vents.put(ep.pos, new VentFlow(f, true));
                }
            }
        }
        if (top) {
            for (int k = 0; k < result.tankVents.size(); k++) {
                double cap = net.ventCaps[k];
                if (cap > 0) {
                    out.vents.put(result.tankVents.get(k), new VentFlow(flow * cap / capTotal, false));
                }
            }
        }
    }

    private static void applyDistribution(Level level, SteamState st, Distribution dist, SteamSearch.Result result,
                                          NetEval net, long now, boolean require) {
        st.outlets.addAll(dist.outlets);

        // 복수기 회수: 대상 보일러의 급수 집계에 더한다 (소수점은 다음 틱으로 넘긴다)
        for (Map.Entry<BlockPos, Double> e : dist.credits.entrySet()) {
            BlockPos target = e.getKey();
            if (!level.isLoaded(target) || !(level.getBlockEntity(target) instanceof FluidTankBlockEntity tank)) {
                continue;
            }
            if (!tank.boiler.isActive() || !(tank.boiler instanceof SteamBoilerAccess access)) {
                continue;
            }
            SteamState ts = access.steamexhaust$state();
            ts.supplyRemainder += e.getValue();
            ts.recoveredCounter += e.getValue();
            int whole = (int) Math.floor(ts.supplyRemainder);
            if (whole > 0) {
                BoilerHooks.addSupply(tank.boiler, whole);
                ts.supplyRemainder -= whole;
            }
        }

        // vent: CSE가 계산한 증기량을 넘겨준다 (requireExhaust가 꺼져 있으면 Aeronautics 기본 동작)
        if (!require) {
            return;
        }
        for (BlockPos pos : result.tankVents) {
            VentFlow vf = dist.vents.get(pos);
            setVentFlow(level, pos, vf == null ? 0 : vf.flow(), false, now);
        }
        forEachPipeVent(result, pos -> {
            VentFlow vf = dist.vents.get(pos);
            setVentFlow(level, pos, vf == null ? 0 : vf.flow(), true, now);
        });
    }

    private static void forEachPipeVent(SteamSearch.Result result, java.util.function.Consumer<BlockPos> action) {
        for (SteamSearch.Line line : result.lines) {
            for (SteamSearch.Endpoint ep : line.endpoints) {
                if (ep.kind == SteamSearch.Kind.PIPE_VENT) {
                    action.accept(ep.pos);
                } else if (ep.kind == SteamSearch.Kind.REHEATER && ep.downstream != null) {
                    forEachPipeVent(ep.downstream, action);
                }
            }
        }
    }

    private static void setVentFlow(Level level, BlockPos pos, double flow, boolean pipeFed, long now) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SteamVentAccess vent)) {
            return;
        }
        float previous = vent.steamexhaust$flow();
        float next = (float) flow;
        vent.steamexhaust$setFlow(next, pipeFed, now);
        boolean crossed = (previous > 0) != (next > 0);
        boolean changed = Math.abs(previous - next) > Math.max(0.5f, previous * 0.1f);
        if ((crossed || changed) && be instanceof SmartBlockEntity smart) {
            smart.sendData();
        }
    }

    // ------------------------------------------------------------------ 상태·폭발·동기화

    private static byte status(Level level, SteamState st, SteamSearch.Result result, NetEval net, double capacity,
                               int nRaw, int consumers, boolean graceActive, double a, double b) {
        if (!st.applies) {
            return SteamState.STATUS_OK;
        }
        if (st.unknown) {
            return SteamState.STATUS_UNKNOWN;
        }
        if (capacity <= 0) {
            if (graceActive) {
                return SteamState.STATUS_GRACE;
            }
            boolean anyEndpoint = !result.tankVents.isEmpty();
            boolean feed = false;
            boolean blocked = false;
            boolean distance = false;
            boolean pump = false;
            for (SteamSearch.Line line : result.lines) {
                feed |= line.feedMixed;
                for (SteamSearch.Endpoint ep : line.endpoints) {
                    anyEndpoint = true;
                    if (ep.kind == SteamSearch.Kind.OUTLET) {
                        if (ep.blocked) {
                            blocked = true;
                        } else if (a - b * ep.distance <= 0) {
                            distance = true;
                        }
                    } else if (ep.kind == SteamSearch.Kind.CONDENSER && pumpSpeed(level, ep.pos) <= 0) {
                        pump = true;
                    }
                }
            }
            if (feed) {
                return SteamState.STATUS_FEED_MIXED;
            }
            if (!anyEndpoint) {
                return SteamState.STATUS_NO_EXHAUST;
            }
            if (blocked) {
                return SteamState.STATUS_BLOCKED;
            }
            if (distance) {
                return SteamState.STATUS_DISTANCE;
            }
            if (pump) {
                return SteamState.STATUS_PUMP_STOPPED;
            }
            return SteamState.STATUS_NO_EXHAUST;
        }
        // 실제 출력은 레벨 하나당 엔진(또는 증기 배출구) 하나를 돌린다 → min(보일러 레벨, 소비처 수)만큼만 쓴다.
        // 배기 레벨이 그보다 낮을 때만 실제로 출력이 깎인다.
        if (st.exhaustLevel < Math.min(nRaw, consumers)) {
            return SteamState.STATUS_LIMITED;
        }
        if (st.exhaustLevel < nRaw) {
            return SteamState.STATUS_HEADROOM;
        }
        return SteamState.STATUS_OK;
    }

    private static void explode(Level level, FluidTankBlockEntity be, SteamState st, int nRaw, SteamSearch.Result result) {
        st.overheat = 0;
        st.overheatRatio = 0;
        BoilerGeometry geometry = result.geometry != null ? result.geometry : BoilerGeometry.cached(level, be);
        double power = Math.min(SEConfig.maxExplosionPower(), 2.0 + 0.2 * nRaw * Math.sqrt(geometry.size / 4.0));
        Vec3 c = geometry.centre;
        Level.ExplosionInteraction mode = SEConfig.explosionBreaksBlocks()
                ? Level.ExplosionInteraction.BLOCK
                : Level.ExplosionInteraction.NONE;
        if (!SEConfig.explosionBreaksBlocks()) {
            be.sendData();
        }
        level.explode(null, c.x, c.y, c.z, (float) power, mode);
    }

    private static void sync(FluidTankBlockEntity be, SteamState st, long now) {
        int hash = st.syncHash();
        if (hash == st.lastSyncHash) {
            return;
        }
        if (now - st.lastSyncTick < 10) {
            return;
        }
        st.lastSyncHash = hash;
        st.lastSyncTick = now;
        be.sendData();
    }
}
