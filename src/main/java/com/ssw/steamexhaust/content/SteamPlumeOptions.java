package com.ssw.steamexhaust.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ssw.steamexhaust.SERegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 증기 덩어리 파티클의 인자. strength(0~1)는 출구 하나의 배기량을 정규화한 값으로,
 * 덩어리의 크기·수명·상승 속도를 연속적으로 키운다 (저출력 = 작은 뭉게, 고출력 = 높이 솟는 기둥).
 */
public record SteamPlumeOptions(float strength) implements ParticleOptions {
    public static final MapCodec<SteamPlumeOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.fieldOf("strength").forGetter(SteamPlumeOptions::strength)
    ).apply(i, SteamPlumeOptions::new));

    public static final StreamCodec<ByteBuf, SteamPlumeOptions> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(SteamPlumeOptions::new, SteamPlumeOptions::strength);

    public SteamPlumeOptions {
        strength = Math.max(0F, Math.min(1F, strength));
    }

    @Override
    public ParticleType<?> getType() {
        return SERegistry.STEAM_PLUME.get();
    }

    /** 등록용 파티클 타입. overrideLimiter=true: 멀리서도 보이는 굴뚝 연기처럼 취급. */
    public static final class Type extends ParticleType<SteamPlumeOptions> {
        public Type() {
            super(true);
        }

        @Override
        public MapCodec<SteamPlumeOptions> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, SteamPlumeOptions> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
