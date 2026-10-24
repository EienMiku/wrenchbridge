package dev.wrenchbridge.mixin;

import dev.wrenchbridge.WrenchContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * GUI-bearing ID machines read the last active-use hand, rather than the hand supplied to
 * useItemOn.
 */
@Pseudo
@Mixin(
    targets = "org.cyclops.integrateddynamics.core.block.BlockWithEntityGuiCabled",
    remap = false)
public abstract class IntegratedMachineHandMixin {
  @Redirect(
      method = "useItemOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/player/Player;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"),
      require = 0)
  private InteractionHand wrenchbridge$clickHand(Player player) {
    return WrenchContext.hand() == null ? player.getUsedItemHand() : WrenchContext.hand();
  }
}
