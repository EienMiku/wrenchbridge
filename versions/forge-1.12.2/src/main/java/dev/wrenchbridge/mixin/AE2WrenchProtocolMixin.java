package dev.wrenchbridge.mixin;

import dev.wrenchbridge.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.util.Platform", remap = false)
public abstract class AE2WrenchProtocolMixin {
  @Inject(method = "isWrench", at = @At("HEAD"), cancellable = true)
  private static void wrenchbridge$shared(
      EntityPlayer p, ItemStack stack, BlockPos pos, CallbackInfoReturnable<Boolean> result) {
    EnumHand h = WrenchContext.hand();
    if (h != null
        && WrenchContext.forcedItem(stack.getItem())
        && Tools.active(WrenchContext.nativeHand(p, h))) result.setReturnValue(true);
  }
}
