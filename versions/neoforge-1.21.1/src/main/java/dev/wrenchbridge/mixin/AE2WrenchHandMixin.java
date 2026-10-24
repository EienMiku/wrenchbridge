package dev.wrenchbridge.mixin;

import dev.wrenchbridge.WrenchContext;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep AE2's permission, disassembly-context and drop logic; extend only its hand gate during our call. */
@Pseudo
@Mixin(targets = "appeng.hooks.WrenchHook", remap = false)
public abstract class AE2WrenchHandMixin {
    @Redirect(method = "onPlayerUseBlock", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/InteractionHand;MAIN_HAND:Lnet/minecraft/world/InteractionHand;"))
    private static InteractionHand wrenchbridge$activeHand() {
        return WrenchContext.hand() == null ? InteractionHand.MAIN_HAND : WrenchContext.hand();
    }

}
