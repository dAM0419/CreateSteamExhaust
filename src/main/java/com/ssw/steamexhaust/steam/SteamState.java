package com.ssw.steamexhaust.steam;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 보일러 하나의 배기 상태. BoilerData에 붙어 저장·동기화된다.
 * 앞쪽 필드는 NBT로 클라이언트에 동기화되고, transient 필드는 서버에서만 쓴다.
 */
public final class SteamState {
    public static final String NBT_KEY = "SteamExhaust";

    public static final byte STATUS_OK = 0;
    public static final byte STATUS_LIMITED = 1;
    public static final byte STATUS_NO_EXHAUST = 2;
    public static final byte STATUS_FEED_MIXED = 3;
    public static final byte STATUS_BLOCKED = 4;
    public static final byte STATUS_DISTANCE = 5;
    public static final byte STATUS_PUMP_STOPPED = 6;
    public static final byte STATUS_UNKNOWN = 7;
    public static final byte STATUS_GRACE = 8;
    /** 지금 엔진은 전부 돌지만 보일러 레벨보다 배기 레벨이 낮다 (엔진을 더 달면 출력이 깎임). */
    public static final byte STATUS_HEADROOM = 9;

    private static final int MAX_SYNCED_OUTLETS = 64;

    /** 출구 하나 (클라이언트 연출용). load는 처리량 대비 실제 배출 비율 0~1. */
    public record Outlet(BlockPos pos, Direction dir, float flow, float load) {
    }

    // ---- 동기화되는 값
    /** 배기 규칙을 적용하는 중인지 (requireExhaust이고 증기 소비자가 있음). */
    public boolean applies;
    /** 총 배기 처리량 C (mB/틱). */
    public float capacity;
    /** ⌊C/10⌋. 보일러 레벨 상한으로 쓰인다. */
    public int exhaustLevel;
    /** 배기를 뺀 보일러 레벨 (min(열, 물, 크기/4)). */
    public int nRaw;
    /** 과열 누적값. */
    public int overheat;
    /** 과열 비율 0~1 (클라이언트 경고용). */
    public float overheatRatio;
    /** 모드 설치 전에 저장된 보일러의 유예 상태. */
    public boolean grace;
    public byte status;
    /** 이 보일러가 복수기로 돌려받는 물 (mB/틱, 표시용). */
    public float recovered;
    public final List<Outlet> outlets = new ArrayList<>();

    // ---- 서버 전용
    public transient SteamSearch.Result search;
    public transient boolean unknown;
    public transient double supplyRemainder;
    public transient double recoveredCounter;
    public transient int lastSyncHash;
    public transient long lastSyncTick;
    public transient long lastSearchTick = Long.MIN_VALUE;
    /** 탱크 모양 캐시 (BoilerGeometry.cached). 구조가 바뀌기 전에는 그대로 쓴다. */
    public transient BoilerGeometry geometry;
    public transient int geometryWidth;
    public transient int geometryHeight;
    public transient net.minecraft.world.level.block.state.BlockState geometryState;

    public void resetInactive() {
        applies = false;
        capacity = 0;
        exhaustLevel = 0;
        nRaw = 0;
        status = STATUS_OK;
        outlets.clear();
        search = null;
        unknown = false;
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Applies", applies);
        tag.putFloat("Capacity", capacity);
        tag.putInt("Level", exhaustLevel);
        tag.putInt("Raw", nRaw);
        tag.putInt("Overheat", overheat);
        tag.putFloat("OverheatRatio", overheatRatio);
        tag.putBoolean("Grace", grace);
        tag.putByte("Status", status);
        tag.putFloat("Recovered", recovered);
        ListTag list = new ListTag();
        int count = 0;
        for (Outlet outlet : outlets) {
            if (count++ >= MAX_SYNCED_OUTLETS) {
                break;
            }
            CompoundTag o = new CompoundTag();
            o.putLong("Pos", outlet.pos().asLong());
            o.putByte("Dir", (byte) outlet.dir().get3DDataValue());
            o.putFloat("Flow", outlet.flow());
            o.putFloat("Load", outlet.load());
            list.add(o);
        }
        tag.put("Outlets", list);
        return tag;
    }

    public void read(CompoundTag tag) {
        applies = tag.getBoolean("Applies");
        capacity = tag.getFloat("Capacity");
        exhaustLevel = tag.getInt("Level");
        nRaw = tag.getInt("Raw");
        overheat = tag.getInt("Overheat");
        overheatRatio = tag.getFloat("OverheatRatio");
        grace = tag.getBoolean("Grace");
        status = tag.getByte("Status");
        recovered = tag.getFloat("Recovered");
        outlets.clear();
        ListTag list = tag.getList("Outlets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag o = list.getCompound(i);
            outlets.add(new Outlet(BlockPos.of(o.getLong("Pos")), Direction.from3DDataValue(o.getByte("Dir")),
                    o.getFloat("Flow"), o.getFloat("Load")));
        }
    }

    /** 동기화가 필요한지 판단하기 위한 거친 해시 (값이 눈에 띄게 바뀔 때만 달라진다). */
    public int syncHash() {
        int h = applies ? 1 : 0;
        h = h * 31 + exhaustLevel;
        h = h * 31 + Math.round(capacity);
        h = h * 31 + nRaw;
        h = h * 31 + status;
        h = h * 31 + (grace ? 1 : 0);
        h = h * 31 + Math.round(overheatRatio * 20);
        h = h * 31 + Math.round(recovered);
        for (Outlet outlet : outlets) {
            h = h * 31 + outlet.pos().hashCode();
            h = h * 31 + outlet.dir().ordinal();
            h = h * 31 + Math.round(outlet.flow());
        }
        return h;
    }
}
