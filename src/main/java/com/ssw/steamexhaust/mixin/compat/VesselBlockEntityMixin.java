package com.ssw.steamexhaust.mixin.compat;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.ssw.steamexhaust.steam.GoggleLines;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fluid Vessel은 고글 툴팁을 super 없이 다시 정의하므로 여기에도 배기 정보를 붙인다. */
@Pseudo
@Mixin(targets = "com.hlysine.create_connected.content.fluidvessel.FluidVesselBlockEntity")
public abstract class VesselBlockEntityMixin {
    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"))
    private void steamexhaust$goggles(List<Component> tooltip, boolean isPlayerSneaking, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            GoggleLines.append((FluidTankBlockEntity) (Object) this, tooltip);
        }
    }
}
