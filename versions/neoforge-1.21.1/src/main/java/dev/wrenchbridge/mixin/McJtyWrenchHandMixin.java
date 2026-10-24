package dev.wrenchbridge.mixin;

import dev.wrenchbridge.WrenchContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** BaseBlock otherwise reads the main hand and the last eating/charging hand instead of this click's hand. */
@Pseudo
@Mixin(targets = "mcjty.lib.blocks.BaseBlock", remap = false)
public abstract class McJtyWrenchHandMixin {
    @Redirect(method = "testWrenchUsage", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/InteractionHand;MAIN_HAND:Lnet/minecraft/world/InteractionHand;"))
    private InteractionHand wrenchbridge$wrenchHand() {
        return WrenchContext.hand() == null ? InteractionHand.MAIN_HAND : WrenchContext.hand();
    }

    @Redirect(method = "useWithoutItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"))
    private InteractionHand wrenchbridge$clickHand(Player player) {
        return WrenchContext.hand() == null ? player.getUsedItemHand() : WrenchContext.hand();
    }
}
