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
@Mixin(targets = "mekanism.common.util.MekanismUtils", remap = false)
public abstract class MekanismWrenchProtocolMixin {
  @Inject(method = "hasUsableWrench", at = @At("HEAD"), cancellable = true)
  private static void wrenchbridge$shared(
      EntityPlayer p, BlockPos pos, CallbackInfoReturnable<Boolean> result) {
    EnumHand h = WrenchContext.hand();
    if (h != null) {
      ItemStack tool = WrenchContext.nativeHand(p, h);
      if (WrenchContext.forcedItem(tool.getItem()) && Tools.active(tool))
        result.setReturnValue(true);
    } else if (p.getHeldItemMainhand().getItem() instanceof UniversalWrenchItem)
      result.setReturnValue(false);
  }
}
