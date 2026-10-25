package dev.wrenchbridge.verification.mixin;

import dev.wrenchbridge.verification.NativeProxyObserver;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.wrench.RadialWrenchMenuSubmitPacket", remap = false)
public abstract class NativeRotationObserverMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private void observe(ServerPlayer player, CallbackInfo info) { NativeProxyObserver.rotations++; }
}
