package dev.wrenchbridge.verification.mixin;

import dev.wrenchbridge.verification.ClientVerification;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, remap = false)
public abstract class SwingObserverMixin {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void wrenchbridge$swing(InteractionHand hand, boolean force, CallbackInfo info) {
        if ((Object) this == Minecraft.getInstance().player) ClientVerification.handSwung(hand);
    }
}
