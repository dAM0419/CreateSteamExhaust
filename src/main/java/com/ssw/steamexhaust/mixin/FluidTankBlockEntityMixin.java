package com.ssw.steamexhaust.mixin;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.ssw.steamexhaust.steam.GoggleLines;
import com.ssw.steamexhaust.steam.SteamExhaustTicker;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 탱크 틱 끝에서 배기 계산을 돌리고, 고글 툴팁 끝에 배기 정보를 붙인다.
 * Create: Connected Fluid Vessel은 tick에서 super.tick()을 부르므로 이 틱 훅이 그대로 동작한다.
 */
@Mixin(FluidTankBlockEntity.class)
public abstract class FluidTankBlockEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void steamexhaust$tick(CallbackInfo ci) {
        SteamExhaustTicker.tick((FluidTankBlockEntity) (Object) this);
    }

    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"))
    private void steamexhaust$goggles(List<Component> tooltip, boolean isPlayerSneaking, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            GoggleLines.append((FluidTankBlockEntity) (Object) this, tooltip);
        }
    }
}
