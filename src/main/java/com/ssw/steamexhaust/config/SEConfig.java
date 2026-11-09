package com.ssw.steamexhaust.config;

import java.util.function.Supplier;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 기획서 12장의 설정값. 서버 설정은 월드별(serverconfig), 클라이언트 설정은 개인별이다.
 * 설정이 아직 로드되지 않은 시점(블록 상태 계산 등)에서도 안전하게 읽을 수 있도록 기본값으로 대체하는 접근자를 둔다.
 */
public final class SEConfig {
    private SEConfig() {
    }

    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        SERVER = new Server(server);
        SERVER_SPEC = server.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        CLIENT = new Client(client);
        CLIENT_SPEC = client.build();
    }

    public static final class Server {
        public final ModConfigSpec.BooleanValue requireExhaust;
        public final ModConfigSpec.DoubleValue baseCapacity;
        public final ModConfigSpec.DoubleValue coolingPerBlock;
        public final ModConfigSpec.DoubleValue condenserEfficiency;
        public final ModConfigSpec.IntValue reheaterMaxDepth;
        public final ModConfigSpec.IntValue overheatThreshold;
        public final ModConfigSpec.IntValue overheatDecayPerTick;
        public final ModConfigSpec.BooleanValue explosionBreaksBlocks;
        public final ModConfigSpec.DoubleValue maxExplosionPower;
        public final ModConfigSpec.BooleanValue graceForExistingBoilers;
        public final ModConfigSpec.DoubleValue ventM3PerMb;
        public final ModConfigSpec.BooleanValue pipeFedVents;
        public final ModConfigSpec.DoubleValue exhaustVentM3PerMb;
        public final ModConfigSpec.BooleanValue closedVentRelief;
        public final ModConfigSpec.IntValue searchIntervalTicks;
        public final ModConfigSpec.IntValue searchMaxNodes;

        Server(ModConfigSpec.Builder b) {
            b.push("exhaust");
            requireExhaust = b.comment("false면 출력 제한과 과열 없이 증기 연출만 한다.")
                    .define("requireExhaust", true);
            baseCapacity = b.comment("A: 배기 라인 1개, 출구 1개의 최대 처리량 (mB/틱). 기본 128 = 최고 속도 펌프 1대 = 보일러 레벨 12.")
                    .defineInRange("baseCapacity", 128.0, 10.0, 1000.0);
            coolingPerBlock = b.comment("B: 블록 1칸마다 줄어드는 처리량(= 응축되는 양) (mB/틱). 기본 4 = 32블록에서 완전히 식음.")
                    .defineInRange("coolingPerBlock", 4.0, 0.0, 128.0);
            b.pop();

            b.push("condenser");
            condenserEfficiency = b.comment("η: 복수기 회수율. 1이면 외부 급수 없이도 계속 돈다.")
                    .defineInRange("efficiency", 0.95, 0.0, 1.0);
            b.pop();

            b.push("reheater");
            reheaterMaxDepth = b.comment("재열기를 연달아 거칠 수 있는 최대 단수.")
                    .defineInRange("maxDepth", 8, 0, 32);
            b.pop();

            b.push("overheat");
            overheatThreshold = b.comment("배기가 없을 때 매 틱 n(보일러 레벨)씩 쌓여 이 값에서 폭발한다. 1200 = 레벨 1에서 60초, 레벨 n에서 60초/n.")
                    .defineInRange("threshold", 1200, 20, 72000);
            overheatDecayPerTick = b.comment("배기가 되거나 보일러가 쉴 때 매 틱 줄어드는 양.")
                    .defineInRange("decayPerTick", 20, 1, 1200);
            explosionBreaksBlocks = b.comment("폭발이 블록을 부수는지.")
                    .define("explosionBreaksBlocks", true);
            maxExplosionPower = b.comment("폭발 위력 상한 (TNT = 4).")
                    .defineInRange("maxExplosionPower", 6.0, 0.0, 12.0);
            graceForExistingBoilers = b.comment("모드 설치 전에 저장된 보일러는 배기가 한 번 유효해질 때까지 과열되지 않는다.")
                    .define("graceForExistingBoilers", true);
            b.pop();

            b.push("compat");
            ventM3PerMb = b.comment("Aeronautics: 탱크 윗면 vent의 양력 환산 (m³ per mB/틱). 500이면 Aeronautics 기본 균형과 같다.")
                    .defineInRange("ventM3PerMb", 500.0, 1.0, 10000.0);
            pipeFedVents = b.comment("Aeronautics: 배기 파이프 끝(위)에 단 vent도 배기 증기로 양력을 낸다.")
                    .define("pipeFedVents", false);
            exhaustVentM3PerMb = b.comment("Aeronautics: 배관 끝 vent의 양력 환산. 엔진 몫을 깎지 않는 버프라서 기본은 절반.")
                    .defineInRange("exhaustVentM3PerMb", 250.0, 1.0, 10000.0);
            closedVentRelief = b.comment("Aeronautics: 닫힌 vent도 양력 없이 처리량 10을 인정한다(하강할 때 기존 비행선 보호).")
                    .define("closedVentRelief", true);
            b.pop();

            b.push("search");
            searchIntervalTicks = b.comment("배관 탐색 주기 (틱).")
                    .defineInRange("intervalTicks", 20, 1, 200);
            searchMaxNodes = b.comment("보일러 1개가 탐색하는 배관 칸 수 상한.")
                    .defineInRange("maxNodes", 2048, 64, 16384);
            b.pop();
        }
    }

    public static final class Client {
        public final ModConfigSpec.DoubleValue particleMultiplier;
        public final ModConfigSpec.BooleanValue sounds;

        Client(ModConfigSpec.Builder b) {
            particleMultiplier = b.comment("증기 파티클 양 배율.")
                    .defineInRange("particleMultiplier", 1.0, 0.0, 4.0);
            sounds = b.comment("출구 쉬익 소리와 과열 경고음.")
                    .define("sounds", true);
        }
    }

    private static <T> T safe(Supplier<T> getter, T fallback) {
        try {
            T value = getter.get();
            return value == null ? fallback : value;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static boolean requireExhaust() {
        return safe(SERVER.requireExhaust::get, true);
    }

    public static double baseCapacity() {
        return safe(SERVER.baseCapacity::get, 128.0);
    }

    public static double coolingPerBlock() {
        return safe(SERVER.coolingPerBlock::get, 4.0);
    }

    public static double condenserEfficiency() {
        return safe(SERVER.condenserEfficiency::get, 0.95);
    }

    public static int reheaterMaxDepth() {
        return safe(SERVER.reheaterMaxDepth::get, 8);
    }

    public static int overheatThreshold() {
        return safe(SERVER.overheatThreshold::get, 1200);
    }

    public static int overheatDecayPerTick() {
        return safe(SERVER.overheatDecayPerTick::get, 20);
    }

    public static boolean explosionBreaksBlocks() {
        return safe(SERVER.explosionBreaksBlocks::get, true);
    }

    public static double maxExplosionPower() {
        return safe(SERVER.maxExplosionPower::get, 6.0);
    }

    public static boolean graceForExistingBoilers() {
        return safe(SERVER.graceForExistingBoilers::get, true);
    }

    public static double ventM3PerMb() {
        return safe(SERVER.ventM3PerMb::get, 500.0);
    }

    public static boolean pipeFedVents() {
        return safe(SERVER.pipeFedVents::get, false);
    }

    public static double exhaustVentM3PerMb() {
        return safe(SERVER.exhaustVentM3PerMb::get, 250.0);
    }

    public static boolean closedVentRelief() {
        return safe(SERVER.closedVentRelief::get, true);
    }

    public static int searchIntervalTicks() {
        return safe(SERVER.searchIntervalTicks::get, 20);
    }

    public static int searchMaxNodes() {
        return safe(SERVER.searchMaxNodes::get, 2048);
    }

    public static double particleMultiplier() {
        return safe(CLIENT.particleMultiplier::get, 1.0);
    }

    public static boolean sounds() {
        return safe(CLIENT.sounds::get, true);
    }
}
