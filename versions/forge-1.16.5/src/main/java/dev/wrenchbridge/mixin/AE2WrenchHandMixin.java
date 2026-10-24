package dev.wrenchbridge.mixin;

import dev.wrenchbridge.WrenchContext;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keep AE2's permission, disassembly-context and drop logic; extend only its hand gate during our
 * call.
 */
@Pseudo
@Mixin(targets = "appeng.hooks.WrenchHook")
public abstract class AE2WrenchHandMixin {
  @Redirect(
      method = "onPlayerUseBlock",
      at =
          @At(
              value = "FIELD",
              remap = true,
              target = "Lnet/minecraft/util/Hand;MAIN_HAND:Lnet/minecraft/util/Hand;"),
      require = 0)
  private static Hand wrenchbridge$activeHand() {
    return WrenchContext.hand() == null ? Hand.MAIN_HAND : WrenchContext.hand();
  }
}
