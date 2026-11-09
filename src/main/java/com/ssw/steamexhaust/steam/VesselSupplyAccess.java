package com.ssw.steamexhaust.steam;

/**
 * Create: Connected의 BoilerData 하위 클래스는 gatheredSupply 필드를 따로 가진다(부모 필드를 가림).
 * 복수기 회수 물은 그 하위 클래스 필드에 넣어야 하므로 별도 인터페이스로 접근한다.
 */
public interface VesselSupplyAccess {
    void steamexhaust$addVesselSupply(int amount);
}
