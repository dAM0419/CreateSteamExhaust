package com.ssw.steamexhaust.steam;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Mixin에서 호출하는 공통 로직. Create BoilerData와 Create: Connected의 BoilerData 하위 클래스가 같은 코드를 쓴다.
 */
public final class BoilerHooks {
    private BoilerHooks() {
    }

    @Nullable
    public static SteamState state(@Nullable Object boiler) {
        return boiler instanceof SteamBoilerAccess access ? access.steamexhaust$state() : null;
    }

    /** 급수 집계에 더한다. Fluid Vessel은 하위 클래스가 가진 별도 필드에 넣는다. */
    public static void addSupply(Object boiler, int amount) {
        if (boiler instanceof VesselSupplyAccess vessel) {
            vessel.steamexhaust$addVesselSupply(amount);
        } else if (boiler instanceof SteamBoilerAccess access) {
            access.steamexhaust$addSupply(amount);
        }
    }

    /** getActualHeat 반환값에 배기 레벨 ⌊C/10⌋ 상한을 씌운다 (기획서 5.5). */
    public static int capActualHeat(Object boiler, int original) {
        SteamState st = state(boiler);
        if (st == null || !st.applies) {
            return original;
        }
        return Math.min(original, st.exhaustLevel);
    }

    /** 패시브 열 경로는 getActualHeat를 거치지 않으므로, 배기 레벨이 0이면 효율을 0으로 막는다. */
    public static float capEfficiency(Object boiler, float original) {
        SteamState st = state(boiler);
        if (st == null || !st.applies) {
            return original;
        }
        return st.exhaustLevel <= 0 ? 0F : original;
    }

    public static void write(Object boiler, CompoundTag tag) {
        SteamState st = state(boiler);
        if (st != null && tag != null) {
            tag.put(SteamState.NBT_KEY, st.write());
        }
    }

    /**
     * 저장 데이터에 CSE 태그가 없으면 모드 설치 전에 저장된 보일러다 → 유예 (기획서 8.4).
     * 새로 만든 보일러는 read를 거치지 않고 첫 저장 때 태그가 붙으므로 유예가 없다.
     * 클라이언트는 서버가 보낸 태그를 항상 받는다.
     */
    public static void read(Object boiler, CompoundTag tag) {
        SteamState st = state(boiler);
        if (st == null || tag == null) {
            return;
        }
        if (tag.contains(SteamState.NBT_KEY, CompoundTag.TAG_COMPOUND)) {
            st.read(tag.getCompound(SteamState.NBT_KEY));
        } else {
            st.grace = true;
        }
    }
}
