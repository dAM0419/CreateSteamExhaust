package com.ssw.steamexhaust.content;

import net.minecraft.util.StringRepresentable;

/**
 * 배기 파이프 한 면의 상태.
 * <ul>
 *   <li>NONE: 연결 없음, 막힌 옆면.</li>
 *   <li>OPEN: 이웃 없이 열린 끝. Create 파이프의 "열린 끝"과 같고, 증기 라인 위에 있으면 출구가 된다.</li>
 *   <li>EXHAUST: 다른 배기 파이프와 이어짐 (같은 굵기, 면 없이 관이 이어짐).</li>
 *   <li>PIPE: Create 파이프·탱크·펌프 등 유체 블록과 이어짐 (막힌 마감면).</li>
 * </ul>
 */
public enum ExhaustConnection implements StringRepresentable {
    NONE("none"),
    OPEN("open"),
    EXHAUST("exhaust"),
    PIPE("pipe");

    private final String name;

    ExhaustConnection(String name) {
        this.name = name;
    }

    /** 이웃과 실제로 이어진 면인지 (증기가 지나가는 면). */
    public boolean isConnected() {
        return this == EXHAUST || this == PIPE;
    }

    /** 관이 그 면까지 뻗어 있는지 (연결 또는 열린 끝). */
    public boolean extends_() {
        return this != NONE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
