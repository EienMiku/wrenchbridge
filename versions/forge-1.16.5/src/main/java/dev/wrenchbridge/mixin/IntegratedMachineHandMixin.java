package dev.wrenchbridge.mixin;

import dev.wrenchbridge.WrenchContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * GUI-bearing ID machines read the last active-use hand, rather than the hand supplied to
 * useItemOn.
 */
@Pseudo
@Mixin(targets = "org.cyclops.integrateddynamics.core.block.BlockWithEntityGuiCabled")
public abstract class IntegratedMachineHandMixin {
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
