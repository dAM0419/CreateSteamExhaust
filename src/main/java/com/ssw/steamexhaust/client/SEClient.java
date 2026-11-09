package com.ssw.steamexhaust.client;

import com.ssw.steamexhaust.SERegistry;
import com.ssw.steamexhaust.steam.SteamClientEffects;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** 클라이언트 전용 등록. FMLEnvironment.dist가 클라이언트일 때만 호출된다. */
public final class SEClient {
    private SEClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(SEClient::registerParticles);
        // 증기는 거리 제한을 무시하고 강제로 그리므로, 비디오 설정의 파티클 단계는 여기서 직접 반영한다.
        SteamClientEffects.setParticleSettingScale(SEClient::particleSettingScale);
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(SERegistry.STEAM_PLUME.get(), SteamPlumeParticle.Provider::new);
    }

    private static double particleSettingScale() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return 1.0;
        }
        return switch (mc.options.particles().get()) {
            case ALL -> 1.0;
            case DECREASED -> 0.5;
            case MINIMAL -> 0.2;
        };
    }
}
