package com.ssw.steamexhaust.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.ssw.steamexhaust.config.SEConfig;
import com.ssw.steamexhaust.steam.SteamVentAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create Aeronautics 1.3.2 steam vent의 양력을 CSE가 계산한 증기량으로 정한다 (기획서 10.1).
 * <ul>
 *   <li>원래: 양력 = 스크롤 값 × 보일러 효율 × 신호/15</li>
 *   <li>CSE: 양력 = min(스크롤 값 × 신호/15, k × 증기량), k = 500(윗면 vent) 또는 250(배관 끝 vent)</li>
 * </ul>
 * CSE가 40틱 동안 증기량을 갱신하지 않으면(보일러가 사라짐 등) Aeronautics 원래 동작으로 돌아간다.
 */
@Pseudo
@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity")
public abstract class SteamVentBlockEntityMixin implements SteamVentAccess {
    @Shadow
    public int signalStrength;

    @Shadow
    protected ScrollValueBehaviour steamAmountBehaviour;

    @Unique
    private float steamexhaust$flow;
    @Unique
    private boolean steamexhaust$pipeFed;
    @Unique
    private boolean steamexhaust$managed;
    @Unique
    private long steamexhaust$updated;

    @Override
    public void steamexhaust$setFlow(float flow, boolean pipeFed, long gameTime) {
        steamexhaust$flow = Math.max(0, flow);
        steamexhaust$pipeFed = pipeFed;
        steamexhaust$updated = gameTime;
        steamexhaust$managed = true;
    }

    @Override
    public float steamexhaust$flow() {
        return steamexhaust$managed ? steamexhaust$flow : 0;
    }

    @Override
    public int steamexhaust$signal() {
        return signalStrength;
    }

    @ModifyReturnValue(method = "getGasOutput", at = @At("RETURN"))
    private double steamexhaust$gasOutput(double original) {
        if (!steamexhaust$managed) {
            return original;
        }
        double scroll = steamAmountBehaviour == null ? 0 : steamAmountBehaviour.getValue();
        double requested = scroll * Math.min(15, Math.max(0, signalStrength)) / 15.0;
        double k = steamexhaust$pipeFed ? SEConfig.exhaustVentM3PerMb() : SEConfig.ventM3PerMb();
        return Math.min(requested, k * steamexhaust$flow);
    }

    @ModifyReturnValue(method = "canOutputGas", at = @At("RETURN"))
    private boolean steamexhaust$canOutputGas(boolean original) {
        if (!steamexhaust$managed) {
            return original;
        }
        return signalStrength > 0 && steamexhaust$flow > 0 && !((BlockEntity) (Object) this).isRemoved();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void steamexhaust$expire(CallbackInfo ci) {
        if (!steamexhaust$managed) {
            return;
        }
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        if (level.getGameTime() - steamexhaust$updated > 40) {
            steamexhaust$managed = false;
            steamexhaust$flow = 0;
            if (self instanceof SmartBlockEntity smart) {
                smart.sendData();
            }
        }
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void steamexhaust$write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        tag.putBoolean("SteamExhaustManaged", steamexhaust$managed);
        tag.putFloat("SteamExhaustFlow", steamexhaust$flow);
        tag.putBoolean("SteamExhaustPipeFed", steamexhaust$pipeFed);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void steamexhaust$read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        steamexhaust$managed = tag.getBoolean("SteamExhaustManaged");
        steamexhaust$flow = tag.getFloat("SteamExhaustFlow");
        steamexhaust$pipeFed = tag.getBoolean("SteamExhaustPipeFed");
    }
}
