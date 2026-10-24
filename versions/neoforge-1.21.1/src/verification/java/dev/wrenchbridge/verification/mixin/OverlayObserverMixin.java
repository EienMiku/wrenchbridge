package dev.wrenchbridge.verification.mixin;

import dev.wrenchbridge.verification.ClientVerification;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the actual native overlay draw; Mekanism does not cancel the default outline for this overlay. */
@Pseudo
@Mixin(targets = "mekanism.client.render.RenderTickHandler", remap = false)
public abstract class OverlayObserverMixin {
    @Inject(method = "onBlockHover", at = @At(value = "INVOKE", target = "Lmekanism/client/render/RenderResizableCuboid;renderCube(Lmekanism/client/render/MekanismRenderer$Model3D;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIILmekanism/client/render/RenderResizableCuboid$FaceDisplay;Lnet/minecraft/client/Camera;Lnet/minecraft/world/phys/Vec3;)V"))
    private void verifyOverlay(RenderHighlightEvent.Block event, CallbackInfo callback) {
        if (Boolean.getBoolean("wrenchbridge.verifyClient")) ClientVerification.overlayDrawn(event);
    }
}
