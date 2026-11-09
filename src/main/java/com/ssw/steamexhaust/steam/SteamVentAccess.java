package com.ssw.steamexhaust.steam;

/** Aeronautics SteamVentBlockEntity에 Mixin으로 붙는 인터페이스. */
public interface SteamVentAccess {
    /** CSE가 계산한 이 vent의 증기량(mB/틱)을 설정한다. pipeFed면 배관 끝 vent(배기 증기 환산 사용). */
    void steamexhaust$setFlow(float flow, boolean pipeFed, long gameTime);

    float steamexhaust$flow();

    /** 레드스톤 신호 0~15. */
    int steamexhaust$signal();
}
