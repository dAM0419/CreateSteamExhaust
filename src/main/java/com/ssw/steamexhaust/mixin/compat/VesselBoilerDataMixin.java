package com.ssw.steamexhaust.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.ssw.steamexhaust.steam.BoilerHooks;
import com.ssw.steamexhaust.steam.VesselSupplyAccess;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create: Connected 1.3.3의 Fluid Vessel(가로 탱크)용 BoilerData 하위 클래스.
 * getActualHeat·getEngineEfficiency·write·read를 모두 자기 것으로 다시 정의하므로 Create BoilerData Mixin이 닿지 않는다.
 * 같은 지점에 같은 처리를 건다. 배기 상태 필드 자체는 부모(Create BoilerData)에 붙은 것을 그대로 쓴다.
 */
@Pseudo
@Mixin(targets = "com.hlysine.create_connected.content.fluidvessel.BoilerData")
public abstract class VesselBoilerDataMixin implements VesselSupplyAccess {
    @Shadow
    int gatheredSupply;

    @Override
    public void steamexhaust$addVesselSupply(int amount) {
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
