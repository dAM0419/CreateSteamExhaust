package com.ssw.steamexhaust.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.ssw.steamexhaust.steam.BoilerHooks;
import com.ssw.steamexhaust.steam.SteamBoilerAccess;
import com.ssw.steamexhaust.steam.SteamState;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create 보일러에 배기 상태를 붙이고, 보일러 레벨 계산에 배기 병목을 넣는다.
 * 레벨 = min(열, 물, 크기/4, ⌊C/10⌋)
 */
@Mixin(BoilerData.class)
public abstract class BoilerDataMixin implements SteamBoilerAccess {
    @Shadow
    int gatheredSupply;

    @Unique
    private SteamState steamexhaust$steamState;

    @Override
    public SteamState steamexhaust$state() {
        if (steamexhaust$steamState == null) {
            steamexhaust$steamState = new SteamState();
        }
        return steamexhaust$steamState;
    }

    @Override
    public void steamexhaust$addSupply(int amount) {
        gatheredSupply += amount;
    }

    @ModifyReturnValue(method = "getActualHeat", at = @At("RETURN"))
    private int steamexhaust$capActualHeat(int original) {
        return BoilerHooks.capActualHeat(this, original);
    }

    @ModifyReturnValue(method = "getEngineEfficiency", at = @At("RETURN"))
    private float steamexhaust$capEfficiency(float original) {
        return BoilerHooks.capEfficiency(this, original);
    }

    /*
     * 고글 표시: Create는 "보일러 상태: 레벨 n"과 피로도 용량을 getActualHeat를 거치지 않고
     * min(열, min(물, 크기))로 직접 계산한다. 그 바깥 Math.min(두 번째 호출) 결과에 배기 상한을 씌워 표시도 맞춘다.
     */
    @ModifyExpressionValue(method = "addToGoggleTooltip",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", ordinal = 1))
    private int steamexhaust$capTooltipLevel(int original) {
        return BoilerHooks.capActualHeat(this, original);
    }

    @ModifyExpressionValue(method = "getHeatLevelTextComponent",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", ordinal = 1))
    private int steamexhaust$capHeaderLevel(int original) {
        return BoilerHooks.capActualHeat(this, original);
    }

    @ModifyReturnValue(method = "write", at = @At("RETURN"))
    private CompoundTag steamexhaust$write(CompoundTag tag) {
        BoilerHooks.write(this, tag);
        return tag;
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void steamexhaust$read(CompoundTag nbt, int boilerSize, CallbackInfo ci) {
        BoilerHooks.read(this, nbt);
    }
}
