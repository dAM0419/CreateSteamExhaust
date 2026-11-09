package com.ssw.steamexhaust.steam;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.ssw.steamexhaust.config.SEConfig;
import com.ssw.steamexhaust.content.SteamPlumeOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.function.DoubleSupplier;

/**
 * 클라이언트 연출 (기획서 9장). 서버가 동기화한 출구 목록만 보고 그린다.
 * Level의 공용 메서드만 쓰므로 서버에서 로드되어도 안전하다.
 */
public final class SteamClientEffects {
    private SteamClientEffects() {
    }

    /*
     * 출구 하나당 틱당 덩어리 수 = BASE + PER_MB × 배기량(mB/틱).
     *   레벨 1(10 mB/틱) ≈ 초당 3개, 레벨 6 ≈ 초당 9개, 출구 상한(128) ≈ 초당 17개.
     * 덩어리 하나는 지름 0.6~1.4블록에서 2.6~4배로 부풀고 80~310틱 동안 떠오르므로,
     * 출구 위에 동시에 떠 있는 덩어리는 레벨 1 ≈ 16개, 상한 ≈ 250개 (높이 약 7 → 27블록의 기둥).
     * Create: Smoke Stacks(틱당 0.06 + 0.055 × 열 단계, 작은 덩어리)보다 확실히 많이 보이도록 맞춘 값.
     */
    private static final double BASE_RATE = 0.08;
    private static final double PER_MB_RATE = 0.006;
    /** 이 배기량(mB/틱)에서 덩어리 세기 1.0 (가장 크고 높이 솟음). */
    private static final float FULL_STRENGTH_FLOW = 120F;

    private static DoubleSupplier particleSettingScale = () -> 1.0;

    /** 클라이언트 초기화에서 비디오 설정(파티클: 모두/감소/최소) 배율을 넘겨준다. */
    public static void setParticleSettingScale(DoubleSupplier supplier) {
        particleSettingScale = supplier;
    }

    public static void tick(Level level, FluidTankBlockEntity be, SteamState st) {
        RandomSource random = level.random;
        double multiplier = SEConfig.particleMultiplier() * particleSettingScale.getAsDouble();
        boolean sounds = SEConfig.sounds();

        for (SteamState.Outlet outlet : st.outlets) {
            if (outlet.flow() <= 0) {
                continue;
            }
            double expected = (BASE_RATE + PER_MB_RATE * outlet.flow()) * multiplier;
            int count = (int) expected;
            if (random.nextDouble() < expected - count) {
                count++;
            }
            Direction dir = outlet.dir();
            BlockPos pos = outlet.pos();
            double nx = dir.getStepX();
            double ny = dir.getStepY();
            double nz = dir.getStepZ();
            // 출구 면에서 살짝 바깥 (덩어리 충돌 상자가 파이프에 걸리지 않게)
            double cx = pos.getX() + 0.5 + nx * 0.65;
            double cy = pos.getY() + 0.5 + ny * 0.65;
            double cz = pos.getZ() + 0.5 + nz * 0.65;
            // 분출 속도: 출구 부하가 클수록 세게 뿜는다
            double speed = 0.08 + 0.22 * outlet.load();
            SteamPlumeOptions plume = new SteamPlumeOptions(outlet.flow() / FULL_STRENGTH_FLOW);
            for (int i = 0; i < count; i++) {
                // 안지름 8px 안에서 흩뿌림 (출구 방향 성분은 제외)
                double jx = (random.nextDouble() - 0.5) * 0.4 * (1 - Math.abs(nx));
                double jy = (random.nextDouble() - 0.5) * 0.4 * (1 - Math.abs(ny));
                double jz = (random.nextDouble() - 0.5) * 0.4 * (1 - Math.abs(nz));
                double vx = nx * speed + (random.nextDouble() - 0.5) * 0.02;
                double vy = ny * speed + (random.nextDouble() - 0.5) * 0.02;
                double vz = nz * speed + (random.nextDouble() - 0.5) * 0.02;
                // 굴뚝 연기처럼 멀리서도 보이게 강제 표시 (설정 배율은 위에서 이미 반영)
                level.addAlwaysVisibleParticle(plume, true, cx + jx, cy + jy, cz + jz, vx, vy, vz);
            }
            if (sounds && random.nextInt(60) == 0) {
                level.playLocalSound(cx, cy, cz, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                        0.04F + 0.12F * outlet.load(), 1.6F + random.nextFloat() * 0.3F, false);
            }
        }

        // ---- 과열 경고 (기획서 8.2)
        float ratio = st.overheatRatio;
        if (ratio <= 0.01F) {
            return;
        }
        BlockPos c = be.getBlockPos();
        double width = Math.max(1, be.getWidth());
        double height = Math.max(1, be.getHeight());
        if (random.nextFloat() < 0.2F + ratio * 0.6F) {
            // 탱크 이음새에서 새는 가는 증기
            double x = c.getX() + random.nextDouble() * width;
            double y = c.getY() + random.nextDouble() * height;
            double z = c.getZ() + random.nextDouble() * width;
            level.addParticle(ParticleTypes.CLOUD, x, y, z,
                    (random.nextDouble() - 0.5) * 0.05, 0.02 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.05);
        }
        if (!sounds) {
            return;
        }
        double sx = c.getX() + width / 2;
        double sy = c.getY() + height / 2;
        double sz = c.getZ() + width / 2;
        if (ratio > 0.25F) {
            if (random.nextInt(25) == 0) {
                level.playLocalSound(sx, sy, sz, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 0.6F, false);
            }
            if (random.nextInt(45) == 0) {
                level.playLocalSound(sx, sy, sz, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.35F, 0.5F, false);
            }
        }
        if (ratio > 0.75F && level.getGameTime() % 8 == 0) {
            level.playLocalSound(sx, sy, sz, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.7F, 1.8F, false);
        }
    }
}
