package com.ssw.steamexhaust.client;

import com.ssw.steamexhaust.content.SteamPlumeOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;

/**
 * 하얀 뭉게구름 증기 덩어리. 바닐라 모닥불 연기({@link CampfireSmokeParticle})를 상속한다.
 * <p>
 * 매 틱 바닐라 연기 처리(흔들림·이동·충돌·제거)를 먼저 그대로 돌리고, 그 뒤에 증기다운 부분만 덧붙인다.
 * 그래서 바람 모드처럼 모닥불 연기를 건드리는 모드의 효과가 이 증기에도 똑같이 먹힌다.
 * <ul>
 *   <li>바닐라에 없는 감속: 옆·아래 출구로 뿜은 증기가 그 방향으로 끝없이 날아가지 않게 한다.</li>
 *   <li>부력: 일정 속도까지 떠오른다 (아래 출구의 증기는 말려 올라간다).</li>
 *   <li>팽창: 떠오르며 2.6~4배로 부풀어 위쪽이 뭉게뭉게 퍼진다.</li>
 *   <li>세기(strength 0~1)가 클수록 처음부터 크고, 오래 살고, 빨리 솟는다.</li>
 * </ul>
 * 텍스처는 바닐라 연기가 회색이라(색 조정으로는 밝게 못 만듦) 하얀 자체 텍스처를 쓴다.
 */
public class SteamPlumeParticle extends CampfireSmokeParticle {
    private static final float FRICTION = 0.92F;
    private static final float MAX_ALPHA = 0.85F;
    private static final float FADE_START = 0.55F;

    private final float growthPerTick;
    private final double buoyancy;

    protected SteamPlumeParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz, float strength) {
        super(level, x, y, z, vx, vy, vz, false);
        // 바닐라 생성자가 정한 값(크기 3배, 수명 80~130, 미세 중력)을 증기용으로 덮어쓴다
        this.yd = vy;
        this.gravity = 0;
        this.lifetime = 80 + Math.round(190 * strength) + this.random.nextInt(41);
        this.quadSize = 0.28F + 0.20F * strength + this.random.nextFloat() * 0.12F;
        float totalGrowth = 2.6F + 1.4F * strength + this.random.nextFloat() * 0.4F;
        this.growthPerTick = (float) Math.pow(totalGrowth, 1.0 / this.lifetime);
        // 최종 상승 속도(블록/틱) × (1 − 마찰) = 틱당 부력 가속
        double terminalRise = 0.045 + 0.04 * strength + this.random.nextFloat() * 0.01;
        this.buoyancy = terminalRise * (1 - FRICTION);

        float shade = 0.92F + this.random.nextFloat() * 0.08F;
        setColor(shade, shade, shade);
        setAlpha(MAX_ALPHA);
    }

    @Override
    public void tick() {
        super.tick(); // 바닐라 모닥불 연기 처리 (다른 모드가 여기에 끼운 효과 포함)
        if (!isAlive()) {
            return;
        }
        // 뭉게뭉게 퍼지도록 바닐라보다 조금 더 흔들리게
        this.xd += (this.random.nextFloat() - 0.5F) * 0.0012F;
        this.zd += (this.random.nextFloat() - 0.5F) * 0.0012F;
        this.xd *= FRICTION;
        this.zd *= FRICTION;
        this.yd = this.yd * FRICTION + this.buoyancy;

        this.quadSize *= this.growthPerTick;
        float life = this.age / (float) this.lifetime;
        setAlpha(life < FADE_START ? MAX_ALPHA : MAX_ALPHA * (1 - (life - FADE_START) / (1 - FADE_START)));
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SteamPlumeOptions> {
        @Override
        public Particle createParticle(SteamPlumeOptions options, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            SteamPlumeParticle particle = new SteamPlumeParticle(level, x, y, z, vx, vy, vz, options.strength());
            particle.pickSprite(sprites);
            return particle;
        }
    }
}
