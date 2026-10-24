package dev.wrenchbridge.mixin;

import dev.wrenchbridge.WrenchContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * BaseBlock otherwise reads the main hand and the last eating/charging hand instead of this click's
 * hand.
 */
@Pseudo
@Mixin(targets = "mcjty.lib.blocks.BaseBlock")
public abstract class McJtyWrenchHandMixin {
  @Redirect(
      method = "testWrenchUsage",
      at =
          @At(
              value = "FIELD",
              remap = true,
              target = "Lnet/minecraft/util/Hand;MAIN_HAND:Lnet/minecraft/util/Hand;"),
      require = 0)
  private Hand wrenchbridge$wrenchHand() {
    return WrenchContext.hand() == null ? Hand.MAIN_HAND : WrenchContext.hand();
  }

  @Redirect(
      method = {"use", "func_225533_a_"},
      at =
          @At(
              value = "INVOKE",
              remap = true,
              target =
                  "Lnet/minecraft/entity/player/PlayerEntity;getUsedItemHand()Lnet/minecraft/util/Hand;"),
      require = 0)
  private Hand wrenchbridge$clickHand(PlayerEntity player) {
    return WrenchContext.hand() == null ? player.getUsedItemHand() : WrenchContext.hand();
  }
}
