package com.ssw.steamexhaust.steam;

/** Create BoilerData(및 Create: Connected의 하위 클래스)에 Mixin으로 붙는 인터페이스. */
public interface SteamBoilerAccess {
    SteamState steamexhaust$state();

    /** 급수량 집계(gatheredSupply)에 더한다. 복수기 회수 물이 이 경로로 들어간다. */
    void steamexhaust$addSupply(int amount);
}
